package uk.co.reiad.library.core

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   Everything the site sends, this app reads.

   `scripts/check-app-surface.ts` on the website guards one end of
   the contract: every table in `shared/content.ts` and
   `shared/nav.ts` either reaches `/api/site` or is named as
   deliberately held back. Nothing guarded the OTHER end, and it
   is the same failure with the arrow reversed: the endpoint sends
   a field, the app's model has no property for it, `ignoreUnknownKeys`
   drops it without a word, and the feature is missing where
   nobody can see it is missing.

   That is not hypothetical. Three fields were being sent and
   dropped when this was written: `order`, which is what the
   audience switch reorders by, so the switch could not have
   worked; `pages`, which is the palette's whole index, so search
   would have been built against a second copy; and `termGroups`,
   the glossary.

   So the fixture is the oracle. It is a real answer from the live
   endpoint, and every top-level key in it has to be a field of
   `SiteManifest` or be named below with the reason.
   ============================================================ */
class ManifestSurfaceTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** A key the app deliberately does not read, with why.

        An entry here has to name a REASON, not a plan: "not
        needed yet" is a field that will be forgotten, and a
        forgotten field is what this test exists to catch. An
        entry that has gone stale fails too, below. */
    private val notRead = mapOf(
        /* ---- the desk's bookkeeping, which is not a reader's ----

           Three columns on a piece that exist for the admin desk
           and mean nothing to somebody reading. They are named
           here rather than modelled, because a field carried and
           never shown is a field somebody later mistakes for a
           feature. */
        "articles.articles[].embedded" to
            "whether the body still holds a data: URL photo. The desk offers the repair; " +
            "a reader cannot act on it and the answer changes every time the body does.",
        "article.article.embedded" to "the same, on the single-piece answer.",
        "articles.articles[].notion_page_id" to
            "which Notion page a piece was imported from. The Studio's bookkeeping.",
        "article.article.notion_page_id" to "the same, on the single-piece answer.",
        "articles.articles[].notion_synced_at" to "when that import last ran.",
        "article.article.notion_synced_at" to "the same, on the single-piece answer.",
    )

    private fun load(name: String): JsonObject =
        json.parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
                "fixtures/$name is missing. See README, 'Refreshing the fixtures'."
            }.readBytes().decodeToString(),
        ).jsonObject

    private fun fixture(): JsonObject = load("site.json")

    /* ---- the whole tree, not just the top of it ----

       This checked only the manifest's own keys first, and two
       fields were being dropped one level down while it passed:
       `kind` on a nav item, which is the word a card's chip
       shows, and `note` on a skill. A check that looks at the
       root of a tree and calls it the tree is the same shape of
       failure as the thing it is checking for.

       So it walks: every object against the class that models it,
       every array against its element's class, every map against
       its value's. A key with no field anywhere in that walk is
       reported by path, `nav[].items[].kind`, so the message says
       where to put it. */
    private fun walk(
        descriptor: SerialDescriptor,
        element: JsonElement,
        path: String,
        missing: MutableSet<String>,
        used: MutableSet<String> = mutableSetOf(),
    ) {
        when (element) {
            is JsonObject -> when (descriptor.kind) {
                StructureKind.CLASS, StructureKind.OBJECT -> {
                    val names = (0 until descriptor.elementsCount)
                        .associateBy { descriptor.getElementName(it) }
                    for ((key, value) in element) {
                        val at = names[key]
                        if (at == null) {
                            when {
                                "$path.$key" in notRead -> used += "$path.$key"
                                key in notRead -> used += key
                                else -> missing += "$path.$key"
                            }
                            continue
                        }
                        walk(descriptor.getElementDescriptor(at), value, "$path.$key", missing, used)
                    }
                }
                /* A map's keys are data, not field names, so only
                   its values are walked. `accents` and `order` are
                   both this shape. */
                StructureKind.MAP ->
                    for ((key, value) in element) {
                        walk(descriptor.getElementDescriptor(1), value, "$path[$key]", missing, used)
                    }
                else -> Unit
            }

            is JsonArray -> if (descriptor.kind == StructureKind.LIST) {
                for (item in element) {
                    walk(descriptor.getElementDescriptor(0), item, "$path[]", missing, used)
                }
            }

            else -> Unit
        }
    }

    /** Every answer this app reads, and the class that models it.

        One list rather than one test per endpoint, because both
        questions below have to be asked of ALL of them: a field
        with nowhere to land, and an exemption for a field nothing
        sends any more. */
    private val endpoints = listOf(
        Triple("site.json", SiteManifest.serializer().descriptor, "site"),
        Triple("articles.json", PiecesResponse.serializer().descriptor, "articles"),
        Triple("article-photo.json", PieceResponse.serializer().descriptor, "article"),
        Triple("article-blocks.json", PieceResponse.serializer().descriptor, "article"),
        Triple("money.json", LadderResponse.serializer().descriptor, "ladder"),
        /* The German ladder as well as the money one, and not for
           symmetry: money has no practice book, so `workbook` and
           `can` are absent from that answer entirely and the
           check could not see them. One fixture per SHAPE, not
           one per endpoint. */
        Triple("deutsch.json", LadderResponse.serializer().descriptor, "ladder"),
        Triple("book-stufe-1.json", BookResponse.serializer().descriptor, "book"),
        Triple("lesson-share.json", LessonResponse.serializer().descriptor, "lesson"),
    )

    /** Walks all of them once and reports both answers, so the
        two tests below cannot drift apart about what was
        inspected. */
    private fun sweep(): Pair<Set<String>, Set<String>> {
        val missing = sortedSetOf<String>()
        val used = sortedSetOf<String>()
        for ((name, descriptor, label) in endpoints) {
            walk(descriptor, load(name), label, missing, used)
        }
        return missing to used
    }

    @Test
    fun `every field every endpoint sends has somewhere to land`() {
        val (missing, _) = sweep()
        assertTrue(
            missing.isEmpty(),
            "These are sent and nothing here has a field for them, so they are dropped " +
                "silently: $missing. Add a property at that path, or name it in " +
                "`notRead` with the reason it stays behind.",
        )
    }

    /** And an exemption that has gone stale fails, for the reason
        the website's own `NOT_FOR_APP` list is checked the same
        way: a name kept after the thing is gone reads as though
        somebody thought about it recently.

        Asked of what the walk actually MATCHED rather than of one
        fixture's top-level keys, which is the shape this had
        first and it was wrong: every path-shaped exemption looked
        stale the moment it was added, because none of them is a
        key of the manifest. */
    @Test
    fun `nothing is exempted that nothing sends`() {
        val (_, used) = sweep()
        val stale = notRead.keys - used
        assertTrue(stale.isEmpty(), "These are exempted and no endpoint sends them: $stale")
    }

    /** The three that were being dropped, by name, because a
        regression here is invisible: the app renders, every other
        test passes, and one feature is quietly absent. */
    @Test
    fun `the three that were dropped are read`() {
        val site = json.decodeFromString(SiteManifest.serializer(), fixture().toString())
        assertTrue(site.order.isNotEmpty(), "order is what the audience switch reorders by")
        assertTrue(site.pages.isNotEmpty(), "pages is the palette's whole index")
        assertTrue(site.termGroups.isNotEmpty(), "termGroups is the glossary")
    }

    /** Both orders hold every group. The site states that rule in
        prose; this is the app checking that the answer it was
        actually sent obeys it, because a switch that hid a group
        would be a switch a reader could not undo. */
    @Test
    fun `every order the site sends holds every group`() {
        val site = json.decodeFromString(SiteManifest.serializer(), fixture().toString())
        val groups = site.nav.map { it.id }.toSet()
        assertTrue(groups.isNotEmpty())
        for ((audience, order) in site.order) {
            assertEquals(
                groups,
                order.toSet(),
                "the $audience order does not hold every group: it reorders, it never hides",
            )
        }
    }

    /** And every audience the switch offers has an order. An
        audience with none would be a button that does nothing. */
    @Test
    fun `every audience offered has an order`() {
        val site = json.decodeFromString(SiteManifest.serializer(), fixture().toString())
        assertTrue(site.audiences.isNotEmpty())
        for (audience in site.audiences) {
            assertTrue(
                site.order.containsKey(audience.id),
                "the switch offers '${audience.id}' and the site sends no order for it",
            )
        }
    }
}
