package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Search, against the site's real answer.

   The fixture is a live `/api/site`, so these assert what a
   reader would actually find rather than what a hand-written
   index would let them find. A fixture kinder than the thing it
   stands in for is not a test.
   ============================================================ */
class SearchTest {

    private val site: SiteManifest = Json { ignoreUnknownKeys = true }.decodeFromString(
        SiteManifest.serializer(),
        requireNotNull(javaClass.getResourceAsStream("/fixtures/site.json"))
            .readBytes().decodeToString(),
    )

    @Test
    fun `nothing typed finds nothing`() {
        assertTrue(search(site, "").isEmpty())
        assertTrue(search(site, "   ").isEmpty())
    }

    @Test
    fun `no manifest is an empty result rather than a crash`() {
        assertTrue(search(null, "money").isEmpty())
    }

    /** A title starting with what was typed beats a title merely
        containing it, which beats a blurb. A reader typing three
        letters is naming a thing. */
    @Test
    fun `a title that starts with the query wins`() {
        val results = search(site, "money")
        assertTrue(results.isNotEmpty())
        assertTrue(
            results.first().title.lowercase().startsWith("money"),
            "the first result for 'money' was ${results.first().title}",
        )
    }

    /** A word inside a title counts, and OUTRANKS a title that
        merely contains the letters somewhere.

        This is why the band exists. A reader typing "check"
        means "Stock check: buy, hold or sell", where the word
        starts after a space; they do not mean a page whose
        blurb happens to say "check". The site's titles carry
        colons, mid dots and brackets, so the test is for a
        separator before the match rather than a split on
        spaces: "Money: টাকা ও শেয়ার" would fail a naive split
        and is exactly the shape most of these titles have. */
    @Test
    fun `a word inside a title outranks a match anywhere else`() {
        val results = search(site, "check")
        val title = results.firstOrNull()
        assertTrue(title != null, "nothing matched 'check'")
        assertTrue(
            "check" in title.title.lowercase(),
            "the best match for 'check' was ${title.title}, which does not contain it",
        )

        /* And the same for a Bangla word after a colon, which is
           the shape of nine titles in this fixture. */
        val bangla = search(site, "টাকা").first()
        assertTrue("টাকা" in bangla.title, "the best match for টাকা was ${bangla.title}")
    }

    @Test
    fun `bangla finds what english finds`() {
        val bangla = search(site, "টাকা")
        assertTrue(bangla.isNotEmpty(), "টাকা found nothing")
        assertTrue(
            bangla.any { it.group == "money" || "/money" in it.url },
            "টাকা did not reach the money school",
        )
    }

    /** Both of a school's names match, and a reader wants ONE
        row for it. */
    @Test
    fun `one row per address`() {
        val results = search(site, "e")
        val urls = results.map { it.url }
        assertEquals(urls.size, urls.toSet().size, "the same address appeared twice")
    }

    /** The glossary is searchable, and the addresses it builds
        are the site's own spelling. `/money/terms/<slug>.html`
        keeps its suffix because the suffix is part of a slug
        there rather than part of a route, and inventing
        `/money/terms/<slug>` would be a dead link. */
    @Test
    fun `a term is found at the address the site actually serves`() {
        val results = search(site, "share")
        val term = results.firstOrNull { it.hint == "Term" }
        assertTrue(term != null, "no term matched 'share'")
        assertTrue(
            term.url.startsWith("/money/terms/") && term.url.endsWith(".html"),
            "a term's address was ${term.url}",
        )
    }

    /** Nothing private is reachable. The endpoint filters before
        sending, so this is checking the contract holds end to
        end rather than checking a filter here. */
    @Test
    fun `nothing private and nothing unlisted is findable`() {
        val everything = ('a'..'z').flatMap { search(site, it.toString(), limit = 500) }
        val urls = everything.map { it.url }.toSet()
        assertTrue(urls.isNotEmpty())
        assertTrue(
            urls.none { it.startsWith("/admin") || it.startsWith("/studio") },
            "an admin address is findable: ${urls.filter { it.startsWith("/admin") || it.startsWith("/studio") }}",
        )
        assertTrue(
            urls.none { "/skills/courses" in it },
            "the private course catalogue is findable",
        )
    }

    /** The limit is a limit, and the best results survive it. */
    @Test
    fun `the limit keeps the best`() {
        val few = search(site, "a", limit = 3)
        val many = search(site, "a", limit = 500)
        assertTrue(few.size <= 3)
        assertEquals(many.take(3).map { it.url }, few.map { it.url })
    }

    /** Grouped the site's way: by the one word it already uses to
        say what a thing is. */
    @Test
    fun `results group by the site's own hint`() {
        val groups = grouped(search(site, "money"))
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.all { (name, rows) -> name.isNotBlank() && rows.isNotEmpty() })
        val flat = groups.flatMap { it.second }
        assertEquals(search(site, "money").size, flat.size, "grouping lost a row")
    }

    /** Case folds both ways. */
    @Test
    fun `case does not matter`() {
        assertEquals(
            search(site, "MONEY").map { it.url },
            search(site, "money").map { it.url },
        )
    }
}
