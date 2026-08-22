package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The material, asserted rather than remembered.

   These are the site's own eight questions where they can be
   asked without a browser. `scripts/check-material.ts` asks them
   of a stylesheet; this asks them of the table the renderer
   reads, and both exist for the same reason: every value in the
   kinds table is plausible on its own, so a retune that breaks
   the IDEA is invisible one number at a time.
   ============================================================ */
class MaterialTest {

    /** The ladder is a ladder: thicker means less polished and
        less clear, because that is what more material does to a
        light. Read down the polish column and the system is
        there in one line.

        The plate and the groove are excluded for the reason the
        site excludes them: their spread is zero, so their depth
        describes a still light and is not on the same scale. */
    @Test
    fun `thicker glass is less polished and less clear`() {
        val following = Kind.entries.filter { it.follows }.sortedBy { it.depth }
        assertEquals(4, following.size, "expected four kinds whose light follows")
        for (i in 1 until following.size) {
            val thinner = following[i - 1]
            val thicker = following[i]
            assertTrue(
                thinner.polish > thicker.polish,
                "$thicker is thicker than $thinner and is not less polished",
            )
            assertTrue(
                thinner.clarity > thicker.clarity,
                "$thicker is thicker than $thinner and is not less clear",
            )
        }
    }

    /** The plate's numbers are placed so it WOULD pass the ladder
        if it were on it, which is the difference between an
        exemption and a hole. It sits between the control and the
        card in thickness and below both in clarity, because it is
        read rather than pressed. */
    @Test
    fun `the plate would sit on the ladder if it were on it`() {
        assertTrue(Kind.PLATE.depth > Kind.CONTROL.depth)
        assertTrue(Kind.PLATE.depth < Kind.CARD.depth)
        assertTrue(Kind.PLATE.clarity < Kind.CARD.clarity)
    }

    /** And the groove's, between the chip and the control: finer
        than a button, a little deeper than a chip, thin enough to
        pass light. What makes a track read as sunk is the ground
        colour it already has, not a low clarity. */
    @Test
    fun `the groove would sit between the chip and the control`() {
        assertTrue(Kind.GROOVE.depth > Kind.CHIP.depth)
        assertTrue(Kind.GROOVE.depth < Kind.CONTROL.depth)
        assertTrue(Kind.GROOVE.polish < Kind.CHIP.polish)
        assertTrue(Kind.GROOVE.polish > Kind.CONTROL.polish)
    }

    /** Nothing that is read follows a finger. A non-zero spread
        IS the membership test on the site, so this is the same
        statement the stylesheet makes with `--glow-w: 0`. */
    @Test
    fun `a plate and a groove carry no following light`() {
        assertEquals(0.0, glowWidth(Kind.PLATE))
        assertEquals(0.0, glowWidth(Kind.GROOVE))
        assertTrue(glowWidth(Kind.CHIP) > 0.0)
        assertTrue(glowWidth(Kind.PANE) > 0.0)
    }

    /** The cap is a physical statement, not a guard. A chip stays
        at 46, a control reaches 101, and a card and a pane both
        settle at 220, which is why the two thickest kinds have
        the same spread and differ in strength instead. */
    @Test
    fun `the spread is capped where a light stops being a light`() {
        assertEquals(46.0, glowWidth(Kind.CHIP), 0.001)
        assertEquals(101.2, glowWidth(Kind.CONTROL), 0.001)
        assertEquals(220.0, glowWidth(Kind.CARD), 0.001)
        assertEquals(220.0, glowWidth(Kind.PANE), 0.001)
        assertTrue(glowStrength(Kind.CARD) > glowStrength(Kind.PANE))
    }

    /** A rough face scatters the same light further. */
    @Test
    fun `a rougher face throws the falloff further out`() {
        assertTrue(glowStop(Kind.PANE) > glowStop(Kind.CARD))
        assertTrue(glowStop(Kind.CARD) > glowStop(Kind.CHIP))
        assertEquals(0.5476, glowStop(Kind.CHIP), 0.0001)
    }

