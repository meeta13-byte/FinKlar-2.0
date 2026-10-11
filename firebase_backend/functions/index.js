const functions = require("firebase-functions");
const admin = require("firebase-admin");
const twilio = require("twilio");
const crypto = require("crypto");

admin.initializeApp();

const db = admin.firestore();

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const OTP_TTL_MS = 5 * 60 * 1000; // how long a code stays valid
const MAX_ATTEMPTS = 5; // failed verifications before the phone is locked
const LOCK_DURATION_MS = 15 * 60 * 1000; // lockout window once attempts are exhausted
const SEND_COOLDOWN_MS = 60 * 1000; // per-phone resend cooldown
const IP_COOLDOWN_MS = 60 * 1000; // per-IP resend cooldown
const MAX_NAME_LEN = 100;
const MAX_EMAIL_LEN = 254;

const E164_RE = /^\+[1-9]\d{7,14}$/;
const OTP_RE = /^\d{6}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

// Reads a value from the environment first, then from legacy functions config.
const readConfig = (key) => {
  const fromEnv = process.env[key];
  if (typeof fromEnv === "string" && fromEnv.trim() !== "") return fromEnv.trim();
  const cfg = functions.config();
  return cfg && cfg[key] ? String(cfg[key]).trim() : "";
};

// Retrieve Twilio credentials from environment configuration or environment variables
const getTwilioConfig = () => {
  const accountSid = process.env.TWILIO_ACCOUNT_SID || functions.config().twilio?.sid;
  const authToken = process.env.TWILIO_AUTH_TOKEN || functions.config().twilio?.token;
  const phoneNumber = process.env.TWILIO_PHONE_NUMBER || functions.config().twilio?.phone;

  return { accountSid, authToken, phoneNumber };
};

// Secret used to HMAC codes before they are stored. It must be identical across
// every function instance, otherwise previously issued codes stop verifying.
// When absent we refuse to issue rather than fall back to an unkeyed hash.
const OTP_PEPPER = readConfig("OTP_PEPPER");

// Comma-separated list of browser origins allowed to call these endpoints.
// Native Android clients send no Origin header and are unaffected.
const ALLOWED_ORIGINS = readConfig("ALLOWED_ORIGINS")
  .split(",")
  .map((origin) => origin.trim())
  .filter(Boolean);

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const genericError = (res, status, message) => res.status(status).json({ error: message });

/** Strips common formatting and validates E.164. Returns null when unusable. */
function normalizePhone(raw) {
  if (typeof raw !== "string") return null;
  const cleaned = raw.replace(/[\s().-]/g, "");
  return E164_RE.test(cleaned) ? cleaned : null;
}

/** HMAC of the code. The plaintext code is never persisted. */
function hashOtp(otp) {
  return crypto.createHmac("sha256", OTP_PEPPER).update(otp).digest("hex");
}

/** Length-checked, constant-time comparison of two hex digests. */
function safeEqualHex(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  return crypto.timingSafeEqual(Buffer.from(a, "hex"), Buffer.from(b, "hex"));
}

/** Hashed so we never persist a raw caller address. */
function clientIpKey(req) {
  const forwarded = req.headers["x-forwarded-for"];
  const ip =
    typeof forwarded === "string" && forwarded.length > 0
      ? forwarded.split(",")[0].trim()
      : req.ip || req.connection?.remoteAddress || "unknown";
  return crypto.createHash("sha256").update(ip).digest("hex");
}

/** Emits CORS headers only for explicitly allowlisted origins. */
function applyCors(req, res) {
  const origin = req.headers.origin;
  if (origin && ALLOWED_ORIGINS.includes(origin)) {
    res.set("Access-Control-Allow-Origin", origin);
    res.set("Access-Control-Allow-Methods", "POST");
    res.set("Access-Control-Allow-Headers", "Content-Type");
    res.set("Vary", "Origin");
  }
}

/**
 * Applies CORS, answers preflight, and rejects every non-POST method.
 * Returns true when the request has already been fully handled.
 */
