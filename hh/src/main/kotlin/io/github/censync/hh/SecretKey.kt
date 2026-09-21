package io.github.censync.hh

/**
 * The 32-byte secret of keyed mode. [close] wipes the bytes; use it with `use { }` or close it when the wallet
 * locks. The key must be uniformly random or the output of a key derivation function: there is no passphrase
 * form.
 *
 * Hosts that keep secrets out of the JVM compute the keyed fingerprint natively and pass the result to
 * [Fingerprint.fromBytes] instead of creating a [SecretKey].
 */
public class SecretKey private constructor(private val bytes: ByteArray) : AutoCloseable {
    @Volatile
    private var closed = false

    // Public, so it is computed once while the key is there and outlives the key bytes.
    private val kcv: ByteArray = Derive.keyCheckValue(bytes)

    /**
     * The key check value: 4 bytes a host stores beside its cached data to notice that the key, and with it
     * every keyed picture, changed. It is public and reveals nothing useful about the key, so it stays
     * readable after [close]; every other use of a closed key throws. Each read returns a copy.
     */
    public val checkValue: ByteArray
        get() = kcv.copyOf()

    /** Whether [close] was called: a closed key holds zeros and computes no fingerprints. */
    public val isClosed: Boolean
        get() = closed

    /**
     * Runs [block] with the key bytes. A key that is closed meanwhile is wiped to zeros, and a result computed
     * under zeros must never be handed out, so the key is checked again afterwards.
     */
    @JvmSynthetic
    internal fun <T> withBytes(block: (ByteArray) -> T): T {
        ensureOpen()
        val result = block(bytes)
        ensureOpen()
        return result
    }

    private fun ensureOpen() {
        if (closed) {
            throw HhException(HhErrorCode.INVALID_KEY, "the key was closed")
        }
    }

    /**
     * Wipes the key. Closing twice is harmless; [Fingerprint.keyed] with a closed key throws
     * [HhErrorCode.INVALID_KEY], while [checkValue] and [isClosed] stay readable.
     */
    override fun close() {
        closed = true
        bytes.fill(0)
    }

    override fun toString(): String = "SecretKey(***)"

    public companion object {
        /** The size of a key in bytes. */
        public const val SIZE: Int = 32

        /**
         * Accepts exactly 32 bytes that are not all zero. The bytes are copied; wipe your own array.
         *
         * @throws HhException with [HhErrorCode.INVALID_KEY].
         */
        @JvmStatic
        public fun of(bytes32: ByteArray): SecretKey {
            if (bytes32.size != SIZE) {
                throw HhException(HhErrorCode.INVALID_KEY, "a key has $SIZE bytes")
            }
            var any = 0
            for (b in bytes32) {
                any = any or b.toInt()
            }
            if (any == 0) {
                throw HhException(HhErrorCode.INVALID_KEY, "the all-zero key is not a key")
            }
            return SecretKey(bytes32.copyOf())
        }

        /** [of], or null instead of an exception. */
        @JvmStatic
        public fun ofOrNull(bytes32: ByteArray): SecretKey? = orNull { of(bytes32) }
    }
}
