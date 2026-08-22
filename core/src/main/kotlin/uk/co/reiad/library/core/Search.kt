package uk.co.reiad.library.core

/* ============================================================
   Finding something, off what the site already sent.

   The palette on the site searches `PAGES` in `shared/content.ts`,
   and `/api/site` sends that whole list as `pages`. So this
   searches the manifest rather than a copy: a page added to the
   site tomorrow is findable here with no app release, and a page
   marked private never arrives in the first place because the
   endpoint filters it before sending.

   **It works with no network, and that is the point of building
   it this way.** The manifest is cached on every fetch, so the
   index is whatever the app last saw rather than a request that
   fails. A search box that needs a connection is a search box
   that is missing exactly when a reader is on a bus.

   ---- what is searched ----

   Four sources, all from the one manifest: the pages, the four
   ladders' school names, the tools, and the glossary's terms. A
   lesson's own title is NOT here, because the manifest does not
   carry lesson titles and asking the network for them would undo
   the paragraph above. The ladders are cached per school, and
   folding those in is the reading block's job.

   ---- how a match is scored ----

   Rank by WHERE the match is, not by how many times it occurs. A
   reader typing three letters is naming a thing, so a title
   starting with what they typed is what they meant, and a word
   inside a blurb almost never is. The four bands below are that
   judgement, and they are bands rather than a continuous score
   because a continuous one invites tuning and hides the rule.
   ============================================================ */

/** One thing a reader can go to. */
data class Found(
    val title: String,
    val url: String,
    /** What kind of thing it is, in one word. */
    val hint: String,
    /** Which destination it belongs to, which decides its colour. */
    val group: String?,
    val blurb: String?,
    val rank: Int,
)

/** The bands, worst first, so a bigger number is a better match. */
private const val IN_BLURB = 1
private const val IN_TITLE = 2
private const val TITLE_WORD_START = 3
private const val TITLE_START = 4

/** Where in a haystack the needle is, or 0 for nowhere.

    Case-folded on both sides, because a reader typing `dse`
    means DSE, and Bangla has no case at all so it costs nothing
    there. */
private fun scoreOf(title: String, blurb: String?, needle: String): Int {
    val t = title.lowercase()
    val n = needle.lowercase()
    return when {
        t.startsWith(n) -> TITLE_START
        /* A word inside the title starting with what was typed:
           "stock" finds "Bangladesh stock check". The check is
           for a separator BEFORE the match rather than a split
           into words, because a title here can hold a colon, a
           slash or an en dash and splitting on spaces alone
           misses "Money: টাকা". */
        t.indexOf(n).let { i -> i > 0 && !t[i - 1].isLetterOrDigit() } -> TITLE_WORD_START
        n in t -> IN_TITLE
        blurb != null && n in blurb.lowercase() -> IN_BLURB
        else -> 0
    }
}

/** Everything the manifest knows about, searched.

    Empty for an empty query rather than everything: a palette
    that lists 300 rows before a key is pressed is a wall, and the
    caller can show whatever it likes in that state. */
fun search(site: SiteManifest?, query: String, limit: Int = 40): List<Found> {
    val needle = query.trim()
    if (site == null || needle.isEmpty()) return emptyList()

    val found = ArrayList<Found>()

    fun add(title: String, url: String, hint: String, group: String?, blurb: String?) {
        val rank = scoreOf(title, blurb, needle)
        if (rank > 0) found += Found(title, url, hint, group, blurb, rank)
    }

    for (page in site.pages) add(page.title, page.url, page.hint, page.group, page.blurb)

    /* A school is findable by either of its names. A reader who
       knows it as টাকা ও শেয়ার and a reader who knows it as the
       money school are the same reader on different days. */
    for (school in site.ladders) {
        add(school.bn, school.href, "School", school.key, school.blurb)
        add(school.en, school.href, "School", school.key, school.blurb)
    }

    for (tool in site.tools) {
        add(tool.bn, "/tools/${tool.id}", "Calculator", "tools", tool.blurb)
        add(tool.en, "/tools/${tool.id}", "Calculator", "tools", tool.blurb)
    }

    /* A term is a lesson of the money school's first stage, which
       is why it is addressed by slug rather than carrying a URL:
       `basics-1` keeps the terms shape of address for a reason
       the site writes out at length, and this must not invent a
       second spelling of it. */
    for (group in site.termGroups) {
        for (term in group.terms) {
            add(term.bn, "/money/terms/${term.slug}.html", "Term", "money", term.blurb)
            add(term.en, "/money/terms/${term.slug}.html", "Term", "money", term.blurb)
        }
    }

    /* One row per address. Both names of a school match, and a
       reader wants one result for it, so the better-scoring name
       wins and the other goes. */
    return found
        .groupBy { it.url }
        .map { (_, rows) -> rows.maxBy { it.rank } }
        .sortedWith(compareByDescending<Found> { it.rank }.thenBy { it.title.length })
        .take(limit)
}

/** The site's own grouping, for the rows to sit under.

    By `hint`, which is the one word the site already uses to say
    what a thing is, so the headings here are the headings there
    rather than a taxonomy invented for a phone. */
fun grouped(results: List<Found>): List<Pair<String, List<Found>>> =
    results.groupBy { it.hint.ifBlank { "Page" } }
        .toList()
        .sortedByDescending { (_, rows) -> rows.maxOfOrNull { it.rank } ?: 0 }