    /** Standing 0 is flat, and the two parts a reader could see
        are gated to nothing by it.

        The catch light was NOT, on the site, for a while: at
        `22% + standing * 30%` a flat row kept 22% of a catch
        light along its bottom, which is a border on an unhovered
        rail item, which is the box again in its last hiding
        place. The rim had the same bug at 26%, and twenty rows in
        a column each closing a full bright box is the cage this
        system has a standing axis to prevent.

        The SEAT keeps a floor of `depth * 2%` that standing does
        not gate, and that is the site's arithmetic rather than an
        oversight here. It is asserted as a floor rather than
        removed, because a port that quietly tidies a number is a
        redesign wearing a port's clothes. What makes it harmless
        is the size: a control at depth 2.2 keeps 4.4% of a source
        that is itself 6% black, which is a quarter of one per
        cent and cannot draw a box. So the assertion is that it
        stays under the threshold, not that it is absent. */
    @Test
    fun `a surface that stands on nothing draws no edge a reader can see`() {
        val flat = Kind.CARD.glass.copy(standing = 0.0)
        val edge = slabEdge(flat)
        assertEquals(0.0, edge.single { it.source == Source.FACE }.alpha, 1e-9,
            "a flush row has no near edge showing and must get no catch light")
        assertEquals(0.0, edge.single { it.source == Source.RIM }.alpha, 1e-9,
            "a rim on a flush row is the cage")
        val seat = edge.single { it.source == Source.UNDER }
        assertTrue(seat.alpha < 0.11, "the seat's ungated floor must stay under a tenth")
        assertTrue(
            slabEdge(Kind.CARD).single { it.source == Source.UNDER }.alpha > seat.alpha * 3,
            "and standing must still be most of what the seat is",
        )
    }

    /** The thickness is at the BOTTOM. A five pixel band across
        the top of a card is not an edge, it is a grey bar, and it
        shipped that way for one commit. */
    @Test
    fun `a slab shows its thickness at the near edge and not the top`() {
        val edge = slabEdge(Kind.CARD)
        val catch = edge[0]
        assertTrue(catch.dy < 0.0, "the catch light must be thrown upward from the bottom edge")
        assertTrue(catch.spread < 0.0, "a positive spread would band the whole box")
        assertTrue(edge.all { it.dy <= 0.0 }, "nothing on a slab's edge hangs off the top")
    }

    /** A groove is the inverse, and that is the whole reason it
        is a kind rather than a row of numbers. */
    @Test
    fun `a groove is lit the other way up`() {
        val edge = grooveEdge(Kind.GROOVE)
        assertEquals(2, edge.size, "a groove has two walls and no rim: it stands on nothing")
        assertTrue(edge[0].dy > 0.0, "the near wall of a cut is in shadow at the TOP")
        assertEquals(Source.UNDER, edge[0].source)
        assertTrue(edge[1].dy < 0.0, "the far wall catches the light along the bottom")
        assertEquals(Source.FACE, edge[1].source)
        assertTrue(edge.none { it.source == Source.RIM })
    }

    /** The rim is soft. A ring with a blur of zero is a drawn
        outline, the same weight in the corners as along the
        sides, which nested inside another surface reads as a box
        inside a box. */
    @Test
    fun `the rim is blurred past its own width so it closes nothing`() {
        val rim = slabEdge(Kind.PANE).single { it.source == Source.RIM }
        assertTrue(rim.blur > rim.spread, "a rim with no blur is an outline somebody drew")
        assertEquals(0.995, rim.spread, 0.001)
        assertEquals(0.0, rim.dx, 1e-9, "the ring does not move; the reflection on it does")
        assertEquals(0.0, rim.dy, 1e-9)
    }

    /** And the reflection that does move is separate, absent at
        rest, and lands on the finger's side: an inset shadow
        pushed right shows on the left inner edge, so the offset
        is away from the touch. */
    @Test
    fun `the glint is absent at rest and lands on the finger's side`() {
        assertEquals(0.0, glintFor(Kind.CARD, 0.8, 0.0, a = 0.0).alpha, 1e-9)
        val lit = glintFor(Kind.CARD, 0.8, -0.5, a = 1.0)
        assertTrue(lit.dx < 0.0, "a touch to the right must offset the glint left")
        assertTrue(lit.dy > 0.0, "a touch above must offset the glint down")
        assertTrue(lit.alpha > 0.0)
        assertEquals(0.0, glintFor(Kind.GROOVE, 0.8, 0.0, a = 1.0).alpha, 1e-9)
    }

    /** A polished edge splits cleanly, a ground one scatters
        everything back as white. */
    @Test
    fun `dispersion follows polish`() {
        assertTrue(rimTint(Kind.CHIP) > rimTint(Kind.PANE))
        assertEquals(62.0, RIM_SPLIT_DEGREES)
    }

    /** The specular is barely there at rest and blooms under a
        finger. A reflection at full strength on every surface at
        once is a sheen on everything, not a material. */
    @Test
    fun `the specular blooms rather than sitting there`() {
        val rest = specularStrength(Kind.CARD, a = 0.0)
        val lit = specularStrength(Kind.CARD, a = 1.0)
        assertTrue(lit > rest * 2, "the specular must be mostly response, not rest state")
        assertEquals(0.0, specularStrength(Kind.GROOVE, a = 1.0), 1e-9)
    }

    /* ---------- the tilt ---------- */

    @Test
    fun `a lean is perpendicular to the direction it leans towards`() {
        val lean = leanFor(1.0, 0.0, Tilt.POINTER_DEGREES)!!
        assertEquals(0.0, lean.x, 1e-9)
        assertEquals(1.0, lean.y, 1e-9)
        assertEquals(Tilt.POINTER_DEGREES, lean.degrees, 1e-9)
    }

