package io.github.censync.hh

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pbkdf2HmacSha256Test {
    private fun deriveHex(password: String, salt: String, iterations: Int, length: Int): String =
        Pbkdf2HmacSha256.derive(password.ascii(), salt.ascii(), iterations, length).toHex()

    // RFC 7914 section 11, the two PBKDF2-HMAC-SHA256 test vectors.
    @Test
    fun rfc7914OneIteration() {
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc" +
                "49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            deriveHex("passwd", "salt", 1, 64),
        )
    }

    @Test
    fun rfc7914EightyThousandIterations() {
        assertEquals(
            "4ddcd8f60b98be21830cee5ef22701f9641a4418d04c0414aeff08876b34ab56" +
                "a1d425a1225833549adb841b51c9b3176a272bdebba1d078478f62b397f33c8d",
            deriveHex("Password", "NaCl", 80000, 64),
        )
    }

    // Values computed independently with Python's hashlib.pbkdf2_hmac.
    @Test
    fun singleBlockAndTruncatedOutputs() {
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            deriveHex("password", "salt", 4096, 32),
        )
        assertEquals("ae4d0c95af6b46d32d0adff928f06dd02a303f8e", deriveHex("password", "salt", 2, 20))
    }

    @Test
    fun outputPrefixDoesNotDependOnTheOutputLength() {
        val full = deriveHex("Password", "NaCl", 3, 96)
        assertEquals(full.substring(0, 64), deriveHex("Password", "NaCl", 3, 32))
        assertEquals(full.substring(0, 66), deriveHex("Password", "NaCl", 3, 33))
        assertEquals(full.substring(0, 2), deriveHex("Password", "NaCl", 3, 1))
    }

    @Test
    fun rejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { Pbkdf2HmacSha256.derive(ByteArray(1), ByteArray(1), 0, 32) }
        assertFailsWith<IllegalArgumentException> { Pbkdf2HmacSha256.derive(ByteArray(1), ByteArray(1), 1, -1) }
    }

    // Test-only cross-check against the platform implementation (ASCII passwords, which PBEKeySpec encodes as
    // their UTF-8 bytes).
    @Test
    fun matchesThePlatformKeyFactory() {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        for (iterations in intArrayOf(1, 2, 3, 100, 1000)) {
            for (length in intArrayOf(1, 31, 32, 33, 64, 65)) {
                val spec = PBEKeySpec("hh-password".toCharArray(), "hh-salt".ascii(), iterations, length * 8)
                val expected = factory.generateSecret(spec).encoded.toHex()
                assertEquals(expected, deriveHex("hh-password", "hh-salt", iterations, length), "c=$iterations")
            }
        }
    }
}
