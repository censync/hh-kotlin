package io.github.censync.hh

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deterministic pseudo-random loops: any input gives a result or an [HhException], never anything else. */
class RobustnessTest {
    @Test
    fun randomFingerprintsRenderWithRandomOptions() {
        val random = Random(0xF00D)
        var rendered = 0
        repeat(1500) {
            val fp = Fingerprint.fromBytes(random.nextBytes(32), Mode.entries[random.nextInt(2)])
            val options = RenderOptions(
                shape = Shape.entries[random.nextInt(2)],
                frame = FrameStyle.entries[random.nextInt(10)],
                backgroundRgb = random.nextInt(0x1000000),
                backgroundAlpha = if (random.nextInt(3) == 0) 255 else random.nextInt(256),
                frameAlpha = random.nextInt(256),
            )
            val size = if (random.nextInt(100) == 0) random.nextInt(2000) else 8 + random.nextInt(90)
            try {
                val image = fp.render(size, options)
                rendered++
                assertEquals(size, image.width)
                val rgba = image.toRgba()
                assertEquals(size * size * 4, rgba.size)
                val zero = 0.toByte()
                for (p in rgba.indices step 4) {
                    // A transparent pixel is 00 00 00 00.
                    val colourless = rgba[p] == zero && rgba[p + 1] == zero && rgba[p + 2] == zero
                    assertTrue(rgba[p + 3] != zero || colourless, "pixel ${p / 4}")
                }
            } catch (e: HhException) {
                val expected = setOf(HhErrorCode.INVALID_SIZE, HhErrorCode.INVALID_FRAME, HhErrorCode.LOW_CONTRAST)
                assertTrue(e.error in expected, e.error.specName)
            }
        }
        assertTrue(rendered > 200, "only $rendered renders succeeded")
    }

    @Test
    fun hexadecimalParsingAgreesWithASimpleOracle() {
        val random = Random(0xA11CE)
        val alphabet = "0123456789abcdefABCDEFxXgG -:\n"
        repeat(4000) {
            val text = buildString {
                if (random.nextInt(4) == 0) {
                    append(if (random.nextBoolean()) "0x" else "0X")
                }
                repeat(random.nextInt(12)) {
                    append(alphabet[if (random.nextInt(10) < 9) random.nextInt(22) else random.nextInt(30)])
                }
            }
            val digits = if (text.startsWith("0x") || text.startsWith("0X")) text.substring(2) else text
            val valid = digits.isNotEmpty() && digits.length % 2 == 0 && digits.all { it in "0123456789abcdefABCDEF" }
            val decoded = try {
                Derive.decodeHex(text)
            } catch (e: HhException) {
                assertEquals(HhErrorCode.INVALID_HEX, e.error)
                null
            }
            assertEquals(valid, decoded != null, "\"$text\"")
            if (decoded != null) {
                assertEquals(digits.lowercase(), decoded.toHex())
            }
        }
    }
}
