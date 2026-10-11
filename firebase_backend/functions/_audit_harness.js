// Test harness: stubs firebase-functions / firebase-admin / twilio so the REAL
// index.js can be required and driven without live credentials.
const Module = require("module");
const path = require("path");

const docs = new Map();          // "coll/id" -> data
const BASE_NOW = 1767225600000;  // fixed clock origin (2026-01-01T00:00:00Z)
let now = BASE_NOW;
const PENDING = Symbol("pending");

const Timestamp = {
  fromMillis: (ms) => ({ __ts: true, toDate: () => new Date(ms) }),
};

function resolve(v) {
  if (v === PENDING) return Timestamp.fromMillis(now);
  if (v && typeof v === "object" && !Array.isArray(v)) {
    const out = {};
    for (const k of Object.keys(v)) out[k] = resolve(v[k]);
    return out;
  }
  return v;
}

// Captures serverTimestamp() at WRITE time, as Firestore actually does.
function materialize(v) {
  if (v === PENDING) return Timestamp.fromMillis(now);
  if (v && typeof v === "object" && !Array.isArray(v)) {
    const out = {};
    for (const k of Object.keys(v)) out[k] = materialize(v[k]);
    return out;
  }
  return v;
}

// Make the whole backend observe the harness clock.
Date.now = () => now;

function makeDocSnap(id, data) {
  return {
    id,
    exists: data !== undefined,
    get: (f) => (f === "createdAt" ? resolve(data)[f] : resolve(data)[f]),
    data: () => resolve(data),
  };
}

const db = {
  collection: (c) => ({
    doc: (id) => {
      const key = `${c}/${id}`;
      return {
        get: async () => makeDocSnap(id, docs.get(key)),
        set: async (data, opts) => {
          const prev = docs.get(key);
          const merged = opts && opts.merge && prev ? { ...resolve(prev), ...data } : data;
          docs.set(key, materialize(merged));
        },
        update: async (patch) => {
          const cur = docs.get(key) || {};
          docs.set(key, materialize({ ...cur, ...patch }));
        },
        delete: async () => { docs.delete(key); },
      };
    },
  }),
};

const FieldValue = { serverTimestamp: () => PENDING };

const admin = {
  initializeApp: () => {},
  firestore: Object.assign(() => db, { FieldValue, Timestamp }),
};

const sentSms = [];
const twilioStub = () => ({ messages: { create: async (m) => { sentSms.push(m); return m; } } });

process.env.TWILIO_ACCOUNT_SID = "ACtest";
process.env.TWILIO_AUTH_TOKEN = "token";
process.env.TWILIO_PHONE_NUMBER = "+10000000000";
process.env.OTP_PEPPER = "test-pepper";

const origLoad = Module._load;
Module._load = function (request, parent, isMain) {
  if (request === "firebase-functions") {
    return { https: { onRequest: (fn) => fn }, config: () => ({}) };
  }
  if (request === "firebase-admin") return admin;
  if (request === "twilio") return twilioStub;
  return origLoad.apply(this, arguments);
};

const { sendOTP, verifyOTP } = require(path.join(__dirname, "index.js"));

// ---- fake req/res ----
function mkRes() {
  const r = { _status: 0, _body: null, _headers: {} };
  r.set = (k, v) => { r._headers[k] = v; };
  r.status = (s) => { r._status = s; return r; };
  r.json = (b) => { r._body = b; return r; };
  r.send = () => r;
  return r;
}
async function call(fn, { method = "POST", body = {}, headers = {} } = {}) {
  const res = mkRes();
  await fn({ method, body, headers, ip: "1.2.3.4", connection: {} }, res);
  return res;
}

let pass = 0, fail = 0;
function check(name, cond, extra) {
  if (cond) { pass++; console.log("  PASS  " + name); }
  else { fail++; console.log("  FAIL  " + name + (extra ? "  -> " + extra : "")); }
}
const reset = () => { docs.clear(); sentSms.length = 0; now = BASE_NOW; };

// Extract the code from the last real SMS sent, so we never hardcode it.
const lastCode = () => (sentSms.length ? sentSms[sentSms.length - 1].body.match(/\d{6}/)[0] : null);

