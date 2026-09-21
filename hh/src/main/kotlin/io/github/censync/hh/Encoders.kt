package io.github.censync.hh

/** A growable byte buffer; the library uses nothing beyond the Kotlin standard library. */
internal class ByteSink(capacity: Int = 1024) {
    private var buffer = ByteArray(capacity)
    var size: Int = 0
        private set

    fun put(value: Int) {
        if (size == buffer.size) {
            buffer = buffer.copyOf(buffer.size * 2)
        }
        buffer[size++] = value.toByte()
    }

    fun put(bytes: ByteArray) {
        for (b in bytes) {
            put(b.toInt())
        }
    }

    fun putBe16(value: Int) {
        put(value ushr 8)
        put(value)
    }

    fun putBe32(value: Int) {
        put(value ushr 24)
        put(value ushr 16)
        put(value ushr 8)
        put(value)
    }

    fun putLe16(value: Int) {
        put(value)
        put(value ushr 8)
    }

    fun putLe32(value: Int) {
        putLe16(value and 0xFFFF)
        putLe16(value ushr 16)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

/** CRC-32 (ISO 3309, as PNG uses it) and Adler-32 (RFC 1950). */
internal object Checksums {
    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) {
            c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
        }
        c
    }

    fun crc32(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = -1
        for (i in offset until offset + length) {
            crc = CRC_TABLE[(crc xor data[i].toInt()) and 0xFF] xor (crc ushr 8)
        }
        return crc.inv()
    }

    fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        var pos = 0
        // 5552 is the largest block for which the sums cannot overflow 32 bits.
        while (pos < data.size) {
            val end = minOf(data.size, pos + 5552)
            while (pos < end) {
                a += data[pos++].toInt() and 0xFF
                b += a
            }
            a = (a.toLong() and 0xFFFFFFFFL).rem(65521L).toInt()
            b = (b.toLong() and 0xFFFFFFFFL).rem(65521L).toInt()
        }
        return (b shl 16) or a
    }
}

