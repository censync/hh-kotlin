package io.github.censync.hh

/** JPEG: section 13 and appendix B of the specification. */
internal object JpegEncoder {
    val ZIGZAG: IntArray = intArrayOf(
        0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5, 12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14,
        21, 28, 35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51, 58, 59, 52, 45, 38, 31, 39, 46, 53,
        60, 61, 54, 47, 55, 62, 63,
    )

    val LUMINANCE_QUANTISER: IntArray = intArrayOf(
        16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40, 57, 69, 56, 14, 17, 22,
        29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103,
        121, 120, 101, 72, 92, 95, 98, 112, 100, 103, 99,
    )

    val CHROMINANCE_QUANTISER: IntArray = intArrayOf(
        17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99, 99, 99, 99, 47, 66, 99,
        99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
    )

    /** A Huffman table: the number of codes of each length 1..16, and the symbols in code order. */
    class HuffmanSpec(val counts: IntArray, val symbols: IntArray)

    private val DC_SYMBOLS = IntArray(12) { it }

    val DC_LUMINANCE: HuffmanSpec =
        HuffmanSpec(intArrayOf(0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0), DC_SYMBOLS)
    val DC_CHROMINANCE: HuffmanSpec =
        HuffmanSpec(intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0), DC_SYMBOLS)

    val AC_LUMINANCE: HuffmanSpec = HuffmanSpec(
        intArrayOf(0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7D),
        intArrayOf(
            0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71,
            0x14, 0x32, 0x81, 0x91, 0xA1, 0x08, 0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52, 0xD1, 0xF0, 0x24, 0x33, 0x62, 0x72,
            0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x34, 0x35, 0x36, 0x37,
            0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
            0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x83,
            0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A, 0xA2, 0xA3,
            0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3,
            0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2,
            0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA,
        ),
    )

    val AC_CHROMINANCE: HuffmanSpec = HuffmanSpec(
        intArrayOf(0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77),
        intArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71, 0x13, 0x22,
            0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xA1, 0xB1, 0xC1, 0x09, 0x23, 0x33, 0x52, 0xF0, 0x15, 0x62, 0x72, 0xD1,
            0x0A, 0x16, 0x24, 0x34, 0xE1, 0x25, 0xF1, 0x17, 0x18, 0x19, 0x1A, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x35, 0x36,
            0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
            0x59, 0x5A, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A,
            0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9A,
            0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xB9, 0xBA,
            0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3, 0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA,
            0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8, 0xE9, 0xEA, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA,
        ),
    )

    private const val F0298 = 2446
    private const val F0390 = 3196
    private const val F0541 = 4433
    private const val F0765 = 6270
    private const val F0899 = 7373
    private const val F1175 = 9633
    private const val F1501 = 12299
    private const val F1847 = 15137
    private const val F1961 = 16069
    private const val F2053 = 16819
    private const val F2562 = 20995
    private const val F3072 = 25172

    fun encode(width: Int, height: Int, rgba: ByteArray, quality: Int, matteRgb: Int): ByteArray {
        val quantisers = arrayOf(scale(LUMINANCE_QUANTISER, quality), scale(CHROMINANCE_QUANTISER, quality))
        val out = ByteSink(width * height / 2 + 1024)
        out.put(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10))
        out.put("JFIF".encodeToByteArray())
        out.put(byteArrayOf(0x00, 0x01, 0x02, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00))
        for (id in 0..1) {
            out.put(0xFF)
            out.put(0xDB)
            out.putBe16(67)
            out.put(id)
            for (i in 0 until 64) {
                out.put(quantisers[id][ZIGZAG[i]])
            }
        }
        out.put(0xFF)
        out.put(0xC0)
        out.putBe16(17)
        out.put(8)
        out.putBe16(height)
        out.putBe16(width)
        out.put(byteArrayOf(3, 1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1))
        putHuffman(out, 0x00, DC_LUMINANCE)
        putHuffman(out, 0x10, AC_LUMINANCE)
        putHuffman(out, 0x01, DC_CHROMINANCE)
        putHuffman(out, 0x11, AC_CHROMINANCE)
        out.put(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x0C, 3, 1, 0x00, 2, 0x11, 3, 0x11, 0, 63, 0))

        val dc = arrayOf(HuffmanEncoder(DC_LUMINANCE), HuffmanEncoder(DC_CHROMINANCE))
        val ac = arrayOf(HuffmanEncoder(AC_LUMINANCE), HuffmanEncoder(AC_CHROMINANCE))
        val writer = ScanWriter(out)
        val previousDc = IntArray(3)
        val blocks = Array(3) { IntArray(64) }
        val zz = IntArray(64)
        val mr = (matteRgb ushr 16) and 0xFF
        val mg = (matteRgb ushr 8) and 0xFF
        val mb = matteRgb and 0xFF
        var by = 0
        while (by < height) {
            var bx = 0
            while (bx < width) {
                for (j in 0 until 8) {
                    val y = minOf(by + j, height - 1)
                    for (i in 0 until 8) {
                        val x = minOf(bx + i, width - 1)
                        val p = (y * width + x) * 4
                        val a = rgba[p + 3].toInt() and 0xFF
                        val r = flatten(rgba[p].toInt() and 0xFF, a, mr)
                        val g = flatten(rgba[p + 1].toInt() and 0xFF, a, mg)
                        val b = flatten(rgba[p + 2].toInt() and 0xFF, a, mb)
                        val k = 8 * j + i
                        blocks[0][k] = (19595 * r + 38470 * g + 7471 * b + 32768) / 65536 - 128
                        blocks[1][k] = (-11059 * r - 21709 * g + 32768 * b + 8421375) / 65536 - 128
                        blocks[2][k] = (32768 * r - 27439 * g - 5329 * b + 8421375) / 65536 - 128
                    }
                }
                for (c in 0 until 3) {
                    val table = if (c == 0) 0 else 1
                    forwardDct(blocks[c])
                    val q = quantisers[table]
                    for (i in 0 until 64) {
                        val k = ZIGZAG[i]
                        val coefficient = blocks[c][k]
                        val divisor = 8 * q[k]
                        var value = if (coefficient >= 0) {
                            (coefficient + divisor / 2) / divisor
                        } else {
                            -((-coefficient + divisor / 2) / divisor)
                        }
                        // The baseline AC tables stop at 10 bits. DC values stay within -1024..1016 by
                        // construction, so their differences fit 11 bits.
                        if (i != 0) {
                            value = value.coerceIn(-1023, 1023)
                        }
                        zz[i] = value
                    }
                    previousDc[c] = encodeBlock(writer, zz, previousDc[c], dc[table], ac[table])
                }
                bx += 8
            }
            by += 8
        }
        writer.finish()
        out.put(0xFF)
        out.put(0xD9)
        return out.toByteArray()
    }

    private fun scale(base: IntArray, quality: Int): IntArray {
        val scale = 200 - 2 * quality
        return IntArray(64) { ((base[it] * scale + 50) / 100).coerceIn(1, 255) }
    }

    private fun putHuffman(out: ByteSink, id: Int, spec: HuffmanSpec) {
        out.put(0xFF)
        out.put(0xC4)
        out.putBe16(2 + 1 + 16 + spec.symbols.size)
        out.put(id)
        for (count in spec.counts) {
            out.put(count)
        }
        for (symbol in spec.symbols) {
            out.put(symbol)
        }
    }

    /** `floor((x + 2^(n-1)) / 2^n)`; `shr` on a signed Int is a floor division. */
    private fun descale(x: Int, n: Int): Int = (x + (1 shl (n - 1))) shr n

    /** One pass over eight values spaced [step] apart, starting at [base]. */
    private fun dctPass(d: IntArray, base: Int, step: Int, first: Boolean) {
        val t0 = d[base] + d[base + 7 * step]
        val t7 = d[base] - d[base + 7 * step]
        val t1 = d[base + step] + d[base + 6 * step]
        val t6 = d[base + step] - d[base + 6 * step]
        val t2 = d[base + 2 * step] + d[base + 5 * step]
        val t5 = d[base + 2 * step] - d[base + 5 * step]
        val t3 = d[base + 3 * step] + d[base + 4 * step]
        val t4 = d[base + 3 * step] - d[base + 4 * step]
        val t10 = t0 + t3
        val t13 = t0 - t3
        val t11 = t1 + t2
        val t12 = t1 - t2
        val n = if (first) 11 else 15
        if (first) {
            d[base] = (t10 + t11) * 4
            d[base + 4 * step] = (t10 - t11) * 4
        } else {
            d[base] = descale(t10 + t11, 2)
            d[base + 4 * step] = descale(t10 - t11, 2)
        }
        var z1 = (t12 + t13) * F0541
        d[base + 2 * step] = descale(z1 + t13 * F0765, n)
        d[base + 6 * step] = descale(z1 - t12 * F1847, n)

        z1 = t4 + t7
        var z2 = t5 + t6
        var z3 = t4 + t6
        var z4 = t5 + t7
        val z5 = (z3 + z4) * F1175
        val m4 = t4 * F0298
        val m5 = t5 * F2053
        val m6 = t6 * F3072
        val m7 = t7 * F1501
        z1 = -z1 * F0899
        z2 = -z2 * F2562
        z3 = -z3 * F1961 + z5
        z4 = -z4 * F0390 + z5
        d[base + 7 * step] = descale(m4 + z1 + z3, n)
        d[base + 5 * step] = descale(m5 + z2 + z4, n)
        d[base + 3 * step] = descale(m6 + z2 + z3, n)
        d[base + step] = descale(m7 + z1 + z4, n)
    }

    private fun forwardDct(block: IntArray) {
        for (row in 0 until 8) {
            dctPass(block, 8 * row, 1, true)
        }
        for (column in 0 until 8) {
            dctPass(block, column, 8, false)
        }
    }

    /** Canonical code assignment of T.81 annex C. */
    private class HuffmanEncoder(spec: HuffmanSpec) {
        val code = IntArray(256)
        val length = IntArray(256)

        init {
            var next = 0
            var k = 0
            for (len in 1..16) {
                repeat(spec.counts[len - 1]) {
                    val symbol = spec.symbols[k++]
                    code[symbol] = next
                    length[symbol] = len
                    next++
                }
                next = next shl 1
            }
        }
    }

    /** Entropy-coded data: most significant bit first, FF followed by 00. */
    private class ScanWriter(private val out: ByteSink) {
        private var acc = 0
        private var used = 0

        fun put(bits: Int, count: Int) {
            acc = (acc shl count) or (bits and ((1 shl count) - 1))
            used += count
            while (used >= 8) {
                val b = (acc ushr (used - 8)) and 0xFF
                out.put(b)
                if (b == 0xFF) {
                    out.put(0x00)
                }
                used -= 8
            }
        }

        fun finish() {
            if (used != 0) {
                put((1 shl (8 - used)) - 1, 8 - used)
            }
        }
    }

    private fun category(v: Int): Int {
        var a = if (v < 0) -v else v
        var n = 0
        while (a != 0) {
            n++
            a = a ushr 1
        }
        return n
    }

    private fun putValue(w: ScanWriter, v: Int, bits: Int) {
        if (bits != 0) {
            w.put(if (v >= 0) v else v + (1 shl bits) - 1, bits)
        }
    }

    /** Codes one block and returns its DC value, the predictor of the next block. */
    private fun encodeBlock(w: ScanWriter, zz: IntArray, previousDc: Int, dc: HuffmanEncoder, ac: HuffmanEncoder): Int {
        val diff = zz[0] - previousDc
        val dcBits = category(diff)
        w.put(dc.code[dcBits], dc.length[dcBits])
        putValue(w, diff, dcBits)
        var run = 0
        for (k in 1 until 64) {
            val v = zz[k]
            if (v == 0) {
                run++
                continue
            }
            while (run >= 16) {
                w.put(ac.code[0xF0], ac.length[0xF0])
                run -= 16
            }
            val bits = category(v)
            val symbol = run * 16 + bits
            w.put(ac.code[symbol], ac.length[symbol])
            putValue(w, v, bits)
            run = 0
        }
        if (run != 0) {
            w.put(ac.code[0x00], ac.length[0x00])
        }
        return zz[0]
    }
}
