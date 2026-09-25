package io.github.censync.hh

/** Geometry: section 7 of the specification. All values are pixels. */
internal class Geometry private constructor(
    val size: Int,
    val line: Int,
    val gutter: Int,
    val cell: Int,
) {
    val grid: Int = 4 * cell + 3 * gutter
    val offset: Int = (size - grid) / 2

    companion object {
        fun of(size: Int, shape: Shape, frame: FrameStyle): Geometry {
            val w = maxOf(1, size / 48)
            val g = maxOf(1, size / 48)
            val t: Int
            if (shape == Shape.ROUND) {
                val k = if (frame == FrameStyle.DOUBLE || frame == FrameStyle.THICK) 3 else 1
                val margin = k * w + g
                val limit = (size - 2 * margin) * (size - 2 * margin)
                var cell = 0
                while (2 * (4 * (cell + 1) + 3 * g) * (4 * (cell + 1) + 3 * g) <= limit) {
                    cell++
                }
                t = cell
            } else {
                val margin = maxOf(4 * w, size / 12)
                t = (size - 2 * margin - 3 * g) / 4
            }
            return Geometry(size, w, g, t)
        }
    }
}

/** Rasterisation: sections 6 and 8 of the specification. */
internal object Raster {
    const val MIN_SIZE: Int = 16
    const val MAX_SIZE: Int = 1024

    fun resolveFrame(frame: FrameStyle, mode: Mode, shape: Shape): FrameStyle = when {
        frame != FrameStyle.AUTOMATIC -> frame
        mode == Mode.KEYED && shape == Shape.SQUARE -> FrameStyle.ROUNDED
        else -> FrameStyle.NONE
    }

    /** Whether a resolved style may be used with the shape. The mode plays no part. */
    fun frameAllowed(resolved: FrameStyle, shape: Shape): Boolean = when (resolved) {
        FrameStyle.NONE, FrameStyle.PLAIN, FrameStyle.DOUBLE, FrameStyle.THICK -> true
        FrameStyle.ROUNDED, FrameStyle.CHAMFERED, FrameStyle.BRACKETS -> shape == Shape.SQUARE
        FrameStyle.TICKS, FrameStyle.GAPS -> shape == Shape.ROUND
        FrameStyle.AUTOMATIC -> false
    }

    /** Renders `size * size * 4` RGBA bytes, with the checks of section 6 in their specified order. */
    fun render(fp: ByteArray, mode: Mode, size: Int, options: RenderOptions): ByteArray {
        if (size < MIN_SIZE || size > MAX_SIZE) {
            throw HhException(HhErrorCode.INVALID_SIZE, "the size must be $MIN_SIZE..$MAX_SIZE")
        }
        val frame = resolveFrame(options.frame, mode, options.shape)
        if (!frameAllowed(frame, options.shape)) {
            throw HhException(HhErrorCode.INVALID_FRAME, "the frame $frame does not fit the shape ${options.shape}")
        }
        if (options.backgroundAlpha == 255 && Contrast.figuresX100(options.backgroundRgb) < 200) {
            throw HhException(HhErrorCode.LOW_CONTRAST, "the background is too close to a palette colour")
        }
        val g = Geometry.of(size, options.shape, frame)
        if (g.cell < 1) {
            throw HhException(HhErrorCode.INVALID_SIZE, "the size leaves no room for the cells")
        }
        return rasterise(Features.cells(fp), g, options.shape, frame, options)
    }

