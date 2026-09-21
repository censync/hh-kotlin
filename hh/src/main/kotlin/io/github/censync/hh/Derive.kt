package io.github.censync.hh

/** Canonicalisation and derivation: sections 3 and 4 of the specification. */
internal object Derive {
    const val STRETCH_ITERATIONS: Int = 16384
    const val MAX_INPUT_SIZE: Int = 1048576
    const val KIND_BINARY: Int = 0x00
    const val KIND_TEXT: Int = 0x01

    private const val STAGE_DIGEST: Int = 0x01
    private const val STAGE_KEYED: Int = 0x02
    private const val STAGE_KCV: Int = 0x03

    private val DST = "HumanizedHash".encodeToByteArray()
    private val STRETCH_SALT = "HumanizedHash/stretch".encodeToByteArray()

    /** `DST || 00 || stage`. */
    private fun prefix(stage: Int, extra: Int): ByteArray {
        val out = ByteArray(DST.size + 2 + extra)
        DST.copyInto(out)
        out[DST.size] = 0x00
        out[DST.size + 1] = stage.toByte()
        return out
    }

    /** M1 without the data: `DST || 00 || 01 || kind || u32be(length)`. */
    fun m1Header(kind: Int, length: Int): ByteArray {
        val header = prefix(STAGE_DIGEST, 5)
        var p = DST.size + 2
        header[p++] = kind.toByte()
        header[p++] = (length ushr 24).toByte()
        header[p++] = (length ushr 16).toByte()
        header[p++] = (length ushr 8).toByte()
        header[p] = length.toByte()
        return header
    }

    /** The exception for an input of more than [MAX_INPUT_SIZE] bytes. */
    fun inputTooLarge(): HhException =
        HhException(HhErrorCode.INPUT_TOO_LARGE, "the input is longer than $MAX_INPUT_SIZE bytes")

    /** The base digest `s` of an input; checks the length limits. */
    fun baseDigest(kind: Int, data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            throw HhException(HhErrorCode.EMPTY_INPUT, "the input has no bytes")
        }
        if (data.size > MAX_INPUT_SIZE) {
            throw inputTooLarge()
        }
        return stretch(d0(kind, data), STRETCH_ITERATIONS)
    }

    /** `d0 = SHA-256(M1)`. */
    fun d0(kind: Int, data: ByteArray): ByteArray {
        val hasher = Sha256()
        hasher.update(m1Header(kind, data.size))
        hasher.update(data)
        return hasher.finish()
    }

    /**
     * `s = PBKDF2-HMAC-SHA-256(d0, "HumanizedHash/stretch", iterations, 32)`. The library always uses
     * [STRETCH_ITERATIONS]; the benchmark module passes other counts to show what the choice costs.
     */
    fun stretch(d0: ByteArray, iterations: Int): ByteArray =
        Pbkdf2HmacSha256.derive(d0, STRETCH_SALT, iterations, 32)

    /** `M2 = DST || 00 || 02 || s`. */
    fun m2(s: ByteArray): ByteArray {
        val message = prefix(STAGE_KEYED, 32)
        s.copyInto(message, DST.size + 2)
        return message
    }

    /** `HMAC-SHA-256(key, M2)`. */
    fun keyedFingerprint(key: ByteArray, s: ByteArray): ByteArray = HmacSha256.tag(key, m2(s))

    /** The first 4 bytes of `HMAC-SHA-256(key, DST || 00 || 03)`. */
    fun keyCheckValue(key: ByteArray): ByteArray {
        val tag = HmacSha256.tag(key, prefix(STAGE_KCV, 0))
        val out = tag.copyOf(4)
        tag.fill(0)
        return out
    }

    /** Decodes hexadecimal text as section 3 defines it. */
    fun decodeHex(hex: String): ByteArray {
        var start = 0
        if (hex.length >= 2 && hex[0] == '0' && (hex[1] == 'x' || hex[1] == 'X')) {
            start = 2
        }
        val digits = hex.length - start
        if (digits == 0 || digits % 2 != 0) {
            throw HhException(HhErrorCode.INVALID_HEX, "not an even, non-zero number of hexadecimal digits")
        }
        for (i in start until hex.length) {
            if (hexValue(hex[i]) < 0) {
                throw HhException(HhErrorCode.INVALID_HEX, "not a hexadecimal digit at position $i")
            }
        }
        if (digits / 2 > MAX_INPUT_SIZE) {
            throw inputTooLarge()
        }
        return ByteArray(digits / 2) { i ->
            (hexValue(hex[start + 2 * i]) * 16 + hexValue(hex[start + 2 * i + 1])).toByte()
        }
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
