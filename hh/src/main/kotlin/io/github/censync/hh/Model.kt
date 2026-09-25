package io.github.censync.hh

/**
 * Universal pictures are the same for everyone; keyed pictures can be computed only with the secret key. The
 * two pictures of one input are unrelated.
 */
public enum class Mode {
    /** The same for everyone: what two parties compare. */
    UNIVERSAL,

    /** Private to the holders of the key: the default inside an application. */
    KEYED,
}

/** The outline of the picture. */
public enum class Shape {
    /** The default. */
    SQUARE,

    /** The 4 x 4 grid inscribed in a circle; no cell is clipped, the cells are smaller. */
    ROUND,
}

/**
 * The frame of a picture. Every style is available in both modes: [NONE], [PLAIN], [DOUBLE] and [THICK] fit
 * either shape, [ROUNDED], [CHAMFERED] and [BRACKETS] need the square shape, [TICKS] and [GAPS] the round one.
 * Rendering refuses a style that does not fit the shape with [HhErrorCode.INVALID_FRAME]. A host that marks its
 * keyed pictures with a frame picks the style; [AUTOMATIC] gives keyed square pictures rounded corners and every
 * other picture no frame.
 */
public enum class FrameStyle {
    /** Keyed and square: [ROUNDED]; otherwise [NONE]. */
    AUTOMATIC,

    /** No frame. */
    NONE,

    /** A thin square frame, or a thin ring. */
    PLAIN,

    /** Square only: rounded corners. */
    ROUNDED,

    /** Square only: four cut corners. */
    CHAMFERED,

    /** Two thin lines. */
    DOUBLE,

    /** One line three times as thick. */
    THICK,

    /** Square only: corner brackets. */
    BRACKETS,

    /** Round only: a ring with four ticks. */
    TICKS,

    /** Round only: a ring with four gaps. */
    GAPS,
}

/** What a cell shows. The ordinal is the layout value of the specification. */
public enum class Figure {
    /** An empty cell. */
    NONE,

    /** The full cell. */
    SQUARE,

    /** The disc inscribed in the cell. */
    CIRCLE,

    /** Base on the bottom side, apex at the middle of the top side. */
    TRIANGLE_UP,

    /** Base on the left side. */
    TRIANGLE_RIGHT,

    /** Base on the top side. */
    TRIANGLE_DOWN,

    /** Base on the right side. */
    TRIANGLE_LEFT,
}

/** One cell of the 4 x 4 matrix. [colour] is a palette index 0..3 and is 0 for an empty cell. */
public class Cell(public val figure: Figure, public val colour: Int) {
    override fun equals(other: Any?): Boolean = other is Cell && figure == other.figure && colour == other.colour

    override fun hashCode(): Int = 31 * figure.hashCode() + colour

    override fun toString(): String = "Cell($figure, $colour)"
}

/**
 * What a fingerprint shows, for hosts that draw vectors themselves. [cells] are row-major from the top left;
 * colours are `0xRRGGBB`. The raster of [Fingerprint.render] is the canonical form and the only one covered by
 * byte-exact vectors.
 */
public class Layout(
    public val mode: Mode,
    public val cells: List<Cell>,
    public val paletteRgb: List<Int>,
    public val frameRgb: Int,
) {
    override fun equals(other: Any?): Boolean = other is Layout && mode == other.mode && cells == other.cells &&
        paletteRgb == other.paletteRgb && frameRgb == other.frameRgb

    override fun hashCode(): Int =
        31 * (31 * (31 * mode.hashCode() + cells.hashCode()) + paletteRgb.hashCode()) + frameRgb

    override fun toString(): String = "Layout($mode, $cells)"
}

/** WCAG contrast ratios times 100 (300 means 3:1), rounded down. */
public class ContrastReport(
    /** The weakest palette colour against the background. */
    public val figuresX100: Int,
    /** The frame against the background. */
    public val frameX100: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is ContrastReport && figuresX100 == other.figuresX100 && frameX100 == other.frameX100

    override fun hashCode(): Int = 31 * figuresX100 + frameX100

    override fun toString(): String = "ContrastReport(figures $figuresX100, frame $frameX100)"
}

/**
 * The look of a render. The cells, the palette and the geometry are fixed by the specification; the shape, the
 * frame and the background are the host's choice, in either mode.
 *
 * @property shape square or round.
 * @property frame the frame style: any style that fits [shape], for universal and keyed fingerprints alike; see
 * [FrameStyle]. Rendering refuses a style that does not fit the shape with [HhErrorCode.INVALID_FRAME].
 * @property backgroundRgb the background colour as `0xRRGGBB`.
 * @property backgroundAlpha 0 (transparent) to 255 (opaque). Outside rounded or chamfered corners and outside
 * the disc the picture is always transparent.
 * @property frameAlpha 0 to 255; the frame colour itself is fixed.
 * @throws HhException with [HhErrorCode.INVALID_ARGUMENT] if a value is out of range.
 */
public class RenderOptions @JvmOverloads constructor(
    public val shape: Shape = Shape.SQUARE,
    public val frame: FrameStyle = FrameStyle.AUTOMATIC,
    public val backgroundRgb: Int = 0xFFFFFF,
    public val backgroundAlpha: Int = 255,
    public val frameAlpha: Int = 255,
) {
    init {
        if (backgroundRgb !in 0..0xFFFFFF || backgroundAlpha !in 0..255 || frameAlpha !in 0..255) {
            throw HhException(HhErrorCode.INVALID_ARGUMENT, "a colour is 0..0xFFFFFF and an alpha is 0..255")
        }
    }

    /**
     * Measures what these options give over a page of the colour [pageRgb] (`0xRRGGBB`); for an opaque
     * background the page does not matter. Rendering refuses an opaque background with
     * [ContrastReport.figuresX100] below 200; hosts should warn below 300.
     */
    @JvmOverloads
    public fun measureContrast(pageRgb: Int = 0xFFFFFF): ContrastReport {
        if (pageRgb !in 0..0xFFFFFF) {
            throw HhException(HhErrorCode.INVALID_ARGUMENT, "a colour is 0..0xFFFFFF")
        }
        val seen = Contrast.over(backgroundRgb, backgroundAlpha, pageRgb)
        val frame = Contrast.over(Palette.FRAME, frameAlpha, seen)
        return ContrastReport(Contrast.figuresX100(seen), Contrast.ratioX100(frame, seen))
    }

    override fun equals(other: Any?): Boolean = other is RenderOptions && shape == other.shape &&
        frame == other.frame && backgroundRgb == other.backgroundRgb && backgroundAlpha == other.backgroundAlpha &&
        frameAlpha == other.frameAlpha

    override fun hashCode(): Int =
        31 * (31 * (31 * (31 * shape.hashCode() + frame.hashCode()) + backgroundRgb) + backgroundAlpha) + frameAlpha

    override fun toString(): String =
        "RenderOptions($shape, $frame, background ${backgroundRgb.toString(16)}/$backgroundAlpha, frame $frameAlpha)"

    public companion object {
        /** White opaque background, square shape, automatic frame. */
        @JvmField
        public val DEFAULT: RenderOptions = RenderOptions()

        /** A transparent background, for pictures laid over the host's own surface. */
        @JvmField
        public val TRANSPARENT: RenderOptions = RenderOptions(backgroundAlpha = 0)
    }
}