/** PNG: section 11 of the specification. */
internal object PngEncoder {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227,
        258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097,
        6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )

    fun encode(width: Int, height: Int, rgba: ByteArray): ByteArray {
        var opaque = true
        var p = 3
        while (p < rgba.size && opaque) {
            opaque = rgba[p] == 0xFF.toByte()
            p += 4
        }
        val bpp = if (opaque) 3 else 4
        val stride = 1 + width * bpp
        val raw = ByteArray(stride * height)
        for (y in 0 until height) {
            var out = y * stride + 1 // the filter byte stays 0
            var src = y * width * 4
            for (x in 0 until width) {
                raw[out++] = rgba[src]
                raw[out++] = rgba[src + 1]
                raw[out++] = rgba[src + 2]
                if (!opaque) {
                    raw[out++] = rgba[src + 3]
                }
                src += 4
            }
        }

        val zlib = ByteSink(raw.size / 8 + 64)
        zlib.put(0x78)
        zlib.put(0x01)
        deflateFixed(raw, bpp, stride, zlib)
        zlib.putBe32(Checksums.adler32(raw))

        val png = ByteSink(zlib.size + 64)
        png.put(SIGNATURE)
        val ihdr = ByteSink(13)
        ihdr.putBe32(width)
        ihdr.putBe32(height)
        ihdr.put(8)
        ihdr.put(if (opaque) 2 else 6)
        ihdr.put(0)
        ihdr.put(0)
        ihdr.put(0)
        putChunk(png, "IHDR", ihdr.toByteArray())
        putChunk(png, "sRGB", byteArrayOf(0))
        putChunk(png, "IDAT", zlib.toByteArray())
        putChunk(png, "IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun putChunk(out: ByteSink, type: String, data: ByteArray) {
        val body = type.encodeToByteArray() + data
        out.putBe32(data.size)
        out.put(body)
        out.putBe32(Checksums.crc32(body))
    }

    /** One deflate block with fixed Huffman codes and greedy matches at the distances bpp and stride. */
    private fun deflateFixed(raw: ByteArray, bpp: Int, stride: Int, out: ByteSink) {
        val w = BitWriter(out)
        w.put(1, 1) // BFINAL
        w.put(1, 2) // BTYPE = 01
        val n = raw.size
        var i = 0
        while (i < n) {
            var best = 0
            var dist = 0
            val limit = minOf(258, n - i)
            for (pass in 0 until 2) {
                val d = if (pass == 0) bpp else stride
                if (i < d) {
                    continue
                }
                var len = 0
                while (len < limit && raw[i + len] == raw[i - d + len]) {
                    len++
                }
                if (len > best) {
                    best = len
                    dist = d
                }
            }
            if (best >= 3) {
                putMatch(w, best, dist)
                i += best
            } else {
                putSymbol(w, raw[i].toInt() and 0xFF)
                i++
            }
        }
        putSymbol(w, 256)
        w.flush()
    }

    /** The fixed literal/length code of RFC 1951 section 3.2.6. */
    private fun putSymbol(w: BitWriter, symbol: Int) {
        when {
            symbol < 144 -> w.putCode(0x30 + symbol, 8)
            symbol < 256 -> w.putCode(0x190 + (symbol - 144), 9)
            symbol < 280 -> w.putCode(symbol - 256, 7)
            else -> w.putCode(0xC0 + (symbol - 280), 8)
        }
    }

    private fun putMatch(w: BitWriter, length: Int, dist: Int) {
        var li = 28
        while (LENGTH_BASE[li] > length) {
            li--
        }
        putSymbol(w, 257 + li)
        w.put(length - LENGTH_BASE[li], LENGTH_EXTRA[li])
        var di = 29
        while (DIST_BASE[di] > dist) {
            di--
        }
        w.putCode(di, 5)
        w.put(dist - DIST_BASE[di], DIST_EXTRA[di])
    }

    /** Deflate packs bits from the least significant bit of each byte. */
    private class BitWriter(private val out: ByteSink) {
        private var acc = 0
        private var used = 0

        fun put(bits: Int, count: Int) {
            acc = acc or (bits shl used)
            used += count
            while (used >= 8) {
                out.put(acc and 0xFF)
                acc = acc ushr 8
                used -= 8
            }
        }

        /** Huffman codes are sent most significant bit first. */
        fun putCode(code: Int, count: Int) {
            var reversed = 0
            for (i in 0 until count) {
                reversed = (reversed shl 1) or ((code ushr i) and 1)
            }
            put(reversed, count)
        }

        fun flush() {
            if (used != 0) {
                out.put(acc and 0xFF)
                acc = 0
                used = 0
            }
        }
    }
}

/** BMP: section 12 of the specification. */
internal object BmpEncoder {
    fun encode(width: Int, height: Int, rgba: ByteArray, matteRgb: Int): ByteArray {
        val row = 4 * ((3 * width + 3) / 4)
        val dataSize = row * height
        val out = ByteSink(54 + dataSize)
        out.put('B'.code)
        out.put('M'.code)
        out.putLe32(54 + dataSize)
        out.putLe16(0)
        out.putLe16(0)
        out.putLe32(54)
        out.putLe32(40)
        out.putLe32(width)
        out.putLe32(height)
        out.putLe16(1)
        out.putLe16(24)
        out.putLe32(0)
        out.putLe32(dataSize)
        out.putLe32(2835)
        out.putLe32(2835)
        out.putLe32(0)
        out.putLe32(0)
        val mr = (matteRgb ushr 16) and 0xFF
        val mg = (matteRgb ushr 8) and 0xFF
        val mb = matteRgb and 0xFF
        for (y in height - 1 downTo 0) {
            var p = y * width * 4
            for (x in 0 until width) {
                val a = rgba[p + 3].toInt() and 0xFF
                out.put(flatten(rgba[p + 2].toInt() and 0xFF, a, mb))
                out.put(flatten(rgba[p + 1].toInt() and 0xFF, a, mg))
                out.put(flatten(rgba[p].toInt() and 0xFF, a, mr))
                p += 4
            }
            for (pad in 3 * width until row) {
                out.put(0)
            }
        }
        return out.toByteArray()
    }
}
