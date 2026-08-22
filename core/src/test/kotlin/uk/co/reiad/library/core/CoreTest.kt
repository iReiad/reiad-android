package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/* ============================================================
   The core, against the site's real answers.

   Every fixture under `src/test/resources/fixtures` was pulled
   from the live API, not written by hand, for the reason the
   site's own tests give: a fixture kinder than the thing it
   stands in for is not a test. `/money/basics-1/share` is in
   there specifically because it carries a `<b>`, which the
   server's allowlist does not have.
   ============================================================ */

private fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name.json")) {
        "missing fixture $name.json"
    }.readBytes().decodeToString()

class StorageKeyTest {

    /* The whole point of naming all twenty-one: a rename should
       fail here rather than lose somebody's ticks in the field. */
    @Test
    fun `every synced key is spelled the way real accounts spell it`() {
        val expected = setOf(
            "learn-read", "learn-last", "learn-checks",
            "deutsch-read", "deutsch-days", "deutsch-last", "deutsch-tag", "deutsch-checks",
            "english-read", "english-days", "english-last", "english-day", "english-checks",
            "quran-done", "quran-last", "quran-checks",
            "courses-read", "courses-last", "courses-answers",
            "days-active", "reader-prefs",
        )
        assertEquals(expected, SyncKeys.ALL.keys)
    }

    @Test
    fun `the money school files under learn, because it moved and its keys did not`() {
        assertEquals("learn-read", ProgressKeys.read(School.MONEY))
        assertEquals("learn-last", ProgressKeys.last(School.MONEY))
        assertEquals("learn-checks", ProgressKeys.checks(School.MONEY))
    }

    @Test
    fun `quran is done rather than read, and english counts in day not tag`() {
        assertEquals("quran-done", ProgressKeys.read(School.QURAN))
        assertEquals("english-day", ProgressKeys.dayCount(School.ENGLISH))
        assertEquals("deutsch-tag", ProgressKeys.dayCount(School.DEUTSCH))
    }

    @Test
    fun `only the two schools with a practice book have day keys`() {
        assertEquals(null, ProgressKeys.days(School.MONEY))
        assertEquals(null, ProgressKeys.days(School.QURAN))
        assertNotNull(ProgressKeys.days(School.DEUTSCH))
        assertNotNull(ProgressKeys.days(School.ENGLISH))
    }

    @Test
    fun `basics-1 ticks under a bare slug and everything else is stage over lesson`() {
        assertEquals("share", lessonId("basics-1", "share"))
        assertEquals("start/papers", lessonId("start", "papers"))
        assertEquals("basics-2/sectors", lessonId("basics-2", "sectors"))
    }

    @Test
    fun `a day id keeps each school's own shape`() {
        assertEquals("stufe-1/tag-3", dayId(School.DEUTSCH, "stufe-1", 3))
        assertEquals("term-1/day-3", dayId(School.ENGLISH, "term-1", 3))
    }

    @Test
    fun `a checkpoint is a position inside a lesson`() {
        assertEquals("share#2", checkpointId("share", 2))
    }
}

class SyncRulesTest {

    private fun ids(vararg s: String) = s.toSet()

    @Test
    fun `a tick made here survives an exchange`() {
        val merged = SyncRules.reconcileSet(
            was = ids("a"),
            mine = ids("a", "b"),
            theirs = ids("a"),
        )
        assertEquals(ids("a", "b"), merged)
    }

    @Test
    fun `a tick made on the other device arrives`() {
        val merged = SyncRules.reconcileSet(
            was = ids("a"),
            mine = ids("a"),
            theirs = ids("a", "c"),
        )
        assertEquals(ids("a", "c"), merged)
    }

    @Test
    fun `an untick here removes it from the account rather than coming back`() {
        val merged = SyncRules.reconcileSet(
            was = ids("a", "b"),
            mine = ids("a"),
            theirs = ids("a", "b"),
        )
        assertEquals(ids("a"), merged)
    }

    /* The bug the base snapshot exists for: without `was`, this
       is indistinguishable from the case above and one of them
       has to be wrong. */
    @Test
    fun `a tick the other device removed does not come back`() {
        val merged = SyncRules.reconcileSet(
            was = ids("a", "b"),
            mine = ids("a", "b"),
            theirs = ids("a"),
        )
        assertEquals(ids("a"), merged)
    }