    private fun rasterise(
        cells: List<Cell>,
        g: Geometry,
        shape: Shape,
        frame: FrameStyle,
        o: RenderOptions,
    ): ByteArray {
        val rgba = ByteArray(g.size * g.size * 4)
        val tester = FrameTester(g, shape, frame)
        val mixer = Mixer(o.backgroundRgb, o.backgroundAlpha)

        // Step 1: the surface and the frame. Inside the grid area every sample is inside the outline and off
        // the frame, so those pixels are the plain surface.
        val framePixels = IntArray(17 * 17)
        val frameKnown = BooleanArray(17 * 17)
        val surface = mixer.mix(Palette.FRAME, o.frameAlpha, 0, 16)
        for (y in 0 until g.size) {
            val gridRow = y >= g.offset && y < g.offset + g.grid
            for (x in 0 until g.size) {
                if (gridRow && x >= g.offset && x < g.offset + g.grid) {
                    put(rgba, g.size, x, y, surface)
                    continue
                }
                var inside = 0
                var onFrame = 0
                for (j in 0 until 4) {
                    val v = 2 * (4 * y + j) + 1
                    for (i in 0 until 4) {
                        val u = 2 * (4 * x + i) + 1
                        if (tester.inOutline(u, v)) {
                            inside++
                            if (tester.onFrame(u, v)) {
                                onFrame++
                            }
                        }
                    }
                }
                val slot = inside * 17 + onFrame
                if (!frameKnown[slot]) {
                    framePixels[slot] = mixer.mix(Palette.FRAME, o.frameAlpha, onFrame, inside - onFrame)
                    frameKnown[slot] = true
                }
                put(rgba, g.size, x, y, framePixels[slot])
            }
        }

        // Step 2: the figures.
        val h = 4 * g.cell
        for (index in 0 until 16) {
            val cell = cells[index]
            if (cell.figure == Figure.NONE) {
                continue
            }
            val colour = Palette.COLOURS[cell.colour]
            val shades = IntArray(17) { n -> mixer.mix(colour, 255, n, 16 - n) }
            val x0 = g.offset + (index % 4) * (g.cell + g.gutter)
            val y0 = g.offset + (index / 4) * (g.cell + g.gutter)
            for (py in 0 until g.cell) {
                for (px in 0 until g.cell) {
                    var n = 0
                    if (cell.figure == Figure.SQUARE) {
                        n = 16
                    } else {
                        for (j in 0 until 4) {
                            val v = 2 * (4 * py + j) + 1
                            for (i in 0 until 4) {
                                val u = 2 * (4 * px + i) + 1
                                if (inFigure(cell.figure, u, v, h)) {
                                    n++
                                }
                            }
                        }
                    }
                    put(rgba, g.size, x0 + px, y0 + py, shades[n])
                }
            }
        }
        return rgba
    }

