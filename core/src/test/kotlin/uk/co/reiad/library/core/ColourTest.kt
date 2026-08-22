package uk.co.reiad.library.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The colour conversion, against the site's own implementation.

   `scripts/check-contrast.ts` runs this same pipeline in
   TypeScript over 126 pairs and will print every one:

       node scripts/check-contrast.ts --table

   The numbers below are what it prints. Two independent
   implementations of the same maths agreeing this closely is the
   strongest statement available here.

   The tolerance is 0.03 of a ratio point, which is the precision
   the reference PRINTS at: two decimals is already plus or minus
   0.005 before either side rounds, and clamping happens in a
   different order in each. Tighter would be measuring the
   printing; looser would accept a conversion that is subtly
   wrong.

   THE ORACLE THAT WAS WRONG. These were first written against
   the four ratios in the stylesheet's own token block, which say
   16.2, 7.7, 8.3 and 4.9 and are labelled as measured in a real
   browser. Six tests failed. The conversion was right and the
   COMMENT was stale: the true figures are 15.88, 7.23, 7.18 and
   7.08. `check-contrast.ts` was written because that comment
   went out of date and nothing re-measured it, which its header
   says at length, and this file believed it anyway.
   ============================================================ */

private const val TOLERANCE = 0.03

private fun assertRatio(name: String, expected: Double, actual: Double) {
    assertTrue(
        abs(expected - actual) <= TOLERANCE,
        "$name: check-contrast prints ${expected}:1, this computes " +
            "${"%.2f".format(actual)}:1",
    )
}

class ColourTest {

    private val light = Surfaces.light(Accents.GREEN)
    private val dark = Surfaces.dark(Accents.GREEN)

    /* Each of these is a line of `check-contrast.ts --table`. */

    @Test
    fun `a heading on a card is what check-contrast prints`() {
        assertRatio("heading on a card, light", 15.88, contrastOf(light.ink, light.panel))
        assertRatio("heading on a card, dark", 10.82, contrastOf(dark.ink, dark.panel))
    }

    @Test
    fun `secondary text on the page is what check-contrast prints`() {
        assertRatio("secondary on the page, light", 7.23, contrastOf(light.inkSoft, light.paper))
        assertRatio("secondary on the page, dark", 7.27, contrastOf(dark.inkSoft, dark.paper))
    }

    @Test
    fun `a link on a card is what check-contrast prints`() {
        assertRatio("green on a card, light", 7.18, contrastOf(Accents.GREEN.light, light.panel))
        assertRatio("green on a card, dark", 5.52, contrastOf(Accents.GREEN.dark, dark.panel))
    }

    @Test
    fun `a gold label on the page is what check-contrast prints`() {
        assertRatio("gold on the page, light", 7.08, contrastOf(Accents.GOLD.light, Surfaces.light(Accents.GOLD).paper))
        assertRatio("gold on the page, dark", 7.10, contrastOf(Accents.GOLD.dark, Surfaces.dark(Accents.GOLD).paper))
    }

    @Test
    fun `the tinted panel lands per accent, as check-contrast measures it`() {
        assertRatio("heading on a card in teal, light", 15.87, contrastOf(Surfaces.light(Accents.TEAL).ink, Surfaces.light(Accents.TEAL).panel))
        assertRatio("heading on a card in blue, dark", 10.89, contrastOf(Surfaces.dark(Accents.BLUE).ink, Surfaces.dark(Accents.BLUE).panel))
        assertRatio("heading on a card in plum, light", 15.86, contrastOf(Surfaces.light(Accents.PLUM).ink, Surfaces.light(Accents.PLUM).panel))
    }

    /* The thresholds the site enforces, applied to every accent
       rather than to the two that were measured. */

    @Test
    fun `every accent clears 4-5 to 1 as text, in both modes`() {
        val accents = mapOf(
            "green" to Accents.GREEN, "teal" to Accents.TEAL, "blue" to Accents.BLUE,
            "violet" to Accents.VIOLET, "plum" to Accents.PLUM, "rose" to Accents.ROSE,
            "gold" to Accents.GOLD,
        )
        for ((name, accent) in accents) {
            for (isDark in listOf(false, true)) {
                val surfaces = Surfaces.of(accent, isDark)
                val colour = if (isDark) accent.dark else accent.light
                for ((groundName, ground) in listOf(
                    "paper" to surfaces.paper,
                    "panel" to surfaces.panel,
                    "sunk" to surfaces.paperSunk,
                )) {
                    val ratio = contrastOf(colour, ground)
                    assertTrue(
                        ratio >= 4.5,
                        "$name on $groundName (${if (isDark) "dark" else "light"}) " +
                            "is only ${"%.2f".format(ratio)}:1",
                    )
                }
            }
        }
    }

