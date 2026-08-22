package uk.co.reiad.library.core

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.PI

/* ============================================================
   The site's colours, computed rather than copied.

   `next/styles/site.css` states every colour once, in OKLCH, and
   derives the rest with `color-mix(in oklab, ...)`. One hue number
   per destination, one lightness per side (44% light, 72% dark),
   and a chroma cap. That is a system, and hand-copying hex out of
   it would be a second copy that drifts the first time an accent
   is retuned.

   So the numbers here are the site's own OKLCH values and the
   conversion is done properly: OKLCH to OKLab to linear sRGB to
   gamma, and `color-mix` mixed in OKLab exactly where the
   stylesheet mixes it.

   ---- how this is known to be right ----

   The site has a reference implementation of exactly this
   pipeline, `scripts/check-contrast.ts`, which measures 126 pairs
   without a browser and can print them. `ColourTest` asserts the
   numbers it prints, to two decimal places: 7.23 for secondary
   text on the page, 7.18 for a link on a card, 10.82 for a
   heading on a card in dark. Matching another implementation of
   the same maths, digit for digit, is a stronger statement than
   any single value would be.

   Do NOT take the four ratios written into the stylesheet's token
   block as the oracle. They say 16.2, 7.7, 8.3 and 4.9, they are
   labelled as measured in a browser, and they are stale: the real
   figures are 15.88, 7.23, 7.18 and 7.08. `check-contrast.ts`
   exists because that comment went out of date and nothing
   re-measured it, which its own header says at length. This file
   made the same mistake for one commit, trusting the prose over
   the program.
   ============================================================ */

/** A colour in the space the site states its colours in. `l` is
    0 to 1 where the stylesheet writes a percentage, `c` is
    absolute chroma, `h` is degrees. */
data class Oklch(val l: Double, val c: Double, val h: Double) {

    /** Straight sRGB, each channel 0 to 255. */
    fun toRgb(): Rgb {
        val hr = h * PI / 180.0
        val a = c * cos(hr)
        val b = c * sin(hr)
        return oklabToRgb(l, a, b)
    }

    fun toArgb(): Int {
        val rgb = toRgb()
        return (0xFF shl 24) or (rgb.r shl 16) or (rgb.g shl 8) or rgb.b
    }

    /** `color-mix(in oklab, other amount%, this)`: the stylesheet
        always names the accent first and the ground second, and
        the amount is how much of the FIRST survives. */
    fun mix(other: Oklch, amount: Double): Oklch {
        val (l1, a1, b1) = labOf(other)
        val (l2, a2, b2) = labOf(this)
        val l = l1 * amount + l2 * (1 - amount)
        val a = a1 * amount + a2 * (1 - amount)
        val b = b1 * amount + b2 * (1 - amount)
        val chroma = kotlin.math.sqrt(a * a + b * b)
        var hue = kotlin.math.atan2(b, a) * 180.0 / PI
        if (hue < 0) hue += 360.0
        return Oklch(l, chroma, hue)
    }

    private fun labOf(colour: Oklch): Triple<Double, Double, Double> {
        val hr = colour.h * PI / 180.0
        return Triple(colour.l, colour.c * cos(hr), colour.c * sin(hr))
    }
}

data class Rgb(val r: Int, val g: Int, val b: Int) {
    fun hex(): String = "#%02X%02X%02X".format(r, g, b)
}

/* ---------- the conversion, which is a fixed pipeline ---------- */

private fun oklabToRgb(l: Double, a: Double, b: Double): Rgb {
    val (rl, gl, bl) = oklabToLinear(l, a, b)
    return Rgb(gamma(rl), gamma(gl), gamma(bl))
}

