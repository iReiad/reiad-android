package uk.co.reiad.library.core

/* ============================================================
   The material: six kinds of glass, and nothing visible typed.

   The site states four numbers per kind and DERIVES everything a
   reader can see from them. That is the whole point of the system
   and it is the part most easily lost in a port: a renderer that
   copies the finished shadows out of the stylesheet has six
   hard-coded appearances, and the day a kind is retuned it has
   six wrong ones. So the four numbers are here, the derivations
   are here, and the Compose layer draws what this returns.

   | kind    | depth | polish | clarity | standing | follows |
   |---------|-------|--------|---------|----------|---------|
   | chip    | 1     | 0.94   | 0.78    | 0.3      | yes     |
   | control | 2.2   | 0.80   | 0.70    | 0.9      | yes     |
   | card    | 5     | 0.50   | 0.50    | 1        | yes     |
   | pane    | 9     | 0.30   | 0.30    | 1        | yes     |
   | plate   | 3.4   | 0.55   | 0.28    | 1        | no      |
   | groove  | 1.6   | 0.88   | 0.74    | 0        | no      |

   Three of the four describe the glass. `standing` describes the
   SITUATION, and it is the difference between a design system and
   a coat of paint: a lone button has to look pressable because
   nothing else says it is, and a row in a column of twenty does
   not, because the list is the affordance. Standing 0 is why a
   rung draws no box.

   The test for a class's kind is not what it looks like. It is
   what happens when you press it: a chip latches, a control acts,
   a card takes you in, a pane holds other things, a plate is
   read, a groove is filled.
   ============================================================ */

/** The four numbers a piece of glass is.

    A separate type rather than six enum constants with fields,
    because every derivation below takes one of these and a test
    that cannot build a hypothetical material can only ever assert
    that the six shipped values are the six shipped values. The
    ladder, the cap and the gating are properties of the ARITHMETIC
    and have to be provable against numbers nobody has typed into
    the table. */
data class Material(
    val depth: Double,
    val polish: Double,
    val clarity: Double,
    val standing: Double,
    /** Whether the light follows a finger across it. A plate and
        a groove are read rather than pressed, so their light is
        still: on the site that is `--glow-w: 0`, which is also
        the membership test the glow module uses. */
    val follows: Boolean,
    /** A channel cut IN rather than a slab sitting ON. */
    val cut: Boolean = false,
)

/** The six. */
enum class Kind(val glass: Material) {
    CHIP(Material(1.0, 0.94, 0.78, 0.3, follows = true)),
    CONTROL(Material(2.2, 0.80, 0.70, 0.9, follows = true)),
    CARD(Material(5.0, 0.50, 0.50, 1.0, follows = true)),
    PANE(Material(9.0, 0.30, 0.30, 1.0, follows = true)),
    PLATE(Material(3.4, 0.55, 0.28, 1.0, follows = false)),

    /** The sixth, and the inverse of the other five. A groove is
        not a thinner plate: it is a channel cut IN, so the light
        in it runs the other way up, the near wall in shadow at
        the top and the far wall catching the light at the bottom.
        `standing` 0 says it in the system's own words, because a
        groove stands on nothing, it IS the absence of standing. */
    GROOVE(Material(1.6, 0.88, 0.74, 0.0, follows = false, cut = true)),
    ;

    val depth: Double get() = glass.depth
    val polish: Double get() = glass.polish
    val clarity: Double get() = glass.clarity
    val standing: Double get() = glass.standing
    val follows: Boolean get() = glass.follows
    val cut: Boolean get() = glass.cut
}

/** How wide the light spreads, in dp.

    46 per millimetre of depth, capped at 220. The cap is a
    physical statement rather than a guard: a light bigger than
    the object holding it stops reading as a light in the glass
    and becomes a wash over the whole panel. A chip stays at 46, a
    control reaches 101, and a card and a pane both settle at the
    cap. */
fun glowWidth(glass: Material): Double =
    if (!glass.follows) 0.0 else minOf(glass.depth * 46.0, 220.0)
fun glowWidth(kind: Kind): Double = glowWidth(kind.glass)

