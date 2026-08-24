package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   A group's own front page is not one of its rows.

   `/skills` on the site filtered `key !== "skills"`, which is the
   right answer written where only that page can read it. This app
   read the same table and drew the card: the Learning tab opened
   with দক্ষতা, no blurb, leading to a copy of the list under it,
   and every check on both sides passed.

   `hub` in `shared/nav.ts` is the fact now, and this is the rule
   that acts on it. A pure function rather than four lines inside
   a composable, because the drawing needs a device and the rule
   does not: what went wrong is arithmetic on a list.
   ============================================================ */
class HubRowsTest {

    private fun item(key: String, hub: Boolean = false) =
        NavItem(label = key, href = "/$key", key = key, hub = hub)

    private fun group(vararg items: NavItem) =
        NavGroup(id = "learn", label = "শেখা · Learning", items = items.toList())

    @Test fun `the hub is not one of the rows`() {
        val g = group(item("skills", hub = true), item("money"), item("deutsch"))
        assertEquals(listOf("money", "deutsch"), rowsOf(g).map { it.key })
        assertEquals("skills", g.hubItem()?.key)
    }

    /** The reading group is one entry and that entry is its hub.
        Filtering it leaves a tab with nothing on it, which is
        worse than the repetition it was meant to fix. */
    @Test fun `a group that is only its hub keeps it`() {
        val g = group(item("insights", hub = true))
        assertEquals(listOf("insights"), rowsOf(g).map { it.key })
    }

    /** Before the deployment that carries the flag, every item
        arrives with `hub = false`, and the tab must look exactly
        as it did rather than emptying. */
    @Test fun `a manifest with no flag on it loses nothing`() {
        val g = group(item("skills"), item("money"))
        assertEquals(listOf("skills", "money"), rowsOf(g).map { it.key })
        assertNull(g.hubItem())
    }

    @Test fun `two hubs in one group would still leave the rest`() {
        val g = group(item("a", hub = true), item("b", hub = true), item("c"))
        assertEquals(listOf("c"), rowsOf(g).map { it.key })
        assertTrue(g.hubItem()?.key == "a", "the first is the one whose head is drawn")
    }
}
