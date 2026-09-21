package io.github.censync.hh

/**
 * SHA-256 as specified in FIPS 180-4. Internal to the library; the public API never exposes it.
 *
 * The hasher is incremental. After [finish] it must be [reset] before it is used again.
 */
internal class Sha256 private constructor(initialState: IntArray, absorbed: Long) {
    private val state = initialState.copyOf()
    private val buffer = ByteArray(BLOCK_SIZE)
    private val schedule = IntArray(64)
    private var length = absorbed
    private var buffered = 0

    /** Creates a hasher in the initial state H(0). */
    constructor() : this(INITIAL_STATE, 0L)

    /** Returns the hasher to the initial state H(0). */
    fun reset() {
        INITIAL_STATE.copyInto(state)
        buffer.fill(0)
        length = 0L
        buffered = 0
    }

    /** Absorbs [size] bytes of [data] starting at [offset]. */
    fun update(data: ByteArray, offset: Int = 0, size: Int = data.size - offset) {
        require(offset >= 0 && size >= 0 && offset + size <= data.size) { "range out of bounds" }
        var pos = offset
        var remaining = size
        length += remaining.toLong()
        if (buffered != 0) {
            val take = minOf(remaining, BLOCK_SIZE - buffered)
            data.copyInto(buffer, buffered, pos, pos + take)
            buffered += take
            pos += take
            remaining -= take
            if (buffered < BLOCK_SIZE) {
                return
            }
            compress(state, buffer, 0, schedule)
            buffered = 0
        }
        while (remaining >= BLOCK_SIZE) {
            compress(state, data, pos, schedule)
            pos += BLOCK_SIZE
            remaining -= BLOCK_SIZE
        }
        if (remaining != 0) {
            data.copyInto(buffer, 0, pos, pos + remaining)
            buffered = remaining
        }
    }

    /** Completes the hash and writes the 32-byte digest into [out] at [offset]. */
    fun finish(out: ByteArray, offset: Int = 0) {
        require(offset >= 0 && offset + DIGEST_SIZE <= out.size) { "output range out of bounds" }
        // FIPS 180-4 section 5.1.1: a one bit, zeros, and the bit length as a 64-bit big-endian integer.
        val bitLength = length * 8L
        buffer[buffered++] = 0x80.toByte()
        if (buffered > BLOCK_SIZE - 8) {
            buffer.fill(0, buffered, BLOCK_SIZE)
            compress(state, buffer, 0, schedule)
            buffered = 0
        }
        buffer.fill(0, buffered, BLOCK_SIZE - 8)
        for (i in 0 until 8) {
            buffer[BLOCK_SIZE - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }
        compress(state, buffer, 0, schedule)
        store(state, out, offset)
        buffered = 0
    }

    /** Completes the hash and returns the 32-byte digest. */
    fun finish(): ByteArray = ByteArray(DIGEST_SIZE).also { finish(it) }

    /** Overwrites the state and the buffered input with zeros. */
    fun wipe() {
        state.fill(0)
        buffer.fill(0)
        schedule.fill(0)
    }

    companion object {
        const val BLOCK_SIZE: Int = 64
        const val DIGEST_SIZE: Int = 32

        /** The initial hash value H(0) of FIPS 180-4 section 5.3.3. */
        private val INITIAL_STATE = words(
            0x6A09E667, 0xBB67AE85, 0x3C6EF372, 0xA54FF53A,
            0x510E527F, 0x9B05688C, 0x1F83D9AB, 0x5BE0CD19,
        )

        /** The round constants K of FIPS 180-4 section 4.2.2. */
        private val K = words(
            0x428A2F98, 0x71374491, 0xB5C0FBCF, 0xE9B5DBA5, 0x3956C25B, 0x59F111F1, 0x923F82A4,
            0xAB1C5ED5, 0xD807AA98, 0x12835B01, 0x243185BE, 0x550C7DC3, 0x72BE5D74, 0x80DEB1FE,
            0x9BDC06A7, 0xC19BF174, 0xE49B69C1, 0xEFBE4786, 0x0FC19DC6, 0x240CA1CC, 0x2DE92C6F,
            0x4A7484AA, 0x5CB0A9DC, 0x76F988DA, 0x983E5152, 0xA831C66D, 0xB00327C8, 0xBF597FC7,
            0xC6E00BF3, 0xD5A79147, 0x06CA6351, 0x14292967, 0x27B70A85, 0x2E1B2138, 0x4D2C6DFC,
            0x53380D13, 0x650A7354, 0x766A0ABB, 0x81C2C92E, 0x92722C85, 0xA2BFE8A1, 0xA81A664B,
            0xC24B8B70, 0xC76C51A3, 0xD192E819, 0xD6990624, 0xF40E3585, 0x106AA070, 0x19A4C116,
            0x1E376C08, 0x2748774C, 0x34B0BCB5, 0x391C0CB3, 0x4ED8AA4A, 0x5B9CCA4F, 0x682E6FF3,
            0x748F82EE, 0x78A5636F, 0x84C87814, 0x8CC70208, 0x90BEFFFA, 0xA4506CEB, 0xBEF9A3F7,
            0xC67178F2,
        )

        private fun words(vararg values: Long): IntArray = IntArray(values.size) { values[it].toInt() }

        /** Returns a copy of H(0). */
        fun initialState(): IntArray = INITIAL_STATE.copyOf()

        /** Resumes hashing from [midState] after [absorbed] bytes (a multiple of [BLOCK_SIZE]). */
        fun resume(midState: IntArray, absorbed: Long): Sha256 = Sha256(midState, absorbed)

        /** Returns the SHA-256 digest of [data]. */
        fun digest(data: ByteArray): ByteArray {
            val hasher = Sha256()
            hasher.update(data)
            return hasher.finish()
        }

        /**
         * Applies the compression function to the 64-byte block of [block] at [offset], updating
         * [state] in place. [w] is a caller-provided 64-word scratch array for the message schedule.
         */
        fun compress(state: IntArray, block: ByteArray, offset: Int, w: IntArray) {
            for (i in 0 until 16) {
                val p = offset + 4 * i
                w[i] = (block[p].toInt() and 0xFF shl 24) or
                    (block[p + 1].toInt() and 0xFF shl 16) or
                    (block[p + 2].toInt() and 0xFF shl 8) or
                    (block[p + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val x = w[i - 15]
                val y = w[i - 2]
                val s0 = x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3)
                val s1 = y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = state[0]
            var b = state[1]
            var c = state[2]
            var d = state[3]
            var e = state[4]
            var f = state[5]
            var g = state[6]
            var h = state[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = h + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }
            state[0] += a
            state[1] += b
            state[2] += c
            state[3] += d
            state[4] += e
            state[5] += f
            state[6] += g
            state[7] += h
        }

        /** Writes [state] as the big-endian 32-byte digest into [out] at [offset]. */
        fun store(state: IntArray, out: ByteArray, offset: Int) {
            for (i in 0 until 8) {
                val v = state[i]
                val p = offset + 4 * i
                out[p] = (v ushr 24).toByte()
                out[p + 1] = (v ushr 16).toByte()
                out[p + 2] = (v ushr 8).toByte()
                out[p + 3] = v.toByte()
            }
        }
    }
}
