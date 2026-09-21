package io.github.censync.hh

/**
 * 32 bytes and the mode they were derived in: everything a picture depends on.
 *
 * ```
 * val digest = BaseDigest.ofHex("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")   // slow: cache it
 * val image = Fingerprint.universal(digest).render(128)
 * val bitmap = Bitmap.createBitmap(image.toArgb(), image.width, image.height, Bitmap.Config.ARGB_8888)
 * ```
 */
public class Fingerprint private constructor(private val bytes: ByteArray, public val mode: Mode) {
    /**
     * The 6-character Crockford Base32 tag, for example `K7QM2X`; hosts display it as `K7Q-M2X`. Text allows
     * a certain check where a picture does not.
     */
    public val tag: String
        get() = Features.tag(bytes)

    /** A copy of the 32 bytes. */
    public fun toByteArray(): ByteArray = bytes.copyOf()

    /** The cells, the palette and the mode, for hosts that draw vectors themselves. */
    public fun layout(): Layout = Layout(mode, Features.cells(bytes), Palette.COLOURS.toList(), Palette.FRAME)

    /**
     * Renders [sizePx] x [sizePx] pixels, 16..1024.
     *
     * @throws HhException with [HhErrorCode.INVALID_SIZE], [HhErrorCode.INVALID_FRAME] or
     * [HhErrorCode.LOW_CONTRAST], as section 6 of the specification defines.
     */
    @JvmOverloads
    public fun render(sizePx: Int, options: RenderOptions = RenderOptions.DEFAULT): HhImage =
        HhImage.wrap(sizePx, sizePx, Raster.render(bytes, mode, sizePx, options))

    /** [render], or null instead of an exception. */
    @JvmOverloads
    public fun renderOrNull(sizePx: Int, options: RenderOptions = RenderOptions.DEFAULT): HhImage? =
        orNull { render(sizePx, options) }

    override fun equals(other: Any?): Boolean =
        other is Fingerprint && mode == other.mode && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mode.hashCode()

    override fun toString(): String = "Fingerprint($mode, $tag)"

    public companion object {
        /** The size of a fingerprint in bytes. */
        public const val SIZE: Int = 32

        /** The universal fingerprint is the base digest itself. */
        @JvmStatic
        public fun universal(digest: BaseDigest): Fingerprint = Fingerprint(digest.toByteArray(), Mode.UNIVERSAL)

        /**
         * The keyed fingerprint: one HMAC of the base digest under the key.
         *
         * @throws HhException with [HhErrorCode.INVALID_KEY] if [key] was closed.
         */
        @JvmStatic
        public fun keyed(digest: BaseDigest, key: SecretKey): Fingerprint =
            Fingerprint(key.withBytes { Derive.keyedFingerprint(it, digest.bytesUnsafe()) }, Mode.KEYED)

        /** [keyed], or null instead of an exception. */
        @JvmStatic
        public fun keyedOrNull(digest: BaseDigest, key: SecretKey): Fingerprint? = orNull { keyed(digest, key) }

        /**
         * For hosts that compute the keyed HMAC elsewhere, for example natively or inside a secure element:
         * takes the 32 fingerprint bytes and the mode they belong to.
         *
         * @throws HhException with [HhErrorCode.INVALID_FINGERPRINT] unless [bytes32] has 32 bytes.
         */
        @JvmStatic
        public fun fromBytes(bytes32: ByteArray, mode: Mode): Fingerprint {
            if (bytes32.size != SIZE) {
                throw HhException(HhErrorCode.INVALID_FINGERPRINT, "a fingerprint has $SIZE bytes")
            }
            return Fingerprint(bytes32.copyOf(), mode)
        }

        /** [fromBytes], or null instead of an exception. */
        @JvmStatic
        public fun fromBytesOrNull(bytes32: ByteArray, mode: Mode): Fingerprint? = orNull { fromBytes(bytes32, mode) }
    }
}
