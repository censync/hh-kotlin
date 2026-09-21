package io.github.censync.hh

/**
 * PBKDF2 with HMAC-SHA-256 as the PRF, as specified in RFC 8018 section 5.2. Internal to the library.
 *
 * With an output of 32 bytes or less, every iteration after the first costs exactly two SHA-256 compressions:
 * the HMAC key is absorbed once into the inner and outer mid-states, and each `U_j` is a single padded block.
 */
internal object Pbkdf2HmacSha256 {
    /** Derives [length] bytes from [password] and [salt] with [iterations] rounds (at least 1). */
    fun derive(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations >= 1) { "iterations must be at least 1" }
        require(length >= 0) { "length must not be negative" }
        val prf = HmacSha256(password)
        val out = ByteArray(length)

        // Every U_j for j >= 2 is the HMAC of a 32-byte message, so the inner and the outer hash each absorb one
        // more block: 32 bytes of data, the 0x80 terminator and the bit length of 64 + 32 bytes.
        val block = ByteArray(Sha256.BLOCK_SIZE)
        block[Sha256.DIGEST_SIZE] = 0x80.toByte()
        val bitLength = (Sha256.BLOCK_SIZE + Sha256.DIGEST_SIZE) * 8
        block[Sha256.BLOCK_SIZE - 2] = (bitLength ushr 8).toByte()
        block[Sha256.BLOCK_SIZE - 1] = bitLength.toByte()

        val t = ByteArray(Sha256.DIGEST_SIZE)
        val state = IntArray(8)
        val w = IntArray(64)
        val innerState = prf.innerState
        val outerState = prf.outerState
        var blockIndex = 1
        var offset = 0
        while (offset < length) {
            // U_1 = PRF(P, S || INT(i)).
            val first = HmacSha256(password)
            first.update(salt)
            first.update(
                byteArrayOf(
                    (blockIndex ushr 24).toByte(),
                    (blockIndex ushr 16).toByte(),
                    (blockIndex ushr 8).toByte(),
                    blockIndex.toByte(),
                ),
            )
            first.finish(block, 0)
            first.wipe()
            block.copyInto(t, 0, 0, Sha256.DIGEST_SIZE)

            // U_j = PRF(P, U_{j-1}); T = U_1 ^ U_2 ^ ... ^ U_c.
            for (j in 1 until iterations) {
                innerState.copyInto(state)
                Sha256.compress(state, block, 0, w)
                Sha256.store(state, block, 0)
                outerState.copyInto(state)
                Sha256.compress(state, block, 0, w)
                Sha256.store(state, block, 0)
                for (k in 0 until Sha256.DIGEST_SIZE) {
                    t[k] = (t[k].toInt() xor block[k].toInt()).toByte()
                }
            }

            val take = minOf(Sha256.DIGEST_SIZE, length - offset)
            t.copyInto(out, offset, 0, take)
            offset += take
            blockIndex++
        }
        prf.wipe()
        state.fill(0)
        w.fill(0)
        block.fill(0)
        t.fill(0)
        return out
    }
}