/** OKLab to linear sRGB. The matrices are the specification's. */
private fun oklabToLinear(l: Double, a: Double, b: Double): Triple<Double, Double, Double> {
    val lp = l + 0.3963377774 * a + 0.2158037573 * b
    val mp = l - 0.1055613458 * a - 0.0638541728 * b
    val sp = l - 0.0894841775 * a - 1.2914855480 * b

    val lc = lp * lp * lp
    val mc = mp * mp * mp
    val sc = sp * sp * sp

    return Triple(
        4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
        -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
        -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
    )
}

private fun gamma(linear: Double): Int {
    val clamped = linear.coerceIn(0.0, 1.0)
    val encoded =
        if (clamped <= 0.0031308) 12.92 * clamped
        else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
    return kotlin.math.round(encoded * 255.0).toInt().coerceIn(0, 255)
}

/** WCAG relative luminance, off the LINEAR channels, which is
    why this does not just read the 0 to 255 values back. */
fun luminanceOf(colour: Oklch): Double {
    val hr = colour.h * PI / 180.0
    val (r, g, b) = oklabToLinear(colour.l, colour.c * cos(hr), colour.c * sin(hr))
    return 0.2126 * r.coerceIn(0.0, 1.0) +
        0.7152 * g.coerceIn(0.0, 1.0) +
        0.0722 * b.coerceIn(0.0, 1.0)
}