    @Test
    fun `a reset empties the account, with no special case for it`() {
        val merged = SyncRules.reconcileSet(
            was = ids("a", "b", "c"),
            mine = emptySet(),
            theirs = ids("a", "b", "c"),
        )
        assertEquals(emptySet(), merged)
    }

    @Test
    fun `the newer bookmark wins`() {
        val mine = StoredValue.Mark(ts = 100, json = "here")
        val theirs = StoredValue.Mark(ts = 200, json = "there")
        assertEquals(theirs, SyncRules.reconcileMark(mine, theirs))
        assertEquals(mine, SyncRules.reconcileMark(mine, StoredValue.Mark(50, "old")))
    }

    @Test
    fun `a missing bookmark on either side is not a conflict`() {
        val mine = StoredValue.Mark(ts = 100, json = "here")
        assertEquals(mine, SyncRules.reconcileMark(mine, null))
        assertEquals(mine, SyncRules.reconcileMark(null, mine))
        assertEquals(null, SyncRules.reconcileMark(null, null))
    }

    @Test
    fun `a count climbs to the larger, but a device that went backwards is a reset`() {
        assertEquals(9L, SyncRules.reconcileCount(was = 5, mine = 9, theirs = 5))
        assertEquals(9L, SyncRules.reconcileCount(was = 5, mine = 5, theirs = 9))
        assertEquals(0L, SyncRules.reconcileCount(was = 9, mine = 0, theirs = 9))
    }

    @Test
    fun `signing in adopts the account and forgets what the account lacks`() {
        val remote = mapOf<String, StoredValue>(
            "learn-read" to StoredValue.Ids(ids("share")),
        )
        val result = SyncRules.adopt(remote)
        assertEquals(remote, result.write)
        assertContains(result.forget, "quran-done")
        assertContains(result.forget, "days-active")
        assertTrue("learn-read" !in result.forget)
    }
}

class LadderTest {

    private val ladder: LadderResponse =
        ReiadJson.decodeFromString(LadderResponse.serializer(), fixture("money"))

    @Test
    fun `the money ladder parses out of what the site actually sent`() {
        assertTrue(ladder.ok)
        assertTrue(ladder.stages.size >= 8, "expected the eight money stages, got ${ladder.stages.size}")
        assertEquals("start", ladder.stages.first().slug)
        assertTrue(ladder.counts.total > 0)
    }

    @Test
    fun `every stage has lessons, whichever noun the school files them under`() {
        for (stage in ladder.stages) {
            assertTrue(stage.lessons.isNotEmpty(), "stage ${stage.slug} came back with no lessons")
        }
    }

    @Test
    fun `basics-1 keeps the address its pages were published at`() {
        val basics = ladder.stages.first { it.slug == "basics-1" }
        assertEquals("/money/terms/", basics.base)
        assertEquals("/money/terms/share.html", lessonUrl("money", basics, "share"))
    }

    @Test
    fun `an ordinary stage gets the ordinary address`() {
        val start = ladder.stages.first { it.slug == "start" }
        assertEquals(null, start.base)
        assertEquals("/money/start/papers.html", lessonUrl("money", start, "papers"))
        assertEquals("/money/start", stageUrl("money", "start"))
    }

    /* This file said Int here and the site sends a boolean. The
       count agreeing with the flag is what proves the type is the
       site's rather than a guess that happens to parse. */
    @Test
    fun `the written flag agrees with the count the site derives`() {
        val all = ladder.stages.flatMap { it.lessons }
        assertTrue(all.any { it.isWritten }, "no lesson came back written")
        assertEquals(ladder.counts.written, all.count { it.isWritten })
    }

    @Test
    fun `the site manifest parses, and carries the counts it derives`() {
        val site = ReiadJson.decodeFromString(SiteManifest.serializer(), fixture("site"))
        assertTrue(site.ok)
        assertTrue(site.nav.isNotEmpty())
        assertEquals(4, site.ladders.size)
        assertEquals(setOf("money", "deutsch", "quran", "english"), site.ladders.map { it.key }.toSet())
        assertTrue(site.counts.getValue("ratios") > 0)
        assertTrue(site.accents.containsKey("money"))
    }