    @Test
    fun `ink clears 4-5 to 1 on every ground of every section`() {
        for (accent in Accents.BY_KEY.values.distinct()) {
            for (isDark in listOf(false, true)) {
                val s = Surfaces.of(accent, isDark)
                for (ground in listOf(s.paper, s.panel, s.paperSunk)) {
                    assertTrue(contrastOf(s.ink, ground) >= 4.5)
                    assertTrue(contrastOf(s.inkSoft, ground) >= 4.5)
                }
            }
        }
    }

    /* The rule check-surfaces.ts holds on the site: a panel sits
       ABOVE the page and a sunk ground BELOW it, in both modes. */

    @Test
    fun `a panel is lighter than the page and a sunk ground is darker, in light mode`() {
        assertTrue(luminanceOf(light.panel) > luminanceOf(light.paper))
        assertTrue(luminanceOf(light.paperSunk) < luminanceOf(light.paper))
    }

    @Test
    fun `and the same relation holds in dark mode, where lighter means nearer`() {
        assertTrue(luminanceOf(dark.panel) > luminanceOf(dark.paper))
        assertTrue(luminanceOf(dark.paperSunk) < luminanceOf(dark.paper))
    }

    /* The conversion itself, at the ends where it is easiest to
       be wrong. */

    @Test
    fun `white and black come out white and black`() {
        assertEquals(Rgb(255, 255, 255), Oklch(1.0, 0.0, 0.0).toRgb())
        assertEquals(Rgb(0, 0, 0), Oklch(0.0, 0.0, 0.0).toRgb())
    }

    @Test
    fun `an out of gamut colour is clamped rather than wrapped`() {
        val rgb = Oklch(0.65, 0.40, 150.0).toRgb()
        assertTrue(rgb.r in 0..255 && rgb.g in 0..255 && rgb.b in 0..255)
    }

    @Test
    fun `the alpha channel is opaque and the packing is ARGB`() {
        val argb = Oklch(0.0, 0.0, 0.0).toArgb()
        assertEquals(0xFF000000.toInt(), argb)
        assertEquals(0xFFFFFFFF.toInt(), Oklch(1.0, 0.0, 0.0).toArgb())
    }

    @Test
    fun `mixing none of a colour leaves the ground alone`() {
        val ground = Oklch(0.98, 0.008, Hue.PAPER)
        val mixed = ground.mix(Accents.GREEN.light, 0.0)
        assertEquals(ground.toRgb(), mixed.toRgb())
    }

    @Test
    fun `mixing all of a colour gives that colour`() {
        val ground = Oklch(0.98, 0.008, Hue.PAPER)
        val mixed = ground.mix(Accents.GREEN.light, 1.0)
        assertEquals(Accents.GREEN.light.toRgb(), mixed.toRgb())
    }

    @Test
    fun `a german page's paper really is faintly blue next to the money school's`() {
        val money = Surfaces.light(Accents.GREEN).paper.toRgb()
        val deutsch = Surfaces.light(Accents.BLUE).paper.toRgb()
        assertTrue(deutsch.b > money.b, "the blue school's paper should carry more blue")
        assertTrue(abs(deutsch.r - money.r) < 12, "and it should still read as paper, not as blue")
    }

    @Test
    fun `an accent token from the api resolves to the same colour`() {
        assertEquals(Accents.BLUE, Accents.byToken("var(--blue)"))
        assertEquals(Accents.GOLD, Accents.byToken("--gold"))
        assertEquals(null, Accents.byToken("var(--nonsense)"))
    }

    @Test
    fun `the ladders and the scales are the site's own`() {
        assertEquals(listOf(5, 12, 18, 24), listOf(Radius.XS, Radius.SM, Radius.CARD, Radius.LG))
        assertEquals(9, TypeScale.STEPS.size)
        assertEquals(1.24, TypeScale.step(8))
        assertEquals(44, Space.TAP)
        assertEquals(190, Motion.IN_MS)
        assertTrue(Motion.OUT_MS > Motion.IN_MS, "the light goes out slower than it comes up")
    }
}