/** The ratio the accessibility thresholds are stated in. */
fun contrastOf(a: Oklch, b: Oklch): Double {
    val la = luminanceOf(a)
    val lb = luminanceOf(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/* ---------- the site's own numbers ---------- */

/** One hue per destination, and the whole palette hangs off them.
    The money school and the site itself are green; German is
    blue; Qur'anic Arabic teal; English violet; the work plum; the
    kitchen rose; the calculators gold. */
object Hue {
    const val GREEN = 162.0
    const val TEAL = 205.0
    const val BLUE = 255.0
    const val VIOLET = 300.0
    const val PLUM = 345.0
    const val ROSE = 30.0
    const val GOLD = 75.0
    const val PAPER = 84.0
}

/** An accent, both ways round. One lightness per side is the
    enforced rule on the site: 44% light, 72% dark. */
data class Accent(val light: Oklch, val dark: Oklch) {
    companion object {
        fun of(hue: Double, lightChroma: Double = 0.090) = Accent(
            light = Oklch(0.44, lightChroma, hue),
            dark = Oklch(0.72, 0.105, hue),
        )
    }
}

object Accents {
    val GREEN = Accent.of(Hue.GREEN)

    /** Teal's light chroma is 0.076 rather than 0.090, and it is
        the one exception in the table. */
    val TEAL = Accent.of(Hue.TEAL, lightChroma = 0.076)
    val BLUE = Accent.of(Hue.BLUE)
    val VIOLET = Accent.of(Hue.VIOLET)
    val PLUM = Accent.of(Hue.PLUM)
    val ROSE = Accent.of(Hue.ROSE)
    val GOLD = Accent.of(Hue.GOLD)

    val DANGER = Accent(
        light = Oklch(0.52, 0.150, 28.0),
        dark = Oklch(0.70, 0.140, 28.0),
    )

    /** Which colour a destination owns. The site keeps this in
        one table and sends it at `/api/site` under `accents`, so
        this map is the fallback for a first run with no network,
        not a second source. */
    val BY_KEY: Map<String, Accent> = mapOf(
        "money" to GREEN,
        "deutsch" to BLUE,
        "quran" to TEAL,
        "english" to VIOLET,
        "insights" to GREEN,
        "cooking" to ROSE,
        "travel" to ROSE,
        "portfolio" to PLUM,
        "about" to PLUM,
        "contact" to PLUM,
        "tools" to GOLD,
        "skills" to GREEN,
        "account" to GREEN,
    )

    /** The token name the site writes, `var(--blue)` and friends,
        as the API sends them. */
    fun byToken(token: String): Accent? = when (token.trim().removeSurrounding("var(", ")")) {
        "--green" -> GREEN
        "--teal" -> TEAL
        "--blue" -> BLUE
        "--violet" -> VIOLET
        "--plum" -> PLUM
        "--rose" -> ROSE
        "--gold" -> GOLD
        else -> null
    }
}

/** Every ground and every ink, for one accent and one side.

    The site derives these with `color-mix`, and the tint
    percentages differ per side on purpose: on paper the accent is
    a whisper, and on a dark ground the same accent has to LIFT
    the surface, so it is about three times as much. */
/** A colour that is mostly not there: the achromatic whites and
    blacks the material's edges and reflections are drawn in.

    Named for what it is rather than for the site's `--glass-veil`,
    which is a different thing one word away: that is the AMOUNT a
    reader chooses in their settings, and it is `Veil` in
    `Prefs.kt`. Two types with one name in one package is a
    compiler crash rather than an error, which is how this got
    noticed.

    They are achromatic ON PURPOSE and it is not a simplification.
    A reflection carries the colour of the light rather than of
    the page, which is what keeps the specular and the cut edge
    distinguishable from the glow, and the glow is the one that is
    the accent's colour. Mix the page's accent into these and the
    material stops having two different kinds of light in it. */
data class Sheer(val colour: Oklch, val alpha: Double) {

    /** The one place a veil takes the page's colour: a CUT edge.

        Real glass disperses at a cut, so the two sides of the
        same piece come back at different hues, and that is most
        of why a bevel reads as glass rather than as a white
        outline somebody drew. `polish` is the knob, because a
        polished edge splits cleanly and a ground one scatters
        everything back as white.

        Mixed in OKLab like every other mix here. The alpha is
        averaged straight rather than premultiplied, which is not
        what `color-mix` does: at the amounts this is called with,
        at most 32% of an opaque accent into a 72% white, the two
        differ by well under one step of eight-bit alpha. */
    fun tinted(with: Oklch, amount: Double): Sheer =
        Sheer(colour.mix(with, amount), alpha * (1 - amount) + amount)
}

data class Surfaces(
    val paper: Oklch,
    val paperSunk: Oklch,
    val panel: Oklch,
    val hairline: Oklch,
    val ink: Oklch,
    val inkSoft: Oklch,
    val accent: Oklch,
    val accentSoft: Oklch,
    val accentLine: Oklch,

    /* ---- the material's own three, and why they are here ----

       `paneTop` is the 1px lit line along the top of a pane and
       is 6% white in dark mode. That is correct for a hairline
       and hopeless as the source for a specular sweep: at 30% of
       6% the reflection is under two per cent white, which is
       another way of saying it is not drawn.

       That one token is why the site's whole material read as
       absent in dark and visible only in light, the same code
       producing a visible button and an invisible card on one
       page. `glassFace` is the material's own highlight at a
       strength a piece of glass actually reflects, and the two
       are separate for that reason rather than by accident. */
    val paneTop: Sheer,
    val glassFace: Sheer,
    val glassUnder: Sheer,

    /** The texture's ink, which is the ONE part of the material
        that does take the page's colour: a weave is IN the paper
        rather than reflected off it. 60% accent into a neutral
        base, at an alpha low enough that a reader who has to ask
        whether it is tinted is getting it right. */
    val texInk: Oklch,
    val texA: Double,
    val texB: Double,
) {
    companion object {

        fun light(accent: Accent): Surfaces {
            val a = accent.light
            val paperBase = Oklch(0.984, 0.008, Hue.PAPER)
            val sunkBase = Oklch(0.964, 0.011, Hue.PAPER)
            val panelBase = Oklch(1.000, 0.003, Hue.PAPER)
            val hairlineBase = Oklch(0.885, 0.012, Hue.PAPER)
            val paper = paperBase.mix(a, 0.04)
            return Surfaces(
                paper = paper,
                paperSunk = sunkBase.mix(a, 0.06),
                panel = panelBase.mix(a, 0.02),
                hairline = hairlineBase.mix(a, 0.12),
                ink = Oklch(0.24, 0.012, Hue.GREEN),
                inkSoft = Oklch(0.43, 0.012, Hue.GREEN),
                accent = a,
                accentSoft = paper.mix(a, 0.11),
                accentLine = hairlineBase.mix(a, 0.28),
                paneTop = Sheer(WHITE, 0.55),
                glassFace = Sheer(WHITE, 0.72),
                glassUnder = Sheer(BLACK, 0.06),
                texInk = TEX_BASE.mix(a, 0.60),
                texA = 0.035,
                texB = 0.028,
            )
        }

        fun dark(accent: Accent): Surfaces {
            val a = accent.dark
            val paperBase = Oklch(0.165, 0.012, Hue.GREEN)
            val sunkBase = Oklch(0.140, 0.012, Hue.GREEN)
            val panelBase = Oklch(0.230, 0.016, Hue.GREEN)
            val hairlineBase = Oklch(0.280, 0.015, Hue.GREEN)
            val paper = paperBase.mix(a, 0.07)
            return Surfaces(
                paper = paper,
                paperSunk = sunkBase.mix(a, 0.08),
                panel = panelBase.mix(a, 0.16),
                hairline = hairlineBase.mix(a, 0.18),
                ink = Oklch(0.935, 0.008, Hue.GREEN),
                inkSoft = Oklch(0.72, 0.020, Hue.GREEN),
                accent = a,
                accentSoft = paper.mix(a, 0.11),
                accentLine = hairlineBase.mix(a, 0.28),
                /* Nine times fainter than the light theme's, which
                   is right for a hairline and is exactly why the
                   material may not read its highlight out of it. */
                paneTop = Sheer(WHITE, 0.06),
                glassFace = Sheer(WHITE, 0.30),
                glassUnder = Sheer(BLACK, 0.22),
                texInk = TEX_BASE.mix(a, 0.60),
                texA = 0.05,
                texB = 0.042,
            )
        }

        private val WHITE = Oklch(1.0, 0.0, 0.0)
        private val BLACK = Oklch(0.0, 0.0, 0.0)
        private val TEX_BASE = Oklch(0.45, 0.04, Hue.GREEN)

        fun of(accent: Accent, dark: Boolean): Surfaces =
            if (dark) dark(accent) else light(accent)
    }
}

/* ---------- the rest of the system, which is only numbers ---------- */

/** Corners are a ladder, never a number. A row and a control are
    pills; a card is `radius`; a field is `sm`. */
object Radius {
    const val XS = 5
    const val SM = 12
    const val CARD = 18
    const val LG = 24
    const val PILL = 999
}

/** Nine steps, ratio about 1.095, in scale-relative units. The
    site enforces that nothing below the top of the scale is a
    literal size. */
object TypeScale {
    val STEPS = listOf(0.60, 0.66, 0.72, 0.79, 0.86, 0.94, 1.03, 1.13, 1.24)
    fun step(n: Int): Double = STEPS[n.coerceIn(0, STEPS.lastIndex)]
}

/** A 2px ramp rather than a 4px one, because 6 and 10 are this
    site's two commonest gaps. */
object Space {
    val STEPS = listOf(2, 4, 6, 8, 10, 12, 16, 20, 26, 34, 44)
    fun step(n: Int): Int = STEPS[(n - 1).coerceIn(0, STEPS.lastIndex)]

    /** The one height for anything pressed, and the smaller one
        for a control inside a row that is itself a target. */
    const val TAP = 44
    const val TAP_SMALL = 36
}

/** The light comes up fast and goes out slowly, and the asymmetry
    is most of what makes a surface read as a material rather than
    as a hover state. */
object Motion {
    const val IN_MS = 190
    const val OUT_MS = 820
    const val QUICK_MS = 160
    const val FAST_MS = 180
    const val SLOW_MS = 450
    const val ENTER_MS = 260
}
