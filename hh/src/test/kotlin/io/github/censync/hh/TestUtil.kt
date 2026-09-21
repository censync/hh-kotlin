package io.github.censync.hh

private val HEX_DIGITS = "0123456789abcdef".toCharArray()

/** Lowercase hex of the bytes. */
internal fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0x0F])
    }
    return out.toString()
}

/** Bytes of an even-length hex string. */
internal fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { i -> substring(2 * i, 2 * i + 2).toInt(16).toByte() }

/** US-ASCII bytes of the text. */
internal fun String.ascii(): ByteArray = toByteArray(Charsets.US_ASCII)

/** [count] copies of [value]. */
internal fun repeatByte(value: Int, count: Int): ByteArray = ByteArray(count) { value.toByte() }
