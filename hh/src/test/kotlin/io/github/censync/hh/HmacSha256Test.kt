package io.github.censync.hh

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class HmacSha256Test {
    private fun tagHex(key: ByteArray, data: ByteArray): String = HmacSha256.tag(key, data).toHex()

    // RFC 4231 section 4, HMAC-SHA-256 results of test cases 1 to 7.
    @Test
    fun rfc4231Case1() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            tagHex(repeatByte(0x0B, 20), "Hi There".ascii()),
        )
    }

    @Test
    fun rfc4231Case2KeyShorterThanTheOutput() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            tagHex("Jefe".ascii(), "what do ya want for nothing?".ascii()),
        )
    }

    @Test
    fun rfc4231Case3CombinedLengthAboveTheBlockSize() {
        assertEquals(
            "773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe",
            tagHex(repeatByte(0xAA, 20), repeatByte(0xDD, 50)),
        )
    }

    @Test
    fun rfc4231Case4CombinedLengthAboveTheBlockSize() {
        assertEquals(
            "82558a389a443c0ea4cc819899f2083a85f0faa3e578f8077a2e3ff46729665b",
            tagHex(ByteArray(25) { (it + 1).toByte() }, repeatByte(0xCD, 50)),
        )
    }

    @Test
    fun rfc4231Case5TruncationTo128Bits() {
        val tag = tagHex(repeatByte(0x0C, 20), "Test With Truncation".ascii())
        assertEquals("a3b6167473100ee06e0c796c2955552b", tag.substring(0, 32))
    }

    @Test
    fun rfc4231Case6KeyLargerThanTheBlockSize() {
        assertEquals(
            "60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54",
            tagHex(repeatByte(0xAA, 131), "Test Using Larger Than Block-Size Key - Hash Key First".ascii()),
        )
    }

    @Test
    fun rfc4231Case7KeyAndDataLargerThanTheBlockSize() {
        val data = "This is a test using a larger than block-size key and a larger than block-size data. " +
            "The key needs to be hashed before being used by the HMAC algorithm."
        assertEquals(
            "9b09ffa71b942fcb27635fbcd5b0e944bfdc63644f0713938a7f51535c3a35e2",
            tagHex(repeatByte(0xAA, 131), data.ascii()),
        )
    }

    // Values computed independently with Python's hmac module.
    @Test
    fun keyLengthsAtTheBlockBoundary() {
        val data = "hh".ascii()
        val key64 = repeatByte(0x55, 64)
        val key65 = repeatByte(0x55, 65)
        assertEquals("a3a13d0a104e002f7998293bf9b0edc93adf3aba32109607a5cc6d8c69b2a93e", tagHex(key64, data))
        assertEquals("4742601e923f054465a5482683c5df7ac5d98d7b5e8107c3cd7212a5bd049de8", tagHex(key65, data))
        assertEquals("1d7771f7c99967222404295da900e866d9351b17739ef7508840e184dbe2bd6f", tagHex(ByteArray(0), data))
    }

    // RFC 2104 zero-pads short keys: K and K || 0x00 are the same key, and the empty key equals 32 zero bytes.
    @Test
    fun zeroPaddingMakesTrailingZeroBytesIrrelevant() {
        val data = "hh".ascii()
        assertEquals(tagHex("Jefe".ascii(), data), tagHex("Jefe".ascii() + byteArrayOf(0), data))
        assertEquals(tagHex(ByteArray(0), data), tagHex(ByteArray(32), data))
    }

    @Test
    fun incrementalUpdatesMatchTheOneShotTag() {
        val key = repeatByte(0xAA, 131)
        val data = ByteArray(200) { (it * 7).toByte() }
        val expected = tagHex(key, data)
        for (step in 1..data.size step 7) {
            val mac = HmacSha256(key)
            var pos = 0
            while (pos < data.size) {
                val n = minOf(step, data.size - pos)
                mac.update(data, pos, n)
                pos += n
            }
            assertEquals(expected, mac.finish().toHex(), "step $step")
        }
    }

    // Test-only cross-check against the platform implementation. javax.crypto rejects empty keys, so key
    // lengths start at 1.
    @Test
    fun matchesThePlatformMac() {
        val random = Random(4231)
        for (keyLength in intArrayOf(1, 16, 32, 63, 64, 65, 100, 200)) {
            for (dataLength in 0..130 step 13) {
                val key = random.nextBytes(keyLength)
                val data = random.nextBytes(dataLength)
                val platform = Mac.getInstance("HmacSHA256")
                platform.init(SecretKeySpec(key, "HmacSHA256"))
                assertEquals(platform.doFinal(data).toHex(), tagHex(key, data), "key $keyLength, data $dataLength")
            }
        }
    }
}