    @Test
    fun `the articles list parses`() {
        val articles = ReiadJson.decodeFromString(ArticlesResponse.serializer(), fixture("articles"))
        assertTrue(articles.ok)
        assertTrue(articles.articles.isNotEmpty())
        assertTrue(articles.articles.first().slug.isNotEmpty())
    }
}

class BodyParserTest {

    private fun lesson(name: String): LessonPage =
        checkNotNull(
            ReiadJson.decodeFromString(LessonResponse.serializer(), fixture(name)).lesson
        )

    @Test
    fun `a real lesson body becomes blocks`() {
        val body = BodyParser.parse(lesson("lesson-share").body)
        assertTrue(body.blocks.isNotEmpty(), "the body parsed to nothing")
        assertTrue(body.blocks.any { it is Block.Paragraph })
    }

    /* The measurement this parser was written around. */
    @Test
    fun `a b tag the allowlist does not have keeps its words and reads as strong`() {
        val body = BodyParser.parse("<p>দাম <b>বাড়লে</b> লাভ</p>")
        val paragraph = body.blocks.single() as Block.Paragraph
        assertTrue(
            paragraph.inlines.any { it is Inline.Strong },
            "a <b> should read as strong, not vanish",
        )
        assertEquals("দাম বাড়লে লাভ", paragraph.inlines.text())
    }

    @Test
    fun `the other synonyms the editor applies are applied here too`() {
        assertTrue(
            (BodyParser.parse("<p><i>x</i></p>").blocks.single() as Block.Paragraph)
                .inlines.any { it is Inline.Emphasis }
        )
        val heading = BodyParser.parse("<h4>x</h4>").blocks.single() as Block.Heading
        assertEquals(3, heading.level)
        val big = BodyParser.parse("<h1>x</h1>").blocks.single() as Block.Heading
        assertEquals(2, big.level)
    }

    @Test
    fun `a tag nothing knows loses the tag and keeps the words`() {
        val body = BodyParser.parse("<p>before <weird>middle</weird> after</p>")
        val paragraph = body.blocks.single() as Block.Paragraph
        assertEquals("before middle after", paragraph.inlines.text())
        assertContains(body.unknown, "weird")
    }

    @Test
    fun `a term link keeps its relative address, because only the stage can resolve it`() {
        val body = BodyParser.parse(lesson("lesson-share").body)
        val links = body.blocks.filterIsInstance<Block.Paragraph>()
            .flatMap { it.inlines }
            .filterIsInstance<Inline.Link>()
        assertTrue(links.isNotEmpty(), "the share lesson should link to the glossary")
        val term = links.first { it.isTerm }
        assertTrue(term.href.isNotEmpty())
        assertTrue(
            !term.href.startsWith("/") && !term.href.startsWith("http"),
            "expected a relative term href, got ${term.href}",
        )
    }

    @Test
    fun `a relative term href resolves against the stage it was written in`() {
        val ladder: LadderResponse =
            ReiadJson.decodeFromString(LadderResponse.serializer(), fixture("money"))
        val basics = ladder.stages.first { it.slug == "basics-1" }
        assertEquals("/money/terms/dividend.html", resolveHref("dividend.html", "money", basics))
        assertEquals("/money/basics-2", resolveHref("/money/basics-2", "money", basics))
        assertEquals("https://x.test/a", resolveHref("https://x.test/a", "money", basics))
    }

    @Test
    fun `the article blocks each become their own kind`() {
        val html = """
            <h2>Heading</h2>
            <div class="at-a-glance"><p class="at-a-glance-label">In short</p><ul><li>one</li></ul></div>
            <div class="side-note"><p class="side-note-label">Aside</p><p>text</p></div>
            <ul class="checklist"><li>first</li><li>second</li></ul>
            <ol class="step-list"><li>step</li></ol>
            <div class="note"><p>careful</p></div>
            <div class="ex"><p>worked</p></div>
            <blockquote>quoted</blockquote>
            <hr>
        """.trimIndent()
        val kinds = BodyParser.parse(html).blocks
        assertTrue(kinds.any { it is Block.Heading })
        assertTrue(kinds.any { it is Block.Callout && it.kind == CalloutKind.AT_A_GLANCE })
        assertTrue(kinds.any { it is Block.Callout && it.kind == CalloutKind.SIDE_NOTE })
        assertTrue(kinds.any { it is Block.Callout && it.kind == CalloutKind.NOTE })
        assertTrue(kinds.any { it is Block.Callout && it.kind == CalloutKind.EXAMPLE })
        assertTrue(kinds.any { it is Block.Checklist })
        assertTrue(kinds.any { it is Block.Steps })
        assertTrue(kinds.any { it is Block.Quote })
        assertTrue(kinds.any { it is Block.Rule })
    }

