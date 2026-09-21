package io.github.censync.hh

/**
 * The base digest of an input: its stretched, public 32-byte value (section 4 of the specification).
 *
 * It is the only slow step, about 16 000 HMAC calls, so hosts compute it off the UI thread and cache
 * [toByteArray] per address; both modes and any key derive their fingerprint from it cheaply. It is public
 * and needs no protection.
 */
public class BaseDigest private constructor(private val bytes: ByteArray) {
    /** A copy of the 32 bytes, for caching; restore with [fromBytes]. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    @JvmSynthetic
    internal fun bytesUnsafe(): ByteArray = bytes

    override fun equals(other: Any?): Boolean = other is BaseDigest && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "BaseDigest(${Hex.encode(bytes)})"

    public companion object {
        /** The size of a base digest in bytes. */
        public const val SIZE: Int = 32

        /**
         * Binary input: the bytes of an address, a public key or a hash, 1 to 1 048 576 of them.
         *
         * @throws HhException with [HhErrorCode.EMPTY_INPUT] or [HhErrorCode.INPUT_TOO_LARGE].
         */
        @JvmStatic
        public fun of(data: ByteArray): BaseDigest = BaseDigest(Derive.baseDigest(Derive.KIND_BINARY, data))

        /**
         * Binary input given as hexadecimal text: an optional `0x` or `0X`, then an even, non-zero number of
         * hexadecimal digits of either case. Every spelling of one address gives the same digest.
         *
         * @throws HhException with [HhErrorCode.INVALID_HEX] or [HhErrorCode.INPUT_TOO_LARGE].
         */
        @JvmStatic
        public fun ofHex(hex: String): BaseDigest = of(Derive.decodeHex(hex))

        /**
         * Text input: the UTF-8 encoding of [text], verbatim. A text input and a binary input with the same
         * bytes give different digests.
         *
         * @throws HhException with [HhErrorCode.EMPTY_INPUT], [HhErrorCode.INPUT_TOO_LARGE], or
         * [HhErrorCode.INVALID_ARGUMENT] if [text] holds an unpaired surrogate.
         */
        @JvmStatic
        public fun ofText(text: String): BaseDigest {
            // Every UTF-16 unit gives at least one byte, so an overlong text is refused before it is measured.
            if (text.length > Derive.MAX_INPUT_SIZE) {
                throw Derive.inputTooLarge()
            }
            // The length check comes first; for it an unpaired surrogate counts as three bytes.
            var bytes = 0
            var unpaired = -1
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val paired = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
                if (c.isSurrogate() && !paired && unpaired < 0) {
                    unpaired = i
                }
                bytes += when {
                    paired -> 4
                    c.code < 0x80 -> 1
                    c.code < 0x800 -> 2
                    else -> 3
                }
                i += if (paired) 2 else 1
            }
            if (bytes > Derive.MAX_INPUT_SIZE) {
                throw Derive.inputTooLarge()
            }
            if (unpaired >= 0) {
                throw HhException(
                    HhErrorCode.INVALID_ARGUMENT,
                    "the text holds an unpaired surrogate at position $unpaired",
                )
            }
            return BaseDigest(Derive.baseDigest(Derive.KIND_TEXT, text.encodeToByteArray()))
        }

        /**
         * Text input that is UTF-8 bytes already, 1 to 1 048 576 of them: what a file, a socket or native code
         * hands over. The bytes are taken verbatim and are not validated, as section 3 of the specification
         * defines for an interface that takes bytes; for well-formed UTF-8 the digest is the one [ofText] gives
         * for the decoded text. Decoding such bytes into a `String` first would replace every ill-formed
         * sequence with U+FFFD and give the digest of another text.
         *
         * @throws HhException with [HhErrorCode.EMPTY_INPUT] or [HhErrorCode.INPUT_TOO_LARGE].
         */
        @JvmStatic
        public fun ofUtf8(utf8: ByteArray): BaseDigest = BaseDigest(Derive.baseDigest(Derive.KIND_TEXT, utf8))

        /**
         * Restores a cached digest from its 32 bytes.
         *
         * @throws HhException with [HhErrorCode.INVALID_DIGEST] unless [bytes32] has 32 bytes.
         */
        @JvmStatic
        public fun fromBytes(bytes32: ByteArray): BaseDigest {
            if (bytes32.size != SIZE) {
                throw HhException(HhErrorCode.INVALID_DIGEST, "a base digest has $SIZE bytes")
            }
            return BaseDigest(bytes32.copyOf())
        }

        /** [of], or null instead of an exception. */
        @JvmStatic
        public fun ofOrNull(data: ByteArray): BaseDigest? = orNull { of(data) }

        /** [ofHex], or null instead of an exception. */
        @JvmStatic
        public fun ofHexOrNull(hex: String): BaseDigest? = orNull { ofHex(hex) }

        /** [ofText], or null instead of an exception. */
        @JvmStatic
        public fun ofTextOrNull(text: String): BaseDigest? = orNull { ofText(text) }

        /** [ofUtf8], or null instead of an exception. */
        @JvmStatic
        public fun ofUtf8OrNull(utf8: ByteArray): BaseDigest? = orNull { ofUtf8(utf8) }

        /** [fromBytes], or null instead of an exception. */
        @JvmStatic
        public fun fromBytesOrNull(bytes32: ByteArray): BaseDigest? = orNull { fromBytes(bytes32) }
    }
}

/** Runs [block] and maps an [HhException] to null. */
internal inline fun <T> orNull(block: () -> T): T? = try {
    block()
} catch (e: HhException) {
    null
}

/** Lowercase hexadecimal, for `toString` only. */
internal object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out.append(DIGITS[v ushr 4]).append(DIGITS[v and 0x0F])
        }
        return out.toString()
    }
}
