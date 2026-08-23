package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   Reading a board written by a different version.

   The catalogue is data and the drawing is code, so the site and
   this app will be at different versions for as long as there is
   an app: a phone installed in March reads a catalogue deployed
   in August, and the site reads a board a newer phone wrote.

   Every case here is one of those crossings. None is about how a
   widget LOOKS, because none can be: what goes wrong across a
   version boundary is a list being parsed.

   `scripts/widgets.test.ts` is the same cases on the site, and
   they are the same cases on purpose. This is one piece of
   arithmetic written twice, which is what the two files together
   are for.
   ============================================================ */
class BoardTest {

    private val all = setOf(
        "continue", "progress", "pulse", "market", "schools", "tools", "stock",
    )

    private fun ids(stored: List<String>?) = layoutOf(stored, all).map { it.id }

    @Test fun `nothing stored gives the floor`() {
        assertEquals(BOARD_FLOOR, storedOf(layoutOf(null, all)))
        assertEquals(BOARD_FLOOR, storedOf(layoutOf(emptyList(), all)))
    }

    /** The one that matters. A phone on a newer build writes a
        widget this one has never heard of, and the board has to
        come back one card short rather than not at all. */
    @Test fun `a widget this build cannot draw is dropped, not fatal`() {
        assertEquals(
            listOf("pulse", "schools"),
            ids(listOf("pulse:full", "hologram:full", "schools:full")),
        )
    }

    @Test fun `a size this build does not know is dropped`() {
        assertEquals(listOf("schools"), ids(listOf("pulse:enormous", "schools:full")))
    }

    @Test fun `rubbish is dropped`() {
        assertEquals(emptyList(), ids(listOf("", ":", "pulse", "a:b:c")))
    }

    /** A reader who emptied their board gets an empty board.
        Falling back there would be the page overruling them. */
    @Test fun `a list that parses to nothing stays nothing`() {
        assertTrue(layoutOf(listOf("nothing:full"), all).isEmpty())
    }

    @Test fun `one of each, and the first wins`() {
        assertEquals(listOf("pulse:full"), storedOf(layoutOf(listOf("pulse:full", "pulse:half"), all)))
    }

    @Test fun `the order is the reader's`() {
        assertEquals(
            listOf("tools", "schools", "pulse"),
            ids(listOf("tools:full", "schools:full", "pulse:full")),
        )
    }

    /** `drawable` is what the CALLER can draw, which is not the
        catalogue: that is the whole reason it is an argument. */
    @Test fun `a build that draws two widgets draws two`() {
        assertEquals(
            listOf("pulse:full", "schools:full"),
            storedOf(layoutOf(BOARD_FLOOR, setOf("pulse", "schools"))),
        )
    }

    @Test fun `a round trip is the identity`() {
        val board = listOf("market:full", "stock:half")
        assertEquals(board, storedOf(layoutOf(board, all)))
    }

    /* ---------- the drag ---------- */

    private val three = layoutOf(listOf("pulse:full", "market:full", "tools:full"), all)

    @Test fun `a reorder is right at both ends`() {
        assertEquals(listOf("market", "pulse", "tools"), moved(three, 0, 1).map { it.id })
        assertEquals(listOf("tools", "pulse", "market"), moved(three, 2, 0).map { it.id })
        assertEquals(listOf("pulse", "tools", "market"), moved(three, 1, 2).map { it.id })
    }

    @Test fun `a reorder off the end clamps rather than throwing`() {
        assertEquals(listOf("market", "tools", "pulse"), moved(three, 0, 9).map { it.id })
        assertEquals(three.map { it.id }, moved(three, 9, 0).map { it.id })
        assertEquals(three.map { it.id }, moved(three, 1, 1).map { it.id })
    }

    /* ---------- what a kind offers ---------- */

    @Test fun `a kind that offers one size has no other`() {
        val one = WidgetKind(id = "pulse", sizes = listOf("full"))
        assertEquals(WidgetSize.FULL, one.added())
        assertNull(one.other(WidgetSize.FULL))

        val two = WidgetKind(id = "progress", sizes = listOf("full", "half"))
        assertEquals(WidgetSize.HALF, two.other(WidgetSize.FULL))
        assertEquals(WidgetSize.FULL, two.other(WidgetSize.HALF))
    }

    /** A catalogue entry from a deploy that says nothing about
        sizes still has to be addable. */
    @Test fun `a kind with no sizes is added full width`() {
        assertEquals(WidgetSize.FULL, WidgetKind(id = "x").added())
    }
    /* ---------- the names before the catalogue ---------- */

    /** A floor that named a widget this build cannot draw would
        be a name nobody ever sees, quietly wrong for ever. */
    @Test fun `every name in the floor is a kind the floor can hold`() {
        val ids = BOARD_FLOOR.map { it.substringBefore(":") }
        for (id in ids) {
            assertTrue(id in KIND_NAMES, "$id is on the default board and has no name")
        }
        for ((id, pair) in KIND_NAMES) {
            assertTrue(pair.first.isNotBlank(), "$id has no Bangla name")
            assertTrue(pair.second.isNotBlank(), "$id has no English name")
        }
    }

    /** The site's own entry wins the moment it arrives, and the
        floor answers until then. Falling back to the ID is what
        put `continue` and `pulse` down the side of a Bangla front
        page. */
    @Test fun `the catalogue wins and the floor answers`() {
        val sent = WidgetKind(id = "pulse", bn = "নতুন", en = "New", sizes = listOf("full"))
        assertEquals("নতুন", kindOf("pulse", mapOf("pulse" to sent), WidgetSize.FULL).bn)

        val floor = kindOf("pulse", emptyMap(), WidgetSize.FULL)
        assertEquals(KIND_NAMES.getValue("pulse").first, floor.bn)
        assertEquals(WidgetSize.FULL, floor.added())

        /* And a kind from a newer site that this build has never
           heard of still gets a name rather than a crash. */
        assertEquals("hologram", kindOf("hologram", emptyMap(), WidgetSize.HALF).bn)
    }

}