    @Test
    fun `a checklist keeps its items in order, because a checkpoint id is a position`() {
        val body = BodyParser.parse("""<ul class="checklist"><li>a</li><li>b</li><li>c</li></ul>""")
        val list = body.blocks.single() as Block.Checklist
        assertEquals(listOf("a", "b", "c"), list.items.map { it.text() })
    }

    @Test
    fun `a scrolling table keeps its head and rows`() {
        val html = """
            <div class="table-scroll"><table>
              <thead><tr><th>Year</th><th>Return</th></tr></thead>
              <tbody><tr><td>2024</td><td>7%</td></tr><tr><td>2025</td><td>9%</td></tr></tbody>
            </table></div>
        """.trimIndent()
        val table = BodyParser.parse(html).blocks.single() as Block.Table
        assertTrue(table.scrolls)
        assertEquals(listOf("Year", "Return"), table.head.map { it.text() })
        assertEquals(2, table.rows.size)
        assertEquals(listOf("2025", "9%"), table.rows[1].map { it.text() })
    }

    @Test
    fun `a photo keeps the crop classes the share card also uses`() {
        val html = """
            <figure class="wide frame-wide focus-top">
              <img src="/media/x/abc.webp" alt="A chart">
              <figcaption>What it shows</figcaption>
            </figure>
        """.trimIndent()
        val photo = BodyParser.parse(html).blocks.single() as Block.Photo
        assertEquals("/media/x/abc.webp", photo.src)
        assertEquals("A chart", photo.alt)
        assertEquals("What it shows", photo.caption.text())
        assertTrue("frame-wide" in photo.classes && "focus-top" in photo.classes)
    }

    @Test
    fun `a stray close tag does not unwind the document`() {
        val body = BodyParser.parse("<p>one</p></div><p>two</p>")
        assertEquals(2, body.blocks.count { it is Block.Paragraph })
    }

    @Test
    fun `an unclosed tag still yields its words`() {
        val body = BodyParser.parse("<p>one<p>two")
        assertTrue(body.blocks.text().contains("one"))
        assertTrue(body.blocks.text().contains("two"))
    }

    @Test
    fun `entities come back as characters`() {
        val body = BodyParser.parse("<p>Tom &amp; Jerry &lt;3 &#2547;100</p>")
        assertEquals("Tom & Jerry <3 ৳100", (body.blocks.single() as Block.Paragraph).inlines.text())
    }

    @Test
    fun `both fixture lessons parse with nothing unknown left over`() {
        for (name in listOf("lesson-share", "lesson-papers")) {
            val body = BodyParser.parse(lesson(name).body)
            assertTrue(body.blocks.isNotEmpty(), "$name parsed to nothing")
            assertEquals(
                emptyList(), body.unknown,
                "$name held a shape the parser does not know: ${body.unknown}",
            )
        }
    }
}

/** Every word of a parsed body, for the tests that only care
    that nothing was dropped. */
private fun List<Block>.text(): String = joinToString(" ") { block ->
    when (block) {
        is Block.Paragraph -> block.inlines.text()
        is Block.Heading -> block.inlines.text()
        is Block.Quote -> block.inlines.text()
        is Block.Bullets -> block.items.joinToString(" ") { it.text() }
        is Block.Numbers -> block.items.joinToString(" ") { it.text() }
        is Block.Steps -> block.items.joinToString(" ") { it.text() }
        is Block.Checklist -> block.items.joinToString(" ") { it.text() }
        is Block.Callout -> block.body.text()
        is Block.KeyFigures -> block.figures.joinToString(" ") { it.value.text() + " " + it.caption.text() }
        is Block.Photo -> block.caption.text()
        is Block.Table -> (block.head + block.rows.flatten()).joinToString(" ") { it.text() }
        is Block.Unknown -> block.inlines.text()
        Block.Rule -> ""
    }
}