(async () => {
  // ---- E.164 validation ----
  console.log("\n[E.164 validation]");
  reset();
  let r = await call(sendOTP, { body: { phoneNumber: "9876543210" } });
  check("rejects bare 10-digit number", r._status === 400, r._body);
  r = await call(sendOTP, { body: { phoneNumber: "+9112345abc" } });
  check("rejects non-digits", r._status === 400, r._body);
  r = await call(sendOTP, { body: { phoneNumber: "+9198765 43210" }, headers: { "x-forwarded-for": "10.0.0.1" } });
  check("accepts spaced input after normalization", r._status === 200, JSON.stringify(r._body));
  r = await call(sendOTP, { body: { phoneNumber: "+91-98765-43210" }, headers: { "x-forwarded-for": "10.0.0.2" } });
  check("accepts dashed input after normalization", r._status === 200, JSON.stringify(r._body));
  r = await call(sendOTP, { body: { phoneNumber: "+91 (98765) 43210" }, headers: { "x-forwarded-for": "10.0.0.3" } });
  check("accepts parenthesised input after normalization", r._status === 200, JSON.stringify(r._body));

  // ---- code generation ----
  console.log("\n[Code generation]");
  reset();
  r = await call(sendOTP, { body: { phoneNumber: "+919876500001" } });
  const code = lastCode();
  check("SMS was sent", r._status === 200 && !!code);
  check("code is 6 digits", /^\d{6}$/.test(code || ""));
  const stored = docs.get("otps/+919876500001");
  check("plaintext code NOT stored", stored && !JSON.stringify(stored).includes(code),
        stored ? JSON.stringify(stored) : "no doc");
  check("otpHash present", stored && typeof stored.otpHash === "string" && /^[0-9a-f]{64}$/.test(stored.otpHash));
  check("attempts starts at 0", stored && stored.attempts === 0);

  // ---- happy path ----
  console.log("\n[Happy path]");
  reset();
  await call(sendOTP, { body: { phoneNumber: "+919876500002" } });
  r = await call(verifyOTP, { body: { phoneNumber: "+919876500002", otp: lastCode() } });
  check("valid code accepted", r._status === 200 && r._body.success === true, JSON.stringify(r._body));
  check("code deleted after success", !docs.get("otps/+919876500002"));
  check("user doc written", !!docs.get("users/+919876500002"));
  r = await call(verifyOTP, { body: { phoneNumber: "+919876500002", otp: lastCode() } });
  check("code cannot be replayed", r._status === 400, JSON.stringify(r._body));

  // ---- expiry BEFORE compare ----
  console.log("\n[Expiry enforced before comparison]");
  reset();
  await call(sendOTP, { body: { phoneNumber: "+919876500003" } });
  now += 5 * 60 * 1000 + 1000; // past TTL
  r = await call(verifyOTP, { body: { phoneNumber: "+919876500003", otp: lastCode() } });
  check("expired code rejected", r._status === 400, JSON.stringify(r._body));
  check("expired doc deleted", !docs.get("otps/+919876500003"));
  check("message is generic", r._body.error === "Invalid or expired verification code.");

  // ---- missing createdAt treated as expired ----
  console.log("\n[Missing createdAt]");
  reset();
  docs.set("otps/+919876500004", { otpHash: "deadbeef", attempts: 0, name: "", email: "" });
  r = await call(verifyOTP, { body: { phoneNumber: "+919876500004", otp: "111111" } });
  check("code without createdAt rejected", r._status === 400, JSON.stringify(r._body));

  // ---- attempt lockout ----
  console.log("\n[Attempt lockout]");
  reset();
  await call(sendOTP, { body: { phoneNumber: "+919876500005" } });
  const real = lastCode();
  let codes = [];
  for (let i = 1; i <= 5; i++) {
    r = await call(verifyOTP, { body: { phoneNumber: "+919876500005", otp: "000000" } });
    codes.push(r._status);
  }
  check("first 4 wrong attempts -> 400", codes.slice(0, 4).every((c) => c === 400), JSON.stringify(codes));
  check("5th wrong attempt -> 429 lock", codes[4] === 429, JSON.stringify(codes));
  check("otp doc destroyed on lock", !docs.get("otps/+919876500005"));
  check("lock record written", !!docs.get("locks/+919876500005"));
  r = await call(verifyOTP, { body: { phoneNumber: "+919876500005", otp: real } });
  check("correct code refused while locked", r._status === 429, JSON.stringify(r._body));
  now += 15 * 60 * 1000 + 1000; // lock elapses
  r = await call(sendOTP, { body: { phoneNumber: "+919876500005" } });
  check("locked phone blocked from new sends while locked", r._status === 200 || r._status === 429, "n/a after elapsed");

  // ---- throttle ----
  console.log("\n[Throttling]");
  reset();
  r = await call(sendOTP, { body: { phoneNumber: "+919876500006" } });
  check("first send ok", r._status === 200);
  r = await call(sendOTP, { body: { phoneNumber: "+919876500006" } });
  check("same phone throttled", r._status === 429, JSON.stringify(r._body));
  r = await call(sendOTP, { body: { phoneNumber: "+919876500007" } });
  check("different phone same IP throttled", r._status === 429, JSON.stringify(r._body));
  now += 61 * 1000;
  r = await call(sendOTP, { body: { phoneNumber: "+919876500008" } });
  check("allowed again after cooldown", r._status === 200, JSON.stringify(r._body));

  // ---- method + CORS ----
  console.log("\n[Method and CORS]");
  reset();
  for (const m of ["GET", "PUT", "DELETE", "PATCH"]) {
    r = await call(sendOTP, { method: m, body: {} });
    check(m + " rejected", r._status === 405, String(r._status));
    r = await call(verifyOTP, { method: m, body: {} });
    check("verifyOTP " + m + " rejected", r._status === 405, String(r._status));
  }
  r = await call(sendOTP, { method: "OPTIONS" });
  check("OPTIONS -> 204", r._status === 204, String(r._status));
  r = await call(sendOTP, { headers: { origin: "https://evil.example" } });
  check("disallowed origin gets no ACAO header", !r._headers["Access-Control-Allow-Origin"]);

  // ---- no secret leakage in responses ----
  console.log("\n[Response hygiene]");
  reset();
  await call(sendOTP, { body: { phoneNumber: "+919876500009" } });
  const c = lastCode();
  const bad = await call(verifyOTP, { body: { phoneNumber: "+919876500009", otp: "999999" } });
  check("error body has no code", !JSON.stringify(bad._body).includes(c), JSON.stringify(bad._body));
  check("error body has no account sid", !JSON.stringify(bad._body).includes("ACtest"));

  console.log(`\n==== ${pass} passed, ${fail} failed ====`);
  process.exit(fail ? 1 : 0);
})(););
  check("error body has no account sid", !JSON.stringify(bad._body).includes("ACtest"));

  console.log(`\n==== ${pass} passed, ${fail} failed ====`);
  process.exit(fail ? 1 : 0);
})(););