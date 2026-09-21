package io.github.censync.hh

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.plugins.jpeg.JPEGHuffmanTable
import javax.imageio.plugins.jpeg.JPEGQTable
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The encoders checked with independent decoders: `javax.imageio`, in tests only. */
class DecoderTest {
    private fun sample(size: Int, mode: Mode, options: RenderOptions = RenderOptions.DEFAULT): HhImage =
        Fingerprint.fromBytes(ByteArray(32) { (it * 37 + 11).toByte() }, mode).render(size, options)

    /** Decodes to straight ARGB ints. */
    private fun decode(bytes: ByteArray): IntArray {
        val image = assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)), "the decoder rejected the file")
        return IntArray(image.width * image.height) { image.getRGB(it % image.width, it / image.width) }
    }

    private fun flattened(image: HhImage, matte: Int): IntArray = image.toArgb().map { p ->
        val a = p ushr 24
        var out = 0xFF shl 24
        for (shift in intArrayOf(16, 8, 0)) {
            out = out or (flatten((p ushr shift) and 0xFF, a, (matte ushr shift) and 0xFF) shl shift)
        }
        out
    }.toIntArray()

    @Test
    fun pngDecodesToTheSamePixels() {
        val round = RenderOptions(Shape.ROUND, FrameStyle.GAPS, 0x121212, 200, 100)
        for (image in listOf(sample(16, Mode.UNIVERSAL), sample(33, Mode.KEYED), sample(128, Mode.KEYED, round))) {
            val decoded = decode(image.encodePng())
            val expected = image.toArgb()
            for (i in expected.indices) {
                // A fully transparent pixel has no colour to compare.
                if (expected[i] ushr 24 != 0 || decoded[i] ushr 24 != 0) {
                    assertEquals(expected[i], decoded[i], "pixel $i")
                }
            }
        }
    }

    @Test
    fun pngOfArbitraryPixelsDecodes() {
        val random = Random(11)
        for ((width, height) in listOf(1 to 1, 3 to 5, 64 to 9, 300 to 3)) {
            val rgba = random.nextBytes(width * height * 4)
            for (p in 3 until rgba.size step 4) {
                rgba[p] = 0xFF.toByte()
            }
            val image = HhImage.ofRgba(width, height, rgba)
            assertContentEquals(image.toArgb(), decode(image.encodePng()), "$width x $height")
        }
    }

    @Test
    fun bmpDecodesToTheFlattenedPixels() {
        val image = sample(33, Mode.KEYED)
        for (matte in intArrayOf(0xFFFFFF, 0x0048FF)) {
            assertContentEquals(flattened(image, matte), decode(image.encodeBmp(matte)))
        }
    }

    @Test
    fun jpegDecodesCloseToThePixels() {
        val image = sample(100, Mode.UNIVERSAL) // not a multiple of 8
        val expected = flattened(image, 0xFFFFFF)
        for ((quality, meanLimit, maxLimit) in listOf(Triple(100, 0.3, 6), Triple(92, 1.5, 70), Triple(50, 4.0, 140))) {
            val decoded = decode(image.encodeJpeg(quality))
            assertEquals(expected.size, decoded.size)
            var total = 0L
            var worst = 0
            for (i in expected.indices) {
                for (shift in intArrayOf(16, 8, 0)) {
                    val d = kotlin.math.abs(((expected[i] ushr shift) and 0xFF) - ((decoded[i] ushr shift) and 0xFF))
                    total += d
                    worst = maxOf(worst, d)
                }
            }
            val mean = total.toDouble() / (expected.size * 3)
            assertTrue(mean < meanLimit, "quality $quality: mean error $mean")
            assertTrue(worst <= maxLimit, "quality $quality: worst error $worst")
        }
    }

    @Test
    fun jpegOfNoiseAndOfOddSizesDecodes() {
        val random = Random(5)
        for ((width, height) in listOf(1 to 1, 7 to 9, 8 to 8, 17 to 24)) {
            val image = HhImage.ofRgba(width, height, random.nextBytes(width * height * 4))
            for (quality in intArrayOf(50, 100)) {
                assertEquals(width * height, decode(image.encodeJpeg(quality, 0x808080)).size)
            }
        }
    }

    // The tables of ITU-T T.81 annex K, as the JDK holds them.
    @Test
    fun jpegTablesAreTheStandardOnes() {
        fun check(spec: JpegEncoder.HuffmanSpec, standard: JPEGHuffmanTable, name: String) {
            assertContentEquals(standard.lengths.map { it.toInt() }, spec.counts.toList(), "$name lengths")
            assertContentEquals(standard.values.map { it.toInt() }, spec.symbols.toList(), "$name symbols")
        }
        check(JpegEncoder.DC_LUMINANCE, JPEGHuffmanTable.StdDCLuminance, "DC luminance")
        check(JpegEncoder.DC_CHROMINANCE, JPEGHuffmanTable.StdDCChrominance, "DC chrominance")
        check(JpegEncoder.AC_LUMINANCE, JPEGHuffmanTable.StdACLuminance, "AC luminance")
        check(JpegEncoder.AC_CHROMINANCE, JPEGHuffmanTable.StdACChrominance, "AC chrominance")
        // Both sides keep quantiser tables in natural (row-major) order.
        assertContentEquals(JPEGQTable.K1Luminance.table, JpegEncoder.LUMINANCE_QUANTISER)
        assertContentEquals(JPEGQTable.K2Chrominance.table, JpegEncoder.CHROMINANCE_QUANTISER)
        assertEquals((0 until 64).toList(), JpegEncoder.ZIGZAG.sorted())
    }

    @Test
    fun checksumsHaveTheirCheckValues() {
        assertEquals(0xCBF43926.toInt(), Checksums.crc32("123456789".ascii()))
        assertEquals(0x11E60398, Checksums.adler32("Wikipedia".ascii()))
        assertEquals(0x9F51D664.toInt(), Checksums.adler32(ByteArray(20000) { 0xFF.toByte() }))
        assertEquals(1, Checksums.adler32(ByteArray(0)))
    }
}
