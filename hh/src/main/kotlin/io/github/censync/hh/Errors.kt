package io.github.censync.hh

/**
 * The error conditions of the hh specification (section 14). [code] is the numeric value the C ABI of hh-cpp
 * uses and [specName] the spelling of the specification and of the golden vectors.
 */
public enum class HhErrorCode(public val code: Int, public val specName: String) {
    /** The input has no bytes. */
    EMPTY_INPUT(1, "empty_input"),

    /** The input is longer than 1 048 576 bytes. */
    INPUT_TOO_LARGE(2, "input_too_large"),

    /** The string is not an even, non-zero number of hexadecimal digits with an optional `0x`. */
    INVALID_HEX(3, "invalid_hex"),

    /** The key is not 32 bytes, is all zero, or was closed. */
    INVALID_KEY(4, "invalid_key"),

    /** The base digest is not 32 bytes. */
    INVALID_DIGEST(5, "invalid_digest"),

    /** The fingerprint is not 32 bytes. */
    INVALID_FINGERPRINT(6, "invalid_fingerprint"),

    /** The image size is outside 16..1024 or leaves no room for the cells. */
    INVALID_SIZE(7, "invalid_size"),

    /** The frame style is not allowed for the shape or the mode. */
    INVALID_FRAME(8, "invalid_frame"),

    /** The opaque background is too close to a palette colour. */
    LOW_CONTRAST(9, "low_contrast"),

    /** The JPEG quality is outside 50..100. */
    INVALID_QUALITY(10, "invalid_quality"),

    /** The image dimensions or its buffer length are invalid. */
    INVALID_IMAGE(11, "invalid_image"),

    /** A text input holds an ill-formed sequence, or a colour or alpha value is out of range. */
    INVALID_ARGUMENT(14, "invalid_argument"),
}

/**
 * Thrown for invalid arguments. It is an [IllegalArgumentException], as Kotlin convention expects. The functions
 * that take outside data (inputs, keys, stored bytes, sizes, pixels) have `...OrNull` companions.
 *
 * ```
 * try {
 *     BaseDigest.ofHex(pasted)
 * } catch (e: HhException) {
 *     if (e.error == HhErrorCode.INVALID_HEX) { ... }
 * }
 * ```
 *
 * @property error the error of the specification: [HhErrorCode.code] is its number and
 * [HhErrorCode.specName] its name.
 */
public class HhException(public val error: HhErrorCode, message: String) : IllegalArgumentException(message)
