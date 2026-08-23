package uk.co.reiad.library

import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.core.SiteManifest

/* ============================================================
   Every row of the menu goes somewhere.

   The drawer's own handler read one kind of destination, a
   school, and silently ignored the rest. Fourteen rows did
   nothing at all when pressed: the account, the stock check, the
   calculators, the live portfolio, the routine and every reading
   hub. The screens all rendered perfectly, every check passed,
   and the report that came back was "account doesn't open".

   Nothing could see it because it is not a rendering fault and
   not an arithmetic one: it is a `when` with no else, one level
   away from the screen it fails to reach.
   ============================================================ */
class NavReachTest {
    private val site: SiteManifest = fixture("site.json", SiteManifest.serializer())

    @Test fun everyItemGoesSomewhere() {
        val items = site.nav.flatMap { it.items }
        assertTrue(items.size > 10, "the fixture should hold a real menu")
        for (item in items) {
            /* Either this app draws it, or it opens on the site.
               A row that is neither is a row a reader presses
               twice and then stops trusting. */
            val here = opensHere(site, item)
            val away = item.href.isNotBlank()
            assertTrue(here || away, "${item.key ?: item.label} goes nowhere")
        }
    }

    @Test fun theAccountOpensInTheApp() {
        val account = site.nav.flatMap { it.items }.first { it.key == "account" }
        assertTrue(opensHere(site, account), "the account is a screen in this app")
    }

    /** And every school does, which is what the drawer DID
        handle: the regression was everything else. */
    @Test fun everySchoolOpensInTheApp() {
        for (school in site.ladders) {
            val item = site.nav.flatMap { it.items }.firstOrNull { it.key == school.key }
            assertTrue(item != null, "${school.key} is missing from the menu")
            assertTrue(opensHere(site, item), "${school.key} does not open in the app")
        }
    }
}
