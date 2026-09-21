package io.github.censync.hh

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    private fun hashHex(message: ByteArray): String = Sha256.digest(message).toHex()

    // FIPS 180-4 examples (NIST CSRC "Examples with Intermediate Values", SHA-256).
    @Test
    fun fipsOneBlockMessage() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hashHex("abc".ascii()))
    }

    @Test
    fun fipsTwoBlockMessage() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hashHex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".ascii()),
        )
    }

    @Test
    fun fips896BitMessage() {
        val message = "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmn" +
            "hijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu"
        assertEquals("cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1", hashHex(message.ascii()))
    }

    @Test
    fun emptyMessage() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hashHex(ByteArray(0)))
    }

    @Test
    fun oneMillionRepetitionsOfA() {
        val hasher = Sha256()
        val chunk = repeatByte('a'.code, 1000)
        repeat(1000) { hasher.update(chunk) }
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", hasher.finish().toHex())
    }

    // Lengths around the padding boundaries; values computed independently with coreutils sha256sum.
    @Test
    fun lengthsAroundBlockBoundaries() {
        val vectors = mapOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            63 to "7d3e74a05d7db15bce4ad9ec0658ea98e3f06eeecf16b4c6fff2da457ddc2f34",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            65 to "635361c48bb9eab14198e76ea8ab7f1a41685d6ad62aa9146d301d4f17eb0ae0",
            119 to "31eba51c313a5c08226adf18d4a359cfdfd8d2e816b13f4af952f7ea6584dcfb",
            120 to "2f3d335432c70b580af0e8e1b3674a7c020d683aa5f73aaaedfdc55af904c21c",
            127 to "c57e9278af78fa3cab38667bef4ce29d783787a2f731d4e12200270f0c32320a",
            128 to "6836cf13bac400e9105071cd6af47084dfacad4e5e302c94bfed24e013afb73e",
        )
        for ((length, digest) in vectors) {
            assertEquals(digest, hashHex(repeatByte('a'.code, length)), "length $length")
        }
    }

    @Test
    fun incrementalUpdatesMatchTheOneShotDigest() {
        val message = ByteArray(1024) { it.toByte() }
        val expected = "785b0751fc2c53dc14a4ce3d800e69ef9ce1009eb327ccf458afe09c242c26c9"
        assertEquals(expected, hashHex(message))
        for (step in 1..130) {
            val hasher = Sha256()
            var pos = 0
            while (pos < message.size) {
                val n = minOf(step, message.size - pos)
                hasher.update(message, pos, n)
                pos += n
            }
            assertEquals(expected, hasher.finish().toHex(), "step $step")
        }
    }

    @Test
    fun resetAllowsReuse() {
        val hasher = Sha256()
        hasher.update("abc".ascii())
        val first = hasher.finish().toHex()
        hasher.reset()
        hasher.update("abc".ascii())
        assertEquals(first, hasher.finish().toHex())
    }

    // Test-only cross-check against the platform implementation.
    @Test
    fun matchesThePlatformMessageDigest() {
        val random = Random(20260921)
        val platform = MessageDigest.getInstance("SHA-256")
        for (length in 0..300) {
            val message = random.nextBytes(length)
            assertEquals(platform.digest(message).toHex(), hashHex(message), "length $length")
        }
    }
}
