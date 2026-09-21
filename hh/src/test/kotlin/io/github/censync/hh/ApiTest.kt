package io.github.censync.hh

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTest {
    private val address = "5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed"
    private val addressDigest = "e212927148fcf76f6669c244a0db08bdd4f36dc50a378f6a1a3fe472807e7852"
    private val testKey = ByteArray(32) { it.toByte() }

    private fun error(block: () -> Unit): HhErrorCode = assertFailsWith<HhException> { block() }.error

    @Test
    fun everySpellingOfAnAddressGivesOneDigest() {
        val expected = BaseDigest.of(address.hexToBytes())
        assertEquals(addressDigest, expected.toByteArray().toHex())
        for (form in listOf(address, "0x$address", "0X${address.uppercase()}", address.lowercase())) {
            assertEquals(expected, BaseDigest.ofHex(form), form)
        }
        assertEquals(expected.hashCode(), BaseDigest.ofHex(address).hashCode())
    }

    @Test
    fun textAndBinaryInputsAreSeparated() {
        val text = "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
        assertNotEquals(BaseDigest.ofText(text), BaseDigest.of(text.ascii()))
        assertEquals(
            "dc705192e4a205d8c403ae7693290df45f09cec04116ad38f6140f349392548f",
            BaseDigest.ofText(text).toByteArray().toHex(),
        )
    }

    @Test
    fun invalidInputsThrowAndTheOrNullFormsReturnNull() {
        assertEquals(HhErrorCode.EMPTY_INPUT, error { BaseDigest.of(ByteArray(0)) })
        assertEquals(HhErrorCode.EMPTY_INPUT, error { BaseDigest.ofText("") })
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.of(ByteArray(1048577)) })
        assertEquals(HhErrorCode.INVALID_HEX, error { BaseDigest.ofHex("0xzz") })
        assertEquals(HhErrorCode.INVALID_HEX, error { BaseDigest.ofHex("") })
        assertEquals(HhErrorCode.INVALID_HEX, error { BaseDigest.ofHex("\u0661\u0662") }) // Arabic-Indic digits
        assertEquals(HhErrorCode.INVALID_DIGEST, error { BaseDigest.fromBytes(ByteArray(31)) })
        // An unpaired surrogate has no UTF-8 encoding; it must not be replaced silently.
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { BaseDigest.ofText("a\uD800b") })
        assertNull(BaseDigest.ofOrNull(ByteArray(0)))
        assertNull(BaseDigest.ofHexOrNull("abc"))
        assertNull(BaseDigest.ofTextOrNull("\uDC00"))
        assertNull(BaseDigest.fromBytesOrNull(ByteArray(33)))
        assertNull(HhImage.ofRgbaOrNull(2, 2, ByteArray(15)))
        assertNotNull(HhImage.ofRgbaOrNull(2, 2, ByteArray(16)))
        assertNotNull(BaseDigest.fromBytesOrNull(ByteArray(32)))
        assertTrue(assertFailsWith<IllegalArgumentException> { BaseDigest.ofHex("x") } is HhException)
    }

    @Test
    fun textIsCheckedForLengthThenForSurrogates() {
        // Every UTF-16 unit is at least one byte, so an overlong text is refused without being encoded,
        // and before its content is looked at.
        val tooLong = "a".repeat(1048577)
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofText(tooLong) })
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofText(tooLong.dropLast(1) + "\uD800") })
        // 1 048 576 units of two bytes each are too many bytes, though not too many units.
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofText("\u00e9".repeat(1048576)) })
        // Within the limit in UTF-16 units, over it in bytes, and ill-formed: the length is judged first.
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofText("\u00e9".repeat(600000) + "\uD800") })
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofText("a".repeat(1048574) + "\uDC00") })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { BaseDigest.ofText("a".repeat(1048573) + "\uDC00") })
        for (bad in listOf("\uD800", "\uDC00", "a\uD800", "\uDC00\uD800", "\uD800\uD800\uDC00", "x\uDBFFy")) {
            assertEquals(HhErrorCode.INVALID_ARGUMENT, error { BaseDigest.ofText(bad) })
        }
        assertNotNull(BaseDigest.ofTextOrNull("\uDBFF\uDFFF")) // the last code point, U+10FFFF
    }

    @Test
    fun hexadecimalSyntaxIsCheckedBeforeItsLength() {
        val tooLong = "a".repeat(2 * 1048577)
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofHex(tooLong) })
        assertEquals(HhErrorCode.INVALID_HEX, error { BaseDigest.ofHex(tooLong.dropLast(1) + "g") })
        assertEquals(HhErrorCode.INVALID_HEX, error { BaseDigest.ofHex(tooLong.dropLast(1)) })
    }

    @Test
    fun supplementaryCharactersAreEncodedAsFourBytes() {
        // U+10348 is one code point, two UTF-16 units, four UTF-8 bytes.
        assertEquals(BaseDigest.ofText("\uD800\uDF48"), BaseDigest.ofUtf8("f0908d88".hexToBytes()))
    }

    @Test
    fun utf8BytesOfWellFormedTextGiveTheDigestOfTheText() {
        val texts =
            listOf("T", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", "caf\u00E9 \u20AC \uD800\uDF48", "\uFEFFa")
        for (text in texts) {
            assertEquals(BaseDigest.ofText(text), BaseDigest.ofUtf8(text.encodeToByteArray()), text)
        }
        assertNotEquals(BaseDigest.ofUtf8("T".ascii()), BaseDigest.of("T".ascii()))
        // The array is read, not kept.
        val bytes = "T".ascii()
        val digest = BaseDigest.ofUtf8(bytes)
        bytes.fill(0)
        assertEquals(BaseDigest.ofText("T"), digest)
    }

    @Test
    fun utf8BytesAreTakenVerbatim() {
        // What hh_cli of hh-cpp prints for a text case with these bytes. None of them is well-formed UTF-8;
        // decoding them into a String first would hash U+FFFD instead.
        val expected = mapOf(
            "ff" to "4a6bd25a8411e1401a222181b32bf82a4bcd2cc2847d3c0f038cea1c4280192e",
            "c0af" to "f483d8ef392bd342d6dead33925851993eaa36c567c6156496af9b56e6ca0741",
            "eda080" to "be60434be29dc43cf38da4ed952ce0be2ab6cba1a79817e7c5539c8059be412e",
            "eda080edb080" to "f49477b8305b9aa92b3ca2a52098899a77fbd7f9fef188f2b4814166888d3463",
            "f4908080" to "eccd520b9bcfb9a6297cbb9ae4c21f59455ba6a352c994121e0199ade488ac1e",
            "e282" to "22d2ea04fb0947be78f460b344cd439e4202b8e81e022846e2f6410e51d18a86",
        )
        for ((utf8, digest) in expected) {
            assertEquals(digest, BaseDigest.ofUtf8(utf8.hexToBytes()).toByteArray().toHex(), utf8)
            val replaced = utf8.hexToBytes().decodeToString()
            assertNotEquals(BaseDigest.ofText(replaced), BaseDigest.ofUtf8(utf8.hexToBytes()), utf8)
        }
        assertEquals(
            "4a4c75ff44a92e8e46dfd48d7d91370b896e0b388937f430f40a9e2595c9b12a",
            BaseDigest.ofUtf8(ByteArray(1)).toByteArray().toHex(),
        )
    }

    @Test
    fun utf8BytesAreCheckedForLengthOnly() {
        assertEquals(HhErrorCode.EMPTY_INPUT, error { BaseDigest.ofUtf8(ByteArray(0)) })
        assertEquals(HhErrorCode.INPUT_TOO_LARGE, error { BaseDigest.ofUtf8(ByteArray(1048577)) })
        assertNull(BaseDigest.ofUtf8OrNull(ByteArray(0)))
        assertNull(BaseDigest.ofUtf8OrNull(ByteArray(1048577)))
        assertEquals(BaseDigest.ofText("T"), BaseDigest.ofUtf8OrNull("T".ascii()))
    }

    @Test
    fun keysAreChecked() {
        assertEquals(HhErrorCode.INVALID_KEY, error { SecretKey.of(ByteArray(31) { 1 }) })
        assertEquals(HhErrorCode.INVALID_KEY, error { SecretKey.of(ByteArray(33) { 1 }) })
        assertEquals(HhErrorCode.INVALID_KEY, error { SecretKey.of(ByteArray(32)) })
        assertNull(SecretKey.ofOrNull(ByteArray(0)))
        assertEquals("6a5955cf", SecretKey.of(testKey).checkValue.toHex())
    }

    @Test
    fun aKeyCopiesItsBytesAndIsUnusableOnceClosed() {
        val bytes = testKey.copyOf()
        val key = SecretKey.of(bytes)
        bytes.fill(0) // the caller wipes its own array; the key is not affected
        assertEquals("6a5955cf", key.checkValue.toHex())
        val digest = BaseDigest.fromBytes(addressDigest.hexToBytes())
        val keyed = Fingerprint.keyed(digest, key)
        assertEquals("26ea8171aab23c8e1bf7c23417d33d6dba81d881af70edd2b0675348e080b478", keyed.toByteArray().toHex())
        assertFalse(key.isClosed)
        key.close()
        key.close()
        assertTrue(key.isClosed)
        assertEquals(HhErrorCode.INVALID_KEY, error { Fingerprint.keyed(digest, key) })
        assertNull(Fingerprint.keyedOrNull(digest, key))
        assertEquals("SecretKey(***)", key.toString())
    }

    @Test
    fun theKeyCheckValueOutlivesTheKey() {
        val key = SecretKey.of(testKey)
        val before = key.checkValue
        before.fill(0) // a copy: the key keeps its own
        key.use { assertEquals("6a5955cf", it.checkValue.toHex()) }
        assertTrue(key.isClosed)
        // The value is public, so a closed key still tells which key it was.
        assertEquals("6a5955cf", key.checkValue.toHex())
    }

    @Test
    fun fingerprintsCompareByBytesAndMode() {
        val digest = BaseDigest.fromBytes(addressDigest.hexToBytes())
        val universal = Fingerprint.universal(digest)
        assertEquals(Mode.UNIVERSAL, universal.mode)
        assertEquals("TKSPVH", universal.tag)
        assertEquals(universal, Fingerprint.fromBytes(addressDigest.hexToBytes(), Mode.UNIVERSAL))
        assertEquals(universal.hashCode(), Fingerprint.fromBytes(addressDigest.hexToBytes(), Mode.UNIVERSAL).hashCode())
        assertNotEquals(universal, Fingerprint.fromBytes(addressDigest.hexToBytes(), Mode.KEYED))
        assertEquals(HhErrorCode.INVALID_FINGERPRINT, error { Fingerprint.fromBytes(ByteArray(16), Mode.KEYED) })
        assertNull(Fingerprint.fromBytesOrNull(ByteArray(64), Mode.KEYED))
        // The arrays handed out are copies.
        universal.toByteArray().fill(0)
        assertEquals(addressDigest, universal.toByteArray().toHex())
    }

    @Test
    fun layoutDescribesTheCells() {
        val bytes = ByteArray(32)
        for (i in 0 until 16) {
            bytes[i] = (((i / 2) shl 5) or ((i % 4) shl 3) or 7).toByte()
        }
        val layout = Fingerprint.fromBytes(bytes, Mode.KEYED).layout()
        val figures = listOf(
            Figure.NONE, Figure.NONE, Figure.SQUARE, Figure.CIRCLE,
            Figure.TRIANGLE_UP, Figure.TRIANGLE_RIGHT, Figure.TRIANGLE_DOWN, Figure.TRIANGLE_LEFT,
        )
        assertEquals(Mode.KEYED, layout.mode)
        for (i in 0 until 16) {
            assertEquals(figures[i / 2], layout.cells[i].figure, "cell $i")
            assertEquals(if (figures[i / 2] == Figure.NONE) 0 else i % 4, layout.cells[i].colour, "cell $i")
        }
        assertEquals(listOf(0x7A96C5, 0x890AF0, 0xC10445, 0xD48200), layout.paletteRgb)
        assertEquals(0x808080, layout.frameRgb)
    }

    @Test
    fun renderOptionsAreValidated() {
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { RenderOptions(backgroundRgb = 0x1000000) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { RenderOptions(backgroundRgb = -1) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { RenderOptions(backgroundAlpha = 256) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { RenderOptions(frameAlpha = -1) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { RenderOptions.DEFAULT.measureContrast(-5) })
        assertEquals(ContrastReport(300, 394), RenderOptions.DEFAULT.measureContrast())
        assertEquals(300, RenderOptions.TRANSPARENT.measureContrast(0x121212).figuresX100)
        assertEquals(112, RenderOptions.TRANSPARENT.measureContrast(0x9E9E9E).figuresX100)
    }

    @Test
    fun pixelsComeInBothLayouts() {
        val fp = Fingerprint.fromBytes(addressDigest.hexToBytes(), Mode.KEYED)
        val image = fp.render(64)
        assertEquals(64, image.width)
        assertEquals(64, image.height)
        val rgba = image.toRgba()
        val argb = image.toArgb()
        assertEquals(64 * 64 * 4, rgba.size)
        assertEquals(64 * 64, argb.size)
        for (i in argb.indices) {
            val expected = ((rgba[4 * i + 3].toInt() and 0xFF) shl 24) or ((rgba[4 * i].toInt() and 0xFF) shl 16) or
                ((rgba[4 * i + 1].toInt() and 0xFF) shl 8) or (rgba[4 * i + 2].toInt() and 0xFF)
            assertEquals(expected, argb[i], "pixel $i")
        }
        assertEquals(0, argb[0]) // outside the rounded corner of a keyed picture: transparent
        rgba.fill(0)
        assertContentEquals(image.toRgba(), fp.render(64).toRgba())
    }

    @Test
    fun encodersCheckTheirArguments() {
        val image = Fingerprint.fromBytes(addressDigest.hexToBytes(), Mode.UNIVERSAL).render(32)
        assertEquals(HhErrorCode.INVALID_QUALITY, error { image.encodeJpeg(49) })
        assertEquals(HhErrorCode.INVALID_QUALITY, error { image.encodeJpeg(101) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { image.encodeBmp(0x1000000) })
        assertEquals(HhErrorCode.INVALID_ARGUMENT, error { image.encodeJpeg(92, -1) })
        assertEquals(HhErrorCode.INVALID_IMAGE, error { HhImage.ofRgba(0, 1, ByteArray(0)) })
        assertEquals(HhErrorCode.INVALID_IMAGE, error { HhImage.ofRgba(2, 2, ByteArray(15)) })
        assertEquals(HhErrorCode.INVALID_IMAGE, error { HhImage.ofRgba(4097, 1, ByteArray(4097 * 4)) })
        assertContentEquals(image.encodePng(), HhImage.ofRgba(32, 32, image.toRgba()).encodePng())
    }

    @Test
    fun errorCodesMatchTheSpecification() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 14), HhErrorCode.entries.map { it.code })
        assertTrue(HhErrorCode.entries.all { it.specName == it.name.lowercase() })
    }
}
