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
        "continue", "progress", "pulse", "market", "schools", "tools", "stock", "diet",
    )

    private fun ids(stored: List<String>?) = layoutOf(stored, all).map { it.id }

    @Test fun `nothing stored gives the floor`() {
        assertEquals(BOARD_FLOOR, storedOf(layoutOf(null, all)))
        assertEquals(BOARD_FLOOR, storedOf(layoutOf(emptyList(), all)))
    }

    /** THE BOARDS ALREADY IN ACCOUNTS. `half` and `full` were the
        first two sizes and `home-board` holds them in real rows:
        read for ever, written never. The day this fails is the
        day everybody who arranged a board before the three sizes
        shipped opens an empty page. */
    @Test fun `the first two sizes are read for ever and written never`() {
        assertEquals(
            listOf("pulse:wide", "stock:small"),
            storedOf(layoutOf(listOf("pulse:full", "stock:half"), all)),
        )
        assertTrue(storedOf(layoutOf(listOf("schools:full"), all)).none { "full" in it })
    }

    /** The resize control cycles the kind's own list, in the
        site's order, so the two boards cycle the same way. */
    @Test fun `resize walks the kind's own sizes and wraps`() {
        val kind = WidgetKind(id = "x", sizes = listOf("wide", "small", "tall"))
        assertEquals(WidgetSize.SMALL, kind.other(WidgetSize.WIDE))
        assertEquals(WidgetSize.TALL, kind.other(WidgetSize.SMALL))
        assertEquals(WidgetSize.WIDE, kind.other(WidgetSize.TALL))
        assertNull(WidgetKind(id = "y", sizes = listOf("wide")).other(WidgetSize.WIDE))
        /* And a catalogue still saying the OLD spellings cycles
           too: a phone can be newer than the deploy it reads. */
        val old = WidgetKind(id = "z", sizes = listOf("full", "half"))
        assertEquals(WidgetSize.SMALL, old.other(WidgetSize.WIDE))
    }

    /** The one that matters. A phone on a newer build writes a
        widget this one has never heard of, and the board has to
        come back one card short rather than not at all. */
    @Test fun `a widget this build cannot draw is dropped, not fatal`() {
        assertEquals(
            listOf("pulse", "schools"),
            ids(listOf("pulse:wide", "hologram:wide", "schools:wide")),
        )
    }

    @Test fun `a size this build does not know is dropped`() {
        assertEquals(listOf("schools"), ids(listOf("pulse:enormous", "schools:wide")))
    }

    @Test fun `rubbish is dropped`() {
        assertEquals(emptyList(), ids(listOf("", ":", "pulse", "a:b:c")))
    }

    /** A reader who emptied their board gets an empty board.
        Falling back there would be the page overruling them. */
    @Test fun `a list that parses to nothing stays nothing`() {
        assertTrue(layoutOf(listOf("nothing:wide"), all).isEmpty())
    }

    @Test fun `one of each, and the first wins`() {
        assertEquals(listOf("pulse:wide"), storedOf(layoutOf(listOf("pulse:wide", "pulse:small"), all)))
    }

    @Test fun `the order is the reader's`() {
        assertEquals(
            listOf("tools", "schools", "pulse"),
            ids(listOf("tools:wide", "schools:wide", "pulse:wide")),
        )
    }

    /** `drawable` is what the CALLER can draw, which is not the
        catalogue: that is the whole reason it is an argument. */
    @Test fun `a build that draws two widgets draws two`() {
        assertEquals(
            listOf("pulse:tall", "schools:wide"),
            storedOf(layoutOf(BOARD_FLOOR, setOf("pulse", "schools"))),
        )
    }

    @Test fun `a round trip is the identity`() {
        val board = listOf("market:wide", "stock:small")
        assertEquals(board, storedOf(layoutOf(board, all)))
    }

    /* ---------- the drag ---------- */

    private val three = layoutOf(listOf("pulse:wide", "market:wide", "tools:wide"), all)

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

    /* ---------- the paired rows ---------- */

    private fun row(vararg ids: String) = ids.toList()

    private fun rowsOf(stored: List<String>) =
        pairSmalls(layoutOf(stored, all)).map { r -> r.map { it.id } }

    @Test fun `two consecutive smalls pair and a wide is a row of one`() {
        assertEquals(
            listOf(row("continue"), row("progress", "stock"), row("pulse")),
            rowsOf(listOf("continue:wide", "progress:small", "stock:small", "pulse:tall")),
        )
    }

    /** The order is the reader's: a small does NOT reach past a
        wide to find a partner, because that would reorder the
        board for them. */
    @Test fun `a small never pairs across a wide`() {
        assertEquals(
            listOf(row("progress"), row("continue"), row("stock")),
            rowsOf(listOf("progress:small", "continue:wide", "stock:small")),
        )
    }

    @Test fun `an odd small at the end is a row of one`() {
        assertEquals(
            listOf(row("progress", "stock"), row("diet")),
            rowsOf(listOf("progress:small", "stock:small", "diet:small")),
        )
    }

    @Test fun `every widget appears in the rows exactly once`() {
        val stored = listOf(
            "continue:wide", "progress:small", "stock:small",
            "pulse:tall", "market:tall", "schools:wide",
        )
        assertEquals(
            layoutOf(stored, all).map { it.id },
            pairSmalls(layoutOf(stored, all)).flatten().map { it.id },
        )
    }

    /* ---------- what a kind offers ---------- */

    @Test fun `a kind that offers one size has no other`() {
        val one = WidgetKind(id = "pulse", sizes = listOf("wide"))
        assertEquals(WidgetSize.WIDE, one.added())
        assertNull(one.other(WidgetSize.WIDE))

        val two = WidgetKind(id = "progress", sizes = listOf("wide", "small"))
        assertEquals(WidgetSize.SMALL, two.other(WidgetSize.WIDE))
        assertEquals(WidgetSize.WIDE, two.other(WidgetSize.SMALL))
    }

    /** A catalogue entry from a deploy that says nothing about
        sizes still has to be addable. */
    @Test fun `a kind with no sizes is added at the row's width`() {
        assertEquals(WidgetSize.WIDE, WidgetKind(id = "x").added())
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
        assertEquals("নতুন", kindOf("pulse", mapOf("pulse" to sent), WidgetSize.WIDE).bn)

        val floor = kindOf("pulse", emptyMap(), WidgetSize.WIDE)
        assertEquals(KIND_NAMES.getValue("pulse").first, floor.bn)
        assertEquals(WidgetSize.WIDE, floor.added())

        /* And a kind from a newer site that this build has never
           heard of still gets a name rather than a crash. */
        assertEquals("hologram", kindOf("hologram", emptyMap(), WidgetSize.SMALL).bn)
    }

}
