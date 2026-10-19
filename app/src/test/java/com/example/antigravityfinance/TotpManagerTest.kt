package com.example.antigravityfinance

import com.example.antigravityfinance.service.security.TotpManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verified against the test vectors in RFC 6238 Appendix B (SHA-1, 8 digits). The shared
 * secret there is the ASCII string "12345678901234567890", whose Base32 form is
 * GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ. Those values come from the RFC, so this test checks the
 * implementation against an external authority rather than against itself.
 */
class TotpManagerTest {

    private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun rfc6238TestVectors() {
        // RFC times are in seconds; the vectors are 8-digit, so we take the low 6.
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130"
        )

        for ((seconds, expected8) in vectors) {
            val actual = TotpManager.codeAt(rfcSecret, seconds * 1000)
            assertEquals(
                "RFC 6238 vector at t=$seconds",
                expected8.takeLast(6),
                actual
            )
        }
    }

    @Test
    fun base32RoundTrip() {
        val secret = TotpManager.generateSecret()
        val decoded = TotpManager.base32Decode(TotpManager.normalizeSecret(secret))
        assertNotEquals(null, decoded)
        // Re-encoding the decoded bytes must reproduce the same secret.
        assertEquals(TotpManager.normalizeSecret(secret), TotpManager.base32Encode(decoded!!))
    }

    @Test
    fun generatedSecretProducesSixDigits() {
        val secret = TotpManager.generateSecret()
        repeat(10) {
            val code = TotpManager.codeAt(secret)
            assertTrue("code should be 6 digits, was $code", code != null && code.length == 6)
            assertTrue("code should be numeric, was $code", code!!.all { c -> c.isDigit() })
        }
    }

    @Test
    fun verifyAcceptsCurrentCode() {
        val secret = TotpManager.generateSecret()
        val now = 1_700_000_000_000L
        assertTrue(TotpManager.verify(secret, TotpManager.codeAt(secret, now)!!, now))
    }

    @Test
    fun verifyRejectsWrongCode() {
        val secret = TotpManager.generateSecret()
        val now = 1_700_000_000_000L
        val valid = TotpManager.codeAt(secret, now)!!
        val wrong = if (valid == "000000") "111111" else "000000"
        assertFalse(TotpManager.verify(secret, wrong, now))
    }

    @Test
    fun verifyRejectsMalformedInput() {
        val secret = TotpManager.generateSecret()
        val now = 1_700_000_000_000L
        assertFalse("too short", TotpManager.verify(secret, "12345", now))
        assertFalse("too long", TotpManager.verify(secret, "1234567", now))
        assertFalse("non-numeric", TotpManager.verify(secret, "12345a", now))
        assertFalse("empty", TotpManager.verify(secret, "", now))
        assertFalse("malformed secret", TotpManager.verify("not!base32", "123456", now))
    }

    @Test
    fun verifyAcceptsAdjacentPeriodWithinWindow() {
        val secret = TotpManager.generateSecret()
        val now = 1_700_000_000_000L
        val previousPeriod = TotpManager.codeAt(secret, now - 30_000)!!
        assertTrue("previous period should pass inside the default window", TotpManager.verify(secret, previousPeriod, now))
        assertFalse(
            "previous period should fail with no window",
            TotpManager.verify(secret, previousPeriod, now, window = 0)
        )
    }

    @Test
    fun secretIsCaseAndSpaceInsensitive() {
        val secret = TotpManager.generateSecret()
        val now = 1_700_000_000_000L
        val expected = TotpManager.codeAt(secret, now)
        val spaced = secret.lowercase().chunked(4).joinToString(" ")
        assertEquals(expected, TotpManager.codeAt(spaced, now))
    }

    @Test
    fun secondsRemainingIsWithinPeriod() {
        val period = 30L * 1000L
        assertTrue(TotpManager.secondsRemaining(0L) in 1..30)
        assertTrue(TotpManager.secondsRemaining(period - 1) in 1..30)
        assertTrue(TotpManager.secondsRemaining(period * 5 + 10_000) in 1..30)
    }

    @Test
    fun otpAuthUriCarriesSecretAndIssuer() {
        val secret = TotpManager.generateSecret()
        val uri = TotpManager.otpAuthUri(secret, "user@example.com")
        assertTrue(uri.startsWith("otpauth://totp/"))
        assertTrue(uri.contains("secret=${TotpManager.normalizeSecret(secret)}"))
        assertTrue(uri.contains("issuer=FinKlar"))
        assertTrue(uri.contains("digits=6"))
        assertTrue(uri.contains("period=30"))
    }
}