    @Test
    fun `dead centre is not a lean`() {
        assertEquals(null, leanFor(0.0, 0.0, Tilt.POINTER_DEGREES))
    }

    /** Corner to corner is one full lean and not one and a half:
        the magnitude is clamped, so a diagonal does not read as
        1.41 times the maximum. */
    @Test
    fun `a corner is a full lean and no more`() {
        val corner = leanFor(1.0, 1.0, Tilt.POINTER_DEGREES)!!
        assertEquals(Tilt.POINTER_DEGREES, corner.degrees, 1e-9)
    }

    /** The hand gets half of what a pointer gets. */
    @Test
    fun `the handset lean is half the pointer lean`() {
        assertTrue(Tilt.HANDSET_DEGREES < Tilt.POINTER_DEGREES / 1.8)
    }

    /** Where the handset was when we started IS level. Somebody
        reading in bed holds a phone at sixty degrees and is not
        tilting it. */
    @Test
    fun `a lean is measured from where the handset started`() {
        assertEquals(0.0, Tilt.normalise(60.0, rest = 60.0), 1e-9)
        assertEquals(0.5, Tilt.normalise(73.0, rest = 60.0), 1e-9)
        assertEquals(1.0, Tilt.normalise(200.0, rest = 60.0), 1e-9)
        assertEquals(-1.0, Tilt.normalise(-200.0, rest = 60.0), 1e-9)
    }

    /** And the whole point of the table: nothing a reader sees is
        typed. Change a kind's four numbers and every derived
        figure moves with them. */
    @Test
    fun `nothing visible is authored`() {
        val thinner = Kind.CARD.glass.copy(depth = 2.0)
        assertEquals(92.0, glowWidth(thinner), 0.001)
        assertTrue(slabEdge(thinner)[0].dy > slabEdge(Kind.CARD)[0].dy)
        assertTrue(slabEdge(thinner).single { it.source == Source.RIM }.spread <
            slabEdge(Kind.CARD).single { it.source == Source.RIM }.spread)

        val rougher = Kind.CHIP.glass.copy(polish = 0.1)
        assertTrue(glowStop(rougher) > glowStop(Kind.CHIP))
        assertTrue(rimTint(rougher) < rimTint(Kind.CHIP))
    }

    /** A material that does not follow has no spread whatever its
        depth, which is the statement `--glow-w: 0` makes and the
        reason it can be the membership test. */
    @Test
    fun `a still light stays still however thick the glass`() {
        assertEquals(0.0, glowWidth(Material(9.0, 0.3, 0.3, 1.0, follows = false)))
    }
}

/* ============================================================
   A checkpoint's number is a storage identity.

   `<lesson id>#<n>` is in real accounts. The site numbers them
   across the WHOLE lesson with one selector, and numbering each
   list from zero instead would point every existing tick at the
   wrong line while looking perfectly reasonable.
   ============================================================ */
class CheckpointNumberTest {

    private fun list(vararg items: String) =
        Block.Checklist(items.map { listOf(Inline.Text(it)) })

    @Test
    fun `numbering runs across the whole lesson and not within a list`() {
        val blocks = listOf(
            Block.Paragraph(listOf(Inline.Text("Before."))),
            list("a", "b", "c"),
            Block.Paragraph(listOf(Inline.Text("Between."))),
            list("d", "e", "f"),
        )
        val bases = checkpointBases(blocks)
        assertEquals(0, bases["1"])
        assertEquals(3, bases["3"], "the second list must not restart at zero")
        assertEquals(6, checkpointCount(blocks))
    }

    @Test
    fun `a checklist inside a callout is counted in place`() {
        val blocks = listOf(
            list("a", "b"),
            Block.Callout(
                kind = CalloutKind.NOTE,
                label = emptyList(),
                body = listOf(list("c", "d")),
            ),
            list("e"),
        )
        val bases = checkpointBases(blocks)
        assertEquals(0, bases["0"])
        assertEquals(2, bases["1.0"], "a nested list is a descendant and counts in document order")
        assertEquals(4, bases["2"])
        assertEquals(5, checkpointCount(blocks))
    }

    @Test
    fun `a body with no checklists has no checkpoints`() {
        val blocks = listOf(Block.Paragraph(listOf(Inline.Text("Just prose."))))
        assertTrue(checkpointBases(blocks).isEmpty())
        assertEquals(0, checkpointCount(blocks))
    }

    /** The id is the site's shape, and the two halves are joined
        by a hash because that is what is in real accounts. */
    @Test
    fun `an id is the lesson and the number`() {
        assertEquals("stufe-1/tag-3#4", checkpointId("stufe-1/tag-3", 4))
        assertEquals("share#0", checkpointId("share", 0))
    }
}
