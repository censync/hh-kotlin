package io.github.censync.hh

/**
 * HMAC-SHA-256 as specified in RFC 2104 and FIPS 198-1. Internal to the library.
 *
 * The key is processed once into the inner and outer mid-states; the key bytes are not retained. Keys longer
 * than the block size are hashed first and shorter keys are zero-padded, as RFC 2104 specifies. After [finish]
 * the object must not be updated again.
 */
internal class HmacSha256(key: ByteArray) {
    /** The mid-state after absorbing key XOR ipad. */
    val innerState: IntArray

    /** The mid-state after absorbing key XOR opad. */
    val outerState: IntArray

    private val inner: Sha256

    init {
        val keyBlock = ByteArray(Sha256.BLOCK_SIZE)
        if (key.size > Sha256.BLOCK_SIZE) {
            val hasher = Sha256()
            hasher.update(key)
            val hashed = hasher.finish()
            hasher.wipe()
            hashed.copyInto(keyBlock)
            hashed.fill(0)
        } else {
            key.copyInto(keyBlock)
        }
        // The message schedule of these two compressions holds the key block; it is wiped with it.
        val schedule = IntArray(64)
        innerState = absorbPaddedKey(keyBlock, 0x36, schedule)
        outerState = absorbPaddedKey(keyBlock, 0x5C, schedule)
        schedule.fill(0)
        keyBlock.fill(0)
        inner = Sha256.resume(innerState, Sha256.BLOCK_SIZE.toLong())
    }

    /** Absorbs [size] bytes of [data] starting at [offset]. */
    fun update(data: ByteArray, offset: Int = 0, size: Int = data.size - offset) {
        inner.update(data, offset, size)
    }

    /** Completes the MAC and writes the 32-byte tag into [out] at [offset]. */
    fun finish(out: ByteArray, offset: Int = 0) {
        val innerDigest = inner.finish()
        val outer = Sha256.resume(outerState, Sha256.BLOCK_SIZE.toLong())
        outer.update(innerDigest)
        outer.finish(out, offset)
        innerDigest.fill(0)
        inner.wipe()
        outer.wipe()
    }

    /** Completes the MAC and returns the 32-byte tag. */
    fun finish(): ByteArray = ByteArray(Sha256.DIGEST_SIZE).also { finish(it) }

    /** Overwrites the key-dependent mid-states with zeros. */
    fun wipe() {
        innerState.fill(0)
        outerState.fill(0)
        inner.wipe()
    }

    companion object {
        /** Returns HMAC-SHA-256 of [data] under [key]. */
        fun tag(key: ByteArray, data: ByteArray): ByteArray {
            val mac = HmacSha256(key)
            mac.update(data)
            val out = mac.finish()
            mac.wipe()
            return out
        }

        private fun absorbPaddedKey(keyBlock: ByteArray, pad: Int, schedule: IntArray): IntArray {
            val block = ByteArray(Sha256.BLOCK_SIZE) { (keyBlock[it].toInt() xor pad).toByte() }
            val state = Sha256.initialState()
            Sha256.compress(state, block, 0, schedule)
            block.fill(0)
            return state
        }
    }
}
