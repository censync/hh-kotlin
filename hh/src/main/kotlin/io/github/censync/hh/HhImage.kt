package io.github.censync.hh

/**
 * Pixels, not pictures: [width] x [height] RGBA pixels with straight (non-premultiplied) alpha, row-major from
 * the top left. Turning them into a platform image belongs to the host:
 *
 * ```
 * Bitmap.createBitmap(image.toArgb(), image.width, image.height, Bitmap.Config.ARGB_8888)
 * ```
 *
 * The encoders are deterministic: the same image gives the same bytes in every implementation of hh.
 */
public class HhImage private constructor(
    public val width: Int,
    public val height: Int,
    private val rgba: ByteArray,
) {
    /** A copy of the pixels, 4 bytes each in the order R, G, B, A. */
    public fun toRgba(): ByteArray = rgba.copyOf()

    /** The pixels as `A << 24 | R << 16 | G << 8 | B` with straight alpha, as Android's `Bitmap` takes them. */
    public fun toArgb(): IntArray = IntArray(width * height) { i ->
        val p = 4 * i
        ((rgba[p + 3].toInt() and 0xFF) shl 24) or ((rgba[p].toInt() and 0xFF) shl 16) or
            ((rgba[p + 1].toInt() and 0xFF) shl 8) or (rgba[p + 2].toInt() and 0xFF)
    }

    /** 8-bit truecolour PNG; with alpha only if some pixel is not opaque. */
    public fun encodePng(): ByteArray = PngEncoder.encode(width, height, rgba)

    /**
     * 24-bit BMP; transparent pixels are flattened over [matteRgb] (`0xRRGGBB`).
     *
     * @throws HhException with [HhErrorCode.INVALID_ARGUMENT] unless [matteRgb] is 0..0xFFFFFF.
     */
    @JvmOverloads
    public fun encodeBmp(matteRgb: Int = 0xFFFFFF): ByteArray {
        checkColour(matteRgb)
        return BmpEncoder.encode(width, height, rgba, matteRgb)
    }

    /**
     * Baseline JFIF, 4:4:4; transparent pixels are flattened over [matteRgb]. Offered for compatibility: JPEG
     * rings on flat colour edges, prefer PNG.
     *
     * @throws HhException with [HhErrorCode.INVALID_QUALITY] unless [quality] is 50..100, or with
     * [HhErrorCode.INVALID_ARGUMENT] unless [matteRgb] is 0..0xFFFFFF.
     */
    @JvmOverloads
    public fun encodeJpeg(quality: Int = DEFAULT_JPEG_QUALITY, matteRgb: Int = 0xFFFFFF): ByteArray {
        if (quality < 50 || quality > 100) {
            throw HhException(HhErrorCode.INVALID_QUALITY, "the JPEG quality must be 50..100")
        }
        checkColour(matteRgb)
        return JpegEncoder.encode(width, height, rgba, quality, matteRgb)
    }

    private fun checkColour(rgb: Int) {
        if (rgb !in 0..0xFFFFFF) {
            throw HhException(HhErrorCode.INVALID_ARGUMENT, "a colour is 0..0xFFFFFF")
        }
    }

    public companion object {
        /** The JPEG quality used when none is given. */
        public const val DEFAULT_JPEG_QUALITY: Int = 92

        /** The largest width and height the encoders accept. */
        public const val MAX_DIMENSION: Int = 4096

        /**
         * Wraps pixels that did not come from [Fingerprint.render], to use the encoders on them.
         *
         * @throws HhException with [HhErrorCode.INVALID_IMAGE] unless the dimensions are 1..4096 and [rgba] has
         * `width * height * 4` bytes.
         */
        @JvmStatic
        public fun ofRgba(width: Int, height: Int, rgba: ByteArray): HhImage {
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION || rgba.size != width * height * 4) {
                throw HhException(
                    HhErrorCode.INVALID_IMAGE,
                    "the dimensions are 1..$MAX_DIMENSION, the buffer w * h * 4",
                )
            }
            return HhImage(width, height, rgba.copyOf())
        }

        /** [ofRgba], or null instead of an exception. */
        @JvmStatic
        public fun ofRgbaOrNull(width: Int, height: Int, rgba: ByteArray): HhImage? =
            orNull { ofRgba(width, height, rgba) }

        /** Takes ownership of pixels the library itself produced. */
        @JvmSynthetic
        internal fun wrap(width: Int, height: Int, rgba: ByteArray): HhImage = HhImage(width, height, rgba)
    }
}

/** One channel with alpha [a] flattened over a matte channel: section 10 of the specification. */
internal fun flatten(value: Int, a: Int, matte: Int): Int = (a * value + (255 - a) * matte + 127) / 255