function guardRequest(req, res) {
  applyCors(req, res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return true;
  }
  if (req.method !== "POST") {
    res.set("Allow", "POST");
    genericError(res, 405, "Method not allowed.");
    return true;
  }
  return false;
}

/** Reads a Firestore timestamp field to epoch millis, or null when unusable. */
function timestampMillis(field) {
  if (!field || typeof field.toDate !== "function") return null;
  const ms = field.toDate().getTime();
  return Number.isNaN(ms) ? null : ms;
}

function lockPhone(lockRef) {
  return lockRef.set({
    lockedUntil: admin.firestore.Timestamp.fromMillis(Date.now() + LOCK_DURATION_MS),
    lockedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
}

// ---------------------------------------------------------------------------
// HTTP Function: Send OTP via Twilio SMS
// Request Body: { "phoneNumber": "+1234567890", "name": "...", "email": "..." }
// ---------------------------------------------------------------------------
exports.sendOTP = functions.https.onRequest(async (req, res) => {
  if (guardRequest(req, res)) return;

  if (!OTP_PEPPER) {
    console.error("OTP_PEPPER is not configured; refusing to issue verification codes.");
    return genericError(res, 503, "Verification service temporarily unavailable.");
  }

  try {
    const body = req.body || {};
    const phoneNumber = normalizePhone(body.phoneNumber);
    if (!phoneNumber) {
      return genericError(res, 400, "A valid phone number in E.164 format is required.");
    }

    const rawName = typeof body.name === "string" ? body.name.trim() : "";
    const rawEmail = typeof body.email === "string" ? body.email.trim() : "";
    if (rawName.length > MAX_NAME_LEN || rawEmail.length > MAX_EMAIL_LEN) {
      return genericError(res, 400, "Invalid request.");
    }
    if (rawEmail && !EMAIL_RE.test(rawEmail)) {
      return genericError(res, 400, "A valid email address is required.");
    }

    // A locked phone must not be able to bypass the lockout by re-requesting.
    const lockRef = db.collection("locks").doc(phoneNumber);
    const lockDoc = await lockRef.get();
    if (lockDoc.exists && (timestampMillis(lockDoc.get("lockedUntil")) || 0) > Date.now()) {
      return genericError(res, 429, "Too many attempts. Try again later.");
    }

    // Per-phone resend cooldown, read from the outstanding code's timestamp.
    // NOTE: read-then-write, so concurrent requests can race past the window.
    const otpRef = db.collection("otps").doc(phoneNumber);
    const existing = await otpRef.get();
    if (existing.exists) {
      const createdMs = timestampMillis(existing.get("createdAt"));
      if (createdMs && Date.now() - createdMs < SEND_COOLDOWN_MS) {
        return genericError(res, 429, "Please wait before requesting another code.");
      }
    }

    // Per-IP resend cooldown, keyed by a hash of the caller address.
    const ipRef = db.collection("throttle").doc(clientIpKey(req));
    const ipDoc = await ipRef.get();
    if (ipDoc.exists) {
      const lastSentMs = timestampMillis(ipDoc.get("lastSentAt"));
      if (lastSentMs && Date.now() - lastSentMs < IP_COOLDOWN_MS) {
        return genericError(res, 429, "Too many requests. Try again later.");
      }
    }

    // Checked before issuing so no code is ever persisted if it cannot be sent.
    const { accountSid, authToken, phoneNumber: twilioPhone } = getTwilioConfig();
    if (!accountSid || !authToken || !twilioPhone) {
      console.error("Twilio is not configured; verification SMS was not sent.");
      return genericError(res, 503, "Verification service temporarily unavailable.");
    }

    // Cryptographically secure 6-digit code.
    const otp = crypto.randomInt(100000, 1000000).toString();

    await otpRef.set({
      otpHash: hashOtp(otp),
      attempts: 0,
      name: rawName,
      email: rawEmail,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await ipRef.set({ lastSentAt: admin.firestore.FieldValue.serverTimestamp() });

    // Requesting a fresh code clears a previously elapsed lock.
    await lockRef.delete();

    const client = twilio(accountSid, authToken);
    await client.messages.create({
      body: `Your FinKlar 2.0 verification code is ${otp}. It is valid for 5 minutes.`,
      from: twilioPhone,
      to: phoneNumber,
    });

    res.status(200).json({ success: true, message: "Verification code sent." });
  } catch (error) {
    // Only the error name is logged; Twilio error strings can embed account
    // identifiers and must not reach logs or the client.
    console.error("Error sending OTP:", error && error.name);
    genericError(res, 500, "Something went wrong. Please try again.");
  }
});

// ---------------------------------------------------------------------------
// HTTP Function: Verify OTP
// Request Body: { "phoneNumber": "+1234567890", "otp": "123456" }
// ---------------------------------------------------------------------------
exports.verifyOTP = functions.https.onRequest(async (req, res) => {
  if (guardRequest(req, res)) return;

  if (!OTP_PEPPER) {
    console.error("OTP_PEPPER is not configured; cannot verify codes.");
    return genericError(res, 503, "Verification service temporarily unavailable.");
  }

  try {
    const body = req.body || {};
    const phoneNumber = normalizePhone(body.phoneNumber);
    const otp = typeof body.otp === "string" ? body.otp.trim() : "";
    if (!phoneNumber || !OTP_RE.test(otp)) {
      return genericError(res, 400, "Invalid or expired verification code.");
    }

    const lockRef = db.collection("locks").doc(phoneNumber);
    const lockDoc = await lockRef.get();
    if (lockDoc.exists) {
      const lockedUntilMs = timestampMillis(lockDoc.get("lockedUntil")) || 0;
      if (lockedUntilMs > Date.now()) {
        return genericError(res, 429, "Too many attempts. Try again later.");
      }
      await lockRef.delete(); // lockout has elapsed
    }

    const docRef = db.collection("otps").doc(phoneNumber);
    const doc = await docRef.get();
    if (!doc.exists) {
      return genericError(res, 400, "Invalid or expired verification code.");
    }
    const data = doc.data();

    // Expiry is enforced BEFORE the code is compared, and a missing or
    // unreadable createdAt is treated as expired rather than as valid.
    const createdMs = timestampMillis(data.createdAt);
    if (createdMs === null || Date.now() - createdMs > OTP_TTL_MS) {
      await docRef.delete();
      return genericError(res, 400, "Invalid or expired verification code.");
    }

    const attempts = Number.isInteger(data.attempts) ? data.attempts : 0;
    if (attempts >= MAX_ATTEMPTS) {
      await docRef.delete();
      await lockPhone(lockRef);
      return genericError(res, 429, "Too many attempts. Try again later.");
    }

    if (!safeEqualHex(hashOtp(otp), data.otpHash)) {
      const nextAttempts = attempts + 1;
      if (nextAttempts >= MAX_ATTEMPTS) {
        // Exhausted attempts: drop the code entirely and lock the phone.
        await docRef.delete();
        await lockPhone(lockRef);
        return genericError(res, 429, "Too many attempts. Try again later.");
      }
      await docRef.update({ attempts: nextAttempts });
      return genericError(res, 400, "Invalid or expired verification code.");
    }

    // Verified: consume the code so it cannot be replayed.
    await docRef.delete();
    await lockRef.delete();

    await db
      .collection("users")
      .doc(phoneNumber)
      .set(
        {
          name: data.name,
          email: data.email,
          verifiedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );

    res.status(200).json({
      success: true,
      message: "Verification code confirmed.",
      profile: {
        name: data.name,
        email: data.email,
        phoneNumber: phoneNumber,
      },
    });
  } catch (error) {
    console.error("Error verifying OTP:", error && error.name);
    genericError(res, 500, "Something went wrong. Please try again.");
  }
});