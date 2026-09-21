package io.github.censync.hh

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reproduces every record of `testdata/vectors.tsv`, the golden vectors of hh-cpp (section 15 of the
 * specification). The file is a byte-identical copy; a mismatch is a bug in this port, never in the vectors.
 */
class VectorsTest {
    private val testdata = File(System.getProperty("hh.testdata") ?: "../testdata")

    private fun records(type: String): List<List<String>> =
        File(testdata, "vectors.tsv").readLines()
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split('\t') }
            .filter { it[0] == type }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** `hex:<bytes>` or `fill:<byte>:<count>`. */
    private fun input(field: String): ByteArray {
        if (field.startsWith("hex:")) {
            return field.substring(4).hexToBytes()
        }
        val parts = field.split(':')
        return ByteArray(parts[2].toInt()) { parts[1].hexToBytes()[0] }
    }

    private fun cellsText(fp: Fingerprint): String =
        fp.layout().cells.joinToString("") { "${it.figure.ordinal}${it.colour}" }

    private class RenderCase(val fp: Fingerprint, val size: Int, val options: RenderOptions)

    /** The fields fp, mode, size, shape, frame, background, frame alpha starting at [at]. */
    private fun renderCase(r: List<String>, at: Int): RenderCase {
        val mode = Mode.valueOf(r[at + 1].uppercase())
        val background = r[at + 5].hexToBytes()
        val options = RenderOptions(
            shape = Shape.valueOf(r[at + 3].uppercase()),
            frame = FrameStyle.valueOf(r[at + 4].uppercase()),
            backgroundRgb = ((background[0].toInt() and 0xFF) shl 16) or ((background[1].toInt() and 0xFF) shl 8) or
                (background[2].toInt() and 0xFF),
            backgroundAlpha = background[3].toInt() and 0xFF,
            frameAlpha = r[at + 6].toInt(),
        )
        return RenderCase(Fingerprint.fromBytes(r[at].hexToBytes(), mode), r[at + 2].toInt(), options)
    }

    @Test
    fun theFileIsPresentAndComplete() {
        assertTrue(records("D").size >= 20)
        assertTrue(records("H").size >= 20)
        assertTrue(records("K").size >= 8)
        assertTrue(records("C").size >= 20)
        assertTrue(records("R").size >= 80)
        assertTrue(records("E").size >= 25)
        assertTrue(records("G").size >= 20)
        assertTrue(records("W").size >= 15)
        assertTrue(records("I").size >= 14)
        assertTrue(records("F").size >= 16)
    }

    // testdata/SOURCE names the hh-cpp release the files came from and their SHA-256.
    @Test
    fun theCopyIsWhatSourceRecords() {
        val lines = File(testdata, "SOURCE").readLines()
        // The vectors come from a release of hh-cpp, never from an untagged commit.
        val tagged = lines.any { Regex("^tag: v\\d+\\.\\d+\\.\\d+$").matches(it) }
        assertTrue(tagged, "testdata/SOURCE names no release tag")
        assertTrue(lines.any { it.startsWith("commit: ") })
        val hashes = lines.filter { Regex("^[0-9a-f]{64} {2}\\S+$").matches(it) }.map { it.split("  ") }
        assertEquals(1 + records("G").size, hashes.size)
        for ((hash, name) in hashes) {
            assertEquals(hash, sha256(File(testdata, name).readBytes()), name)
        }
    }

    @Test
    fun derivationRecords() {
        for (r in records("D")) {
            val id = r[1]
            assertEquals(16, r.size, id)
            val data = input(r[3])
            val digest = if (r[2] == "text") BaseDigest.ofText(data.decodeToString()) else BaseDigest.of(data)
            assertEquals(r[7], digest.toByteArray().toHex(), id)
            if (r[2] == "text") {
                assertEquals(digest, BaseDigest.ofUtf8(data), id)
            }
            val universal = Fingerprint.universal(digest)
            assertEquals(r[9], universal.toByteArray().toHex(), id)
            assertEquals(r[12], cellsText(universal), id)
            assertEquals(r[14], universal.tag, id)
            if (r[5] != "-") {
                // M1 and d0 = SHA-256(M1), checked with the platform digest.
                assertEquals(r[6], sha256(r[5].hexToBytes()), id)
                val kind = if (r[2] == "text") Derive.KIND_TEXT else Derive.KIND_BINARY
                assertEquals(r[5], (Derive.m1Header(kind, data.size) + data).toHex(), id)
            }
            assertEquals(r[8], Derive.m2(digest.toByteArray()).toHex(), id)
            if (r[4] == "-") {
                assertEquals("-", r[10], id)
                continue
            }
            SecretKey.of(r[4].hexToBytes()).use { key ->
                assertEquals(r[11], key.checkValue.toHex(), id)
                val keyed = Fingerprint.keyed(digest, key)
                assertEquals(Mode.KEYED, keyed.mode, id)
                assertEquals(r[10], keyed.toByteArray().toHex(), id)
                assertEquals(r[13], cellsText(keyed), id)
                assertEquals(r[15], keyed.tag, id)
            }
        }
    }

    @Test
    fun hexadecimalInputRecords() {
        for (r in records("H")) {
            val text = r[2].hexToBytes().decodeToString()
            val expected = r[3]
            if (expected.isNotEmpty() && expected.all { it in "0123456789abcdef" }) {
                assertEquals(BaseDigest.of(expected.hexToBytes()), BaseDigest.ofHex(text), r[1])
            } else {
                val e = assertFailsWith<HhException>(r[1]) { BaseDigest.ofHex(text) }
                assertEquals(expected, e.error.specName, r[1])
                assertNull(BaseDigest.ofHexOrNull(text), r[1])
            }
        }
    }

    @Test
    fun keyRecords() {
        for (r in records("K")) {
            val raw = if (r[2] == "-") ByteArray(0) else r[2].hexToBytes()
            val key = SecretKey.ofOrNull(raw)
            if (key != null) {
                assertEquals(r[3], key.checkValue.toHex(), r[1])
                key.close()
            } else {
                assertEquals(r[3], assertFailsWith<HhException> { SecretKey.of(raw) }.error.specName, r[1])
            }
        }
    }

    @Test
    fun contrastRecords() {
        for (r in records("C")) {
            val background = r[2].hexToBytes()
            val options = RenderOptions(
                backgroundRgb = r[2].substring(0, 6).toInt(16),
                backgroundAlpha = background[3].toInt() and 0xFF,
                frameAlpha = r[3].toInt(),
            )
            val report = options.measureContrast(r[4].toInt(16))
            assertEquals(r[5].toInt(), report.figuresX100, r[1])
            assertEquals(r[6].toInt(), report.frameX100, r[1])
        }
    }

    @Test
    fun renderRecords() {
        for (r in records("R")) {
            val id = r[1]
            assertEquals(15, r.size, id)
            val c = renderCase(r, 2)
            val image = c.fp.render(c.size, c.options)
            val matte = r[10].toInt(16)
            assertEquals(r[11], sha256(image.toRgba()), "$id rgba")
            assertEquals(r[12], sha256(image.encodePng()), "$id png")
            assertEquals(r[13], sha256(image.encodeBmp(matte)), "$id bmp")
            assertEquals(r[14], sha256(image.encodeJpeg(r[9].toInt(), matte)), "$id jpeg")
        }
    }

    @Test
    fun renderErrorRecords() {
        for (r in records("E")) {
            val c = renderCase(r, 2)
            val e = assertFailsWith<HhException>(r[1]) { c.fp.render(c.size, c.options) }
            assertEquals(r[9], e.error.specName, r[1])
            assertNull(c.fp.renderOrNull(c.size, c.options), r[1])
        }
    }

    /** The test image patterns of section 15: `flat:<RRGGBBAA>`, `noise:<seed>`, `opaque:<seed>`. */
    private fun pattern(name: String, width: Int, height: Int): ByteArray {
        val kind = name.substringBefore(':')
        val argument = name.substringAfter(':')
        val rgba = ByteArray(width * height * 4)
        if (kind == "flat") {
            val value = argument.hexToBytes()
            for (i in rgba.indices) {
                rgba[i] = value[i % 4]
            }
            return rgba
        }
        var x = argument.toInt()
        for (i in rgba.indices) {
            // Int arithmetic is modulo 2^32; the mask gives the value modulo 2^31.
            x = (x * 1103515245 + 12345) and 0x7FFFFFFF
            rgba[i] = (x ushr 16).toByte()
        }
        if (kind == "opaque") {
            for (i in 3 until rgba.size step 4) {
                rgba[i] = 0xFF.toByte()
            }
        }
        return rgba
    }

    @Test
    fun sizeSweepRecords() {
        for (r in records("W")) {
            assertEquals(11, r.size, r[1])
            val c = renderCase(listOf(r[2], r[3], r[8], r[4], r[5], r[6], r[7]), 0)
            val hasher = MessageDigest.getInstance("SHA-256")
            for (size in c.size..r[9].toInt()) {
                hasher.update(c.fp.render(size, c.options).toRgba())
            }
            assertEquals(r[10], hasher.digest().toHex(), r[1])
        }
    }

    @Test
    fun imageRecords() {
        for (r in records("I")) {
            val id = r[1]
            assertEquals(11, r.size, id)
            val image = HhImage.ofRgba(r[2].toInt(), r[3].toInt(), pattern(r[4], r[2].toInt(), r[3].toInt()))
            val matte = r[6].toInt(16)
            assertEquals(r[7], sha256(image.toRgba()), "$id rgba")
            assertEquals(r[8], sha256(image.encodePng()), "$id png")
            assertEquals(r[9], sha256(image.encodeBmp(matte)), "$id bmp")
            assertEquals(r[10], sha256(image.encodeJpeg(r[5].toInt(), matte)), "$id jpeg")
        }
    }

    @Test
    fun failureRecords() {
        for (r in records("F")) {
            val e = assertFailsWith<HhException>(r[1]) {
                when (r[2]) {
                    "digest" -> {
                        val data = input(r[4])
                        if (r[3] == "text") BaseDigest.ofText(data.decodeToString()) else BaseDigest.of(data)
                    }
                    "jpeg" -> HhImage.ofRgba(8, 8, pattern("flat:ffffffff", 8, 8)).encodeJpeg(r[3].toInt())
                    "image" -> HhImage.ofRgba(r[3].toInt(), r[4].toInt(), ByteArray(r[5].toInt()) { 0x7F })
                    else -> error("unknown operation ${r[2]}")
                }
            }
            assertEquals(r.last(), e.error.specName, r[1])
        }
    }

    @Test
    fun goldenFiles() {
        for (r in records("G")) {
            val c = renderCase(r, 3)
            val expected = File(testdata, "golden/${r[2]}").readBytes()
            assertContentEquals(expected, c.fp.render(c.size, c.options).encodePng(), r[2])
        }
    }
}