/** How strong it is, 0 to 1. 32% at full clarity and not 44%: a
    material on every surface of a page has to be quiet or it is
    noise everywhere, and the ladder between the kinds is what
    carries the meaning rather than the absolute brightness. */
fun glowStrength(glass: Material): Double = glass.clarity * 0.32
fun glowStrength(kind: Kind): Double = glowStrength(kind.glass)

/** How far the falloff reaches, as a fraction of the radius. A
    polished face gives a tight bright core with a quick edge; a
    rough one scatters the same light into a broad soft wash. */
fun glowStop(glass: Material): Double = 0.52 + (1.0 - glass.polish) * 0.46
fun glowStop(kind: Kind): Double = glowStop(kind.glass)

/* ---------- the cut edge ---------- */

/** Which of the three glass colours a layer is drawn in.

    They are achromatic on purpose. A reflection carries the
    colour of the light rather than of the page, which is what
    keeps the specular and the edge distinguishable from the glow,
    and the glow is the one that is the accent's colour. `RIM` is
    the exception and says so: a cut edge of real glass DISPERSES,
    so polish mixes the section's accent into it, which is most of
    why a bevel reads as glass rather than as an outline somebody
    drew. */
enum class Source { FACE, UNDER, RIM }

/** One inset shadow, in the same four numbers CSS states one in.

    Everything is dp except `alpha`, which is 0 to 1 of the source
    colour. A negative `spread` shrinks the shadow's shape inward,
    which is what turns an offset inset shadow into a crescent
    hugging one edge instead of a band across the whole box. */
data class EdgeLayer(
    val dx: Double,
    val dy: Double,
    val blur: Double,
    val spread: Double,
    val alpha: Double,
    val source: Source,
)

/** The edge of a slab, in the order the stylesheet lists it.

    Flat on top. The thickness is at the CUT EDGE, and `depth` is
    a length in pixels here, which is the most literal reading it
    has anywhere in the system: a chip is one pixel of edge and a
    pane is nine. It drives the BOTTOM band only, because what you
    see looking slightly down at a slab is the whole top face, the
    far edge foreshortened to a line, and the near edge showing
    its full thickness. A five pixel band across the TOP of a card
    is not an edge, it is a grey bar.

    Every part is gated by `standing`, and one of them was not for
    a while: a flat row kept 22% of a catch light along its
    bottom, which is a border on an unhovered rail item, which is
    the box again in its last hiding place. */
fun slabEdge(glass: Material): List<EdgeLayer> {
    val d = glass.depth
    val s = glass.standing
    val rim = 0.5 + d * 0.055
    return listOf(
        // the catch light on the near wall, at the full thickness
        EdgeLayer(0.0, d * -0.55, 0.0, d * -0.26, s * 0.38, Source.FACE),
        // the seat just above it, so the two read as a cut rather than a glow
        EdgeLayer(0.0, d * -0.42, 0.0, d * -0.20, d * (0.02 + s * 0.06), Source.UNDER),
        /* and the rim, all the way round and SOFT.

           A ring with a blur of zero is a drawn outline: the same
           weight in the corners as along the sides, which nested
           inside another surface reads as a box inside a box.
           Blurring it just past its own width is the whole fix,
           so where a small piece of glass sits on a larger one
           the two grounds meet with no line between them. */
        EdgeLayer(0.0, 0.0, rim * 1.2, rim, s * 0.26, Source.RIM),
    )
}

fun slabEdge(kind: Kind): List<EdgeLayer> = slabEdge(kind.glass)

/** The moving part, and it is the only part that should move.

    A cut edge is a fact about the object: it is the same edge in
    the dark. What changes when a light crosses it is the
    REFLECTION. So this is a thin bright glint on the stretch of
    edge closest to the finger, offset AWAY from it, because an
    inset shadow pushed right lands on the left inner edge.

    `nx` and `ny` are the touch as signed numbers from the centre,
    -1 to 1. `a` is how lit the surface is, 0 at rest. */
fun glintFor(glass: Material, nx: Double, ny: Double, a: Double): EdgeLayer =
    EdgeLayer(
        dx = nx * -7.0,
        dy = ny * -7.0,
        blur = 7.0,
        spread = -5.0,
        alpha = a * glass.standing * 0.60,
        source = Source.FACE,
    )

