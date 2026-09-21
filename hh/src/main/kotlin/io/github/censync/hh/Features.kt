package io.github.censync.hh

/** The fixed colours: section 5.2 of the specification. */
internal object Palette {
    val COLOURS: IntArray = intArrayOf(0x7A96C5, 0x890AF0, 0xC10445, 0xD48200)
    const val FRAME: Int = 0x808080
}

/** Feature extraction: section 5 of the specification. */
internal object Features {
    private val FIGURES = arrayOf(
        Figure.NONE, Figure.NONE, Figure.SQUARE, Figure.CIRCLE,
        Figure.TRIANGLE_UP, Figure.TRIANGLE_RIGHT, Figure.TRIANGLE_DOWN, Figure.TRIANGLE_LEFT,
    )
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    fun cells(fp: ByteArray): List<Cell> = List(16) { i ->
        val b = fp[i].toInt() and 0xFF
        val figure = FIGURES[b ushr 5]
        Cell(figure, if (figure == Figure.NONE) 0 else (b ushr 3) and 3)
    }

    fun tag(fp: ByteArray): String {
        val word = ((fp[16].toInt() and 0xFF) shl 24) or ((fp[17].toInt() and 0xFF) shl 16) or
            ((fp[18].toInt() and 0xFF) shl 8) or (fp[19].toInt() and 0xFF)
        val v = word ushr 2
        val out = StringBuilder(6)
        for (i in 0 until 6) {
            out.append(CROCKFORD[(v ushr (25 - 5 * i)) and 31])
        }
        return out.toString()
    }
}