    /** Section 8.4; [h] is `H = 4 t`. */
    private fun inFigure(figure: Figure, u: Int, v: Int, h: Int): Boolean = when (figure) {
        Figure.NONE -> false
        Figure.SQUARE -> true
        Figure.CIRCLE -> (u - h) * (u - h) + (v - h) * (v - h) <= h * h
        Figure.TRIANGLE_UP -> 2 * abs(u - h) <= v
        Figure.TRIANGLE_DOWN -> 2 * abs(u - h) <= 2 * h - v
        Figure.TRIANGLE_RIGHT -> 2 * abs(v - h) <= 2 * h - u
        Figure.TRIANGLE_LEFT -> 2 * abs(v - h) <= u
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v

    /** Writes a pixel packed as `R << 24 | G << 16 | B << 8 | A`. */
    private fun put(rgba: ByteArray, size: Int, x: Int, y: Int, pixel: Int) {
        val p = (y * size + x) * 4
        rgba[p] = (pixel ushr 24).toByte()
        rgba[p + 1] = (pixel ushr 16).toByte()
        rgba[p + 2] = (pixel ushr 8).toByte()
        rgba[p + 3] = pixel.toByte()
    }

    /** `MIX` of section 8.5 over a fixed background. */
    private class Mixer(private val backgroundRgb: Int, private val ab: Int) {
        /** Returns the pixel packed as `R << 24 | G << 16 | B << 8 | A`. */
        fun mix(rgb: Int, a: Int, nf: Int, nb: Int): Int {
            val total = nf * (255 * a + ab * (255 - a)) + nb * 255 * ab
            val alpha = (total + 2040) / 4080
            if (alpha == 0) {
                return 0
            }
            var out = alpha
            for (shift in intArrayOf(16, 8, 0)) {
                val f = (rgb ushr shift) and 0xFF
                val b = (backgroundRgb ushr shift) and 0xFF
                val p = nf * (255 * a * f + ab * (255 - a) * b) + nb * 255 * ab * b
                out = out or (((p + total / 2) / total) shl (shift + 8))
            }
            return out
        }
    }

    /** The per-sample tests of sections 8.2 and 8.3. */
    private class FrameTester(g: Geometry, shape: Shape, private val style: FrameStyle) {
        private val round = shape == Shape.ROUND
        private val s8 = 8 * g.size
        private val w8 = 8 * g.line
        private val r = 4 * g.size
        private val corner = minOf(16 * g.offset, 4 * g.size)
        private val diagonal = (w8 * 1414 + 500) / 1000
        private val chamfer = maxOf(8, (16 * g.offset - diagonal - w8) / 8 * 8)
        private val bracket = 8 * (g.size / 4)
        private val gap = 8 * maxOf(1, g.size / 24)
        private val tickEnd = (r - w8) - maxOf(8, ((r - w8) - 4 * g.grid) * 6 / 10)

        fun inOutline(u: Int, v: Int): Boolean {
            if (round) {
                val dx = u - r
                val dy = v - r
                return dx * dx + dy * dy <= r * r
            }
            val du = minOf(u, s8 - u)
            val dv = minOf(v, s8 - v)
            if (style == FrameStyle.ROUNDED) {
                if (du < corner && dv < corner) {
                    val ex = corner - du
                    val ey = corner - dv
                    return ex * ex + ey * ey <= corner * corner
                }
                return true
            }
            if (style == FrameStyle.CHAMFERED) {
                return du + dv >= chamfer
            }
            return true
        }

        /** Only meaningful for samples inside the outline. */
        fun onFrame(u: Int, v: Int): Boolean {
            if (style == FrameStyle.NONE) {
                return false
            }
            if (round) {
                val dx = u - r
                val dy = v - r
                val d2 = dx * dx + dy * dy
                val ring = d2 > (r - w8) * (r - w8)
                return when (style) {
                    FrameStyle.PLAIN -> ring
                    FrameStyle.DOUBLE ->
                        ring || (d2 > (r - 3 * w8) * (r - 3 * w8) && d2 <= (r - 2 * w8) * (r - 2 * w8))
                    FrameStyle.THICK -> d2 > (r - 3 * w8) * (r - 3 * w8)
                    FrameStyle.GAPS -> ring && abs(abs(dx) - abs(dy)) >= gap
                    FrameStyle.TICKS ->
                        ring || (abs(dx) < w8 && abs(dy) >= tickEnd) || (abs(dy) < w8 && abs(dx) >= tickEnd)
                    else -> false
                }
            }
            val du = minOf(u, s8 - u)
            val dv = minOf(v, s8 - v)
            val e = minOf(du, dv)
            return when (style) {
                FrameStyle.PLAIN -> e < w8
                FrameStyle.DOUBLE -> e < w8 || (e >= 2 * w8 && e < 3 * w8)
                FrameStyle.THICK -> e < 3 * w8
                FrameStyle.BRACKETS -> e < w8 && maxOf(du, dv) < bracket
                FrameStyle.CHAMFERED -> e < w8 || du + dv - chamfer < diagonal
                FrameStyle.ROUNDED ->
                    if (du < corner && dv < corner) {
                        val ex = corner - du
                        val ey = corner - dv
                        ex * ex + ey * ey > (corner - w8) * (corner - w8)
                    } else {
                        e < w8
                    }
                else -> false
            }
        }

        private fun abs(v: Int): Int = if (v < 0) -v else v
    }
}