fun glintFor(kind: Kind, nx: Double, ny: Double, a: Double): EdgeLayer =
    glintFor(kind.glass, nx, ny, a)

/** The edge of a channel, which runs the other way up.

    The near wall is in shadow at the TOP: a positive y offset
    throws an inset shadow downwards from the upper edge, and that
    is the whole of what makes a groove a groove rather than a
    shallow plate. The far wall catches the light along the
    bottom. There is no rim, because a groove stands on nothing. */
fun grooveEdge(glass: Material): List<EdgeLayer> {
    val d = glass.depth
    return listOf(
        EdgeLayer(0.0, d * 1.2, 0.0, d * -0.45, d * (0.08 + glass.clarity * 0.14), Source.UNDER),
        EdgeLayer(0.0, -1.0, 0.0, 0.0, glass.polish * 0.40, Source.FACE),
    )
}

fun grooveEdge(kind: Kind): List<EdgeLayer> = grooveEdge(kind.glass)

fun edgeOf(glass: Material): List<EdgeLayer> =
    if (glass.cut) grooveEdge(glass) else slabEdge(glass)
fun edgeOf(kind: Kind): List<EdgeLayer> = edgeOf(kind.glass)

/** How much of the accent is mixed into the rim.

    Polish is the right knob, because a polished edge splits
    cleanly and a ground one scatters everything back as white. */
fun rimTint(glass: Material): Double = glass.polish * 0.34
fun rimTint(kind: Kind): Double = rimTint(kind.glass)

/** The second hue a cut edge comes back at. Sixty-two degrees off
    the section's own accent, so the money school's edge splits
    green and Deutsch's splits blue, and neither is a colour
    anybody typed: this stays one design system with six sections
    in it rather than a rainbow bolted on. */
const val RIM_SPLIT_DEGREES = 62.0

/** How bright the reflection off the outer face is.

    The glow is light INSIDE the material; this is light coming
    off the front of it, and they must stay distinguishable. It is
    barely there at rest and blooms under a finger, because a
    reflection at full strength on every surface at once is not a
    material, it is a sheen on everything. */
fun specularStrength(glass: Material, a: Double): Double =
    glass.polish * glass.standing * (0.30 + a * 0.55)
fun specularStrength(kind: Kind, a: Double): Double = specularStrength(kind.glass, a)

/* ---------- the tilt, which is the same arithmetic twice ---------- */

/** A lean, as one axis and one angle. */
data class Lean(val x: Double, val y: Double, val degrees: Double)

/** The rotation that leans a surface towards (nx, ny).

    Tipping the near edge down and the far edge up is, for a point
    to the right, a positive rotation about Y, and for a point
    below, a NEGATIVE one about X, because screen y grows
    downwards. Combining those into one axis-angle: the axis is
    perpendicular to the direction in the surface's plane, and the
    angle is how far off centre it is. */
fun leanFor(nx: Double, ny: Double, maxDegrees: Double): Lean? {
    val magnitude = minOf(1.0, kotlin.math.hypot(nx, ny))
    if (magnitude == 0.0) return null
    return Lean(ny / magnitude, nx / magnitude, magnitude * maxDegrees)
}

object Tilt {
    /** The whole effect corner to corner, under a pointer. */
    const val POINTER_DEGREES = 2.6

    /** Half of it in the hand, and for a reason: a pointer tilt
        answers a deliberate movement over one card, and this
        answers the ordinary sway of holding a phone. The same
        angle that reads as a lean under a cursor reads as a
        wobble in the hand. */
    const val HANDSET_DEGREES = 1.4

    /** Degrees of handset travel for the full lean. */
    const val HANDSET_RANGE = 26.0

    /** No sensor reading by then: this device cannot do it, and
        the listener comes off rather than sitting there. */
    const val GIVE_UP_MS = 3000L

    /** Where the handset was when we started IS level. Somebody
        reading in bed holds a phone at sixty degrees and is not
        tilting it, so a lean is measured from the first reading
        rather than from flat. */
    fun normalise(now: Double, rest: Double): Double =
        (now - rest).let { (it / HANDSET_RANGE).coerceIn(-1.0, 1.0) }
}
