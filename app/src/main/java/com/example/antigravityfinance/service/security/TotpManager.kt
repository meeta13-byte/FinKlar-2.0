package com.example.antigravityfinance.service.security

import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords (RFC 6238), the 6-digit rotating code used by Google
 * Authenticator, Authy and 1Password.
 *
 * Free and offline: verification is local HMAC maths, so unlike SMS there is no per-message
 * cost, no network call and no SIM-swap exposure. The secret never leaves the device, which
 * is why it is stored encrypted by [SecurityHelper].
 */
object TotpManager {
    private const val ALGORITHM = "HmacSHA1"
    private const val DIGITS = 6
    private const val PERIOD_SECONDS = 30L
    private const val SECRET_BYTES = 20
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** Codes within +/- this many periods of now are accepted, to absorb clock drift. */
    const val DEFAULT_WINDOW = 1

    /** A fresh random Base32 secret. 160 bits, the RFC 4226 recommended size. */
    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        SecureRandom().nextBytes(bytes)
        return base32Encode(bytes)
    }

    /**
     * The code for [secret] at [timeMillis]. [secret] may be raw Base32 text with or
     * without padding, and is matched case-insensitively with spaces ignored so a user can
     * paste it back exactly as it was displayed.
     */
    fun codeAt(secret: String, timeMillis: Long = System.currentTimeMillis()): String? {
        val key = base32Decode(normalizeSecret(secret)) ?: return null
        val counter = timeMillis / 1000L / PERIOD_SECONDS
        val digest = hmac(key, counter) ?: return null
        // RFC 4226 dynamic truncation.
        val offset = digest[digest.size - 1].toInt() and 0x0F
        val binary = ((digest[offset].toInt() and 0x7F) shl 24) or
            ((digest[offset + 1].toInt() and 0xFF) shl 16) or
            ((digest[offset + 2].toInt() and 0xFF) shl 8) or
            (digest[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * Checks [code] against [secret], accepting the surrounding [window] periods to allow
     * for a device clock that is slightly off.
     */
    fun verify(
        secret: String,
        code: String,
        timeMillis: Long = System.currentTimeMillis(),
        window: Int = DEFAULT_WINDOW
    ): Boolean {
        val trimmed = code.trim()
        if (trimmed.length != DIGITS || trimmed.any { !it.isDigit() }) return false

        val matched = (-window..window).any { offset ->
            val candidate = codeAt(secret, timeMillis + offset * PERIOD_SECONDS * 1000L) ?: return@any false
            constantTimeEquals(candidate, trimmed)
        }
        return matched
    }

    /** Seconds until the current code expires, for a countdown in the UI. */
    fun secondsRemaining(timeMillis: Long = System.currentTimeMillis()): Int {
        val elapsed = (timeMillis / 1000L) % PERIOD_SECONDS
        return (PERIOD_SECONDS - elapsed).toInt()
    }

    /**
     * The otpauth:// URI that authenticator apps import, either by scanning the QR code or
     * by pasting this text.
     */
    fun otpAuthUri(secret: String, accountName: String, issuer: String = ISSUER): String {
        val label = java.net.URLEncoder.encode("$issuer:$accountName", "UTF-8")
        val issuerParam = java.net.URLEncoder.encode(issuer, "UTF-8")
        return "otpauth://totp/$label?secret=${normalizeSecret(secret)}" +
            "&issuer=$issuerParam&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    const val ISSUER = "FinKlar"

    private fun hmac(key: ByteArray, counter: Long): ByteArray? = try {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(key, ALGORITHM))
        // The counter is an 8-byte big-endian value.
        val data = ByteArray(8)
        for (i in 0 until 8) {
            data[7 - i] = ((counter shr (8 * i)) and 0xFF).toByte()
        }
        mac.doFinal(data)
    } catch (e: Exception) {
        android.util.Log.w("TotpManager", "HMAC failed: " + e.javaClass.simpleName)
        null
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        java.security.MessageDigest.isEqual(
            a.toByteArray(Charsets.UTF_8),
            b.toByteArray(Charsets.UTF_8)
        )

    /** Uppercases and strips spaces and padding so display text can be re-entered freely. */
    fun normalizeSecret(secret: String): String =
        secret.uppercase(Locale.US).filter { it != ' ' && it != '-' && it != '=' }

    fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                sb.append(BASE32_ALPHABET[(buffer shr (bitsLeft - 5)) and 0x1F])
                bitsLeft -= 5
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    /** Returns null on any invalid character, so a malformed secret fails instead of throwing. */
    fun base32Decode(encoded: String): ByteArray? {
        if (encoded.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bitsLeft = 0
        for (c in encoded) {
            val index = BASE32_ALPHABET.indexOf(c)
            if (index < 0) return null
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.write((buffer shr (bitsLeft - 8)) and 0xFF)
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }
}