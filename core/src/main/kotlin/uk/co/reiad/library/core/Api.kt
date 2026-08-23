package uk.co.reiad.library.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uk.co.reiad.library.core.stock.Phrase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/* ============================================================
   What the site answers with.

   Every model here is `ignoreUnknownKeys`, and that is the whole
   arrangement rather than laziness. `/api/site` spreads the site's
   own tables instead of mapping them field by field, so a school
   gaining a field reaches this app with no release. That only
   works if the app tolerates fields it has never heard of, and
   the day it wants one, it adds a property and nothing else
   changes.

   Every type in here was checked against a captured answer
   rather than reasoned out. `written` is the one that caught it:
   the row is a SQL CASE returning 0 or 1, so this file declared
   an Int, and the API hands back a real `true`. Eight tests went
   red at once on a fixture the site had actually sent, which is
   the entire argument for testing against captured answers
   instead of hand-written ones.
   ============================================================ */

val ReiadJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

const val SITE_ORIGIN: String = "https://reiad.co.uk"

/* ---------- /api/site ---------- */

@Serializable
data class SiteManifest(
    val ok: Boolean = true,
    val site: SiteFacts = SiteFacts(),
    val nav: List<NavGroup> = emptyList(),
    val accents: Map<String, String> = emptyMap(),
    val audiences: List<Audience> = emptyList(),

    /** Which groups lead, per audience. Sent by `/api/site` from
        the site's own `ORDER` table, so a third audience or a
        sixth group needs no app release. Dropping this field was
        the app's own version of the failure `check-app-surface.ts`
        watches for from the other end: the endpoint sends it and
        nothing here read it. */
    val order: Map<String, List<String>> = emptyMap(),
    val ladders: List<LadderSchool> = emptyList(),
    val sections: List<ReadingSection> = emptyList(),
    val tools: List<Tool> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),

    /** What the front page SAYS: the eyebrow, one headline and
        lede per audience, and the strip of counts.

        Data rather than code, which is why it is here at all: the
        app's front door was a title and a grey line for eleven
        blocks because the site's was written into a React page.
        Editing the headline on the site now changes the app on
        its next fetch. */
    val door: Door = Door(),

    /** The routine tool's own vocabulary: the moods, the seasons,
        the garden and the two task ids the drawings hang on.

        Sent because every one of them is an id, a name in each
        language and a colour, which is DATA by the contract at
        the top of CLAUDE.md, and because this app was carrying a
        Kotlin copy of all four. A fifth mood reaches a phone on
        the next fetch now. The copy stays as the floor, for a
        first run with no network. */
    val routine: RoutineWords = RoutineWords(),

    /** The two vocabularies an account answers with: how often
        somebody means to practise, and the three kinds of target.

        Sent rather than spelled here because both are a CHECK
        constraint in Postgres: a value this app offers that the
        constraint has not heard of is a 400 on the whole write,
        so one list is the only safe number of lists. Empty until
        a deployment carries it, and every screen that reads it
        has to render with nothing rather than an empty box. */
    val profile: ProfileWords = ProfileWords(),

    /** What each hub page SAYS about itself: the eyebrow, the
        headline and the lede, with any count already resolved.

        Keyed by the nav key of the page, so a screen that knows
        which destination it is drawing knows where to look.
        Absent until a deployment carries it, and a screen with
        no head draws its title and nothing else rather than an
        empty paragraph. */
    val heads: Map<String, PageHeadWords> = emptyMap(),

    /** What the front page can be made of, and what a reader who
        has arranged nothing gets.

        The catalogue is DATA and each side's renderer is CODE, so
        a kind this build cannot draw is skipped rather than left
        as a blank rectangle with a title on it, and a kind
        renamed on the site is renamed here at the next fetch.
        Empty until a deployment carries it; `BOARD_FLOOR` is what
        a first run with no network draws. */
    val widgets: Widgets = Widgets(),

    /** The diet tool's own readouts, in both languages.

        Copy is DATA by the contract at the top of `CLAUDE.md`, so
        a line reworded on the site is reworded here at the next
        fetch and this app carries no Kotlin copy of nineteen
        sentences. Its own table rather than a corner of
        `/api/tools`, because `stringKeys` there is "every phrase
        the stock check can render" and a diet phrase in that list
        makes the stock test's assertion weaker for both tools.

        Empty until a deployment carries it, and a screen that
        reads it renders the figures with no explanation rather
        than the key in square brackets. */
    val dietWords: DietWords = DietWords(),

    /** The palette's index: every page of the site that is not
        private, with the title, the address and one line saying
        what it is. This is what the Ctrl+K palette searches on
        the site and what search searches here, and it comes down
        rather than being built from a copy, so a page added
        tomorrow is findable with no app release. */
    val pages: List<PageEntry> = emptyList(),

    /** The A to Z of terms, grouped. Every term is a lesson of the
        money school's `basics-1` stage, which is why it carries a
        slug rather than a URL. */
    val termGroups: List<TermGroup> = emptyList(),
)

@Serializable
data class PageEntry(
    val title: String = "",
    val url: String = "",
    /** What kind of thing it is, in one word, for the row's chip. */
    val hint: String = "",
    /** Which destination it belongs to, which decides its colour.
        Absent for the site's own furniture. */
    val group: String? = null,
    val blurb: String? = null,
    /** What a case study IS: a model, an analysis, a piece of
        research. Only the portfolio pages carry one. */
    val kind: String? = null,
    /** The title cut down to fit a card or a chip. */
    val short: String? = null,
)

@Serializable
data class TermGroup(
    val id: String = "",
    val bn: String = "",
    val en: String = "",
    val terms: List<Term> = emptyList(),
)

@Serializable
data class Term(
    val slug: String = "",
    val bn: String = "",
    val en: String = "",
    val blurb: String = "",
)

@Serializable
data class SiteFacts(
    val name: String = "",
    val tagline: String = "",
    val origin: String = SITE_ORIGIN,
    val email: String = "",
    val linkedin: String = "",
)

@Serializable
data class NavGroup(
    val id: String = "",
    val label: String = "",
    val accent: String = "",
    val items: List<NavItem> = emptyList(),
)

@Serializable
data class NavItem(
    val label: String = "",
    val sub: String? = null,
    val href: String = "",
    val icon: String = "",
    val key: String? = null,
    val ladder: Boolean = false,
    val soon: Boolean = false,
    /** This entry IS its group's own front page.

        A card linking to the page you are already on is a dead
        card, so the group tab drops it and takes that page's own
        head from `heads` instead. The site's `/skills` filtered
        on its own key for a year, which is a fact one page held
        and this app could not read: the Learning tab opened with
        a card titled দক্ষতা that led to a copy of the list under
        it. Absent until a deployment carries it, and false is the
        harmless answer. */
    val hub: Boolean = false,
    /** What that entry is, in three words, for a card's chip. */
    val kind: String? = null,
    val blurb: String? = null,
    val accent: String? = null,
)

/** The entry that IS this group's own front page, if it has one. */
fun NavGroup.hubItem(): NavItem? = items.firstOrNull { it.hub }

/** The rows a group's own tab lists.

    Its hub is dropped, because a card taking a reader to the list
    they are reading is a dead card and it was the FIRST one on
    the Learning tab: দক্ষতা, no blurb, leading to a copy of what
    was under it.

    Unless the hub is all there is. The reading group is one entry
    and that entry is its hub, so filtering it leaves a tab with
    nothing on it, which is worse than the repetition. */
fun rowsOf(group: NavGroup): List<NavItem> =
    group.items.filter { !it.hub }.ifEmpty { group.items }

/* ---------- /api/news ---------- */

/** One market story, as `/api/news` picked it.

    The endpoint reads three RSS feeds server-side, scores each
    story against a keyword table and dedupes, so what arrives is
    already the shortlist. `_score` and the rest of its working
    are not sent and are not wanted: the ranking is the site's
    editorial judgement and a client that re-ranked would be a
    second opinion nobody asked for.

    `titleBn` is present only where a translation succeeded, and
    a story with none is shown in English rather than held back:
    a headline nobody can read is still better than a gap where
    the news should be. */
@Serializable
data class Story(
    val title: String = "",
    @SerialName("title_bn") val titleBn: String? = null,
    val url: String = "",
    val summary: String = "",
    val source: String = "",
    /** `BD` or `Global`, and it is the SITE's label for a feed
        rather than anything the publisher said. */
    val region: String = "",
    val published: String? = null,
) {
    fun headline(lang: String): String =
        if (lang == "bn") titleBn?.takeIf { it.isNotBlank() } ?: title else title
}

@Serializable
data class NewsResponse(
    val updated: String = "",
    val count: Int = 0,
    val items: List<Story> = emptyList(),
)

/** The diet tool's own words, as `/api/site` sends them.

    `phrases` is keyed by a phrase id (`dt.bmi.why`); the four
    below are keyed by a TOKEN the arithmetic in `core/diet/`
    returns, which is what lets a band this app computed have a
    sentence without this app holding a single one of them.

    Empty until a deployment carries it, and `say()` answers null
    rather than the key: a figure with no explanation under it is
    a figure, and a figure with `dt.bmi.why` under it is a bug
    somebody has to report. */
@Serializable
data class DietWords(
    val phrases: Map<String, Phrase> = emptyMap(),
    val bmiBands: Map<String, Phrase> = emptyMap(),
    val whtrBands: Map<String, Phrase> = emptyMap(),
    val sexForms: Map<String, Phrase> = emptyMap(),
    val cutSets: Map<String, Phrase> = emptyMap(),
) {
    private fun pick(p: Phrase?, lang: String): String? {
        val said = if (lang == "bn") p?.bn ?: p?.en else p?.en ?: p?.bn
        return said?.takeIf { it.isNotBlank() }
    }

    /** One phrase, or null where the table has not arrived. */
    fun say(key: String, lang: String): String? = pick(phrases[key], lang)

    fun bmiBand(token: String, lang: String): String? = pick(bmiBands[token], lang)
    fun whtrBand(token: String, lang: String): String? = pick(whtrBands[token], lang)
    fun sexForm(token: String, lang: String): String? = pick(sexForms[token], lang)
    fun cutSet(token: String, lang: String): String? = pick(cutSets[token], lang)
}

@Serializable
data class Audience(val id: String = "", val label: String = "", val sub: String = "")

@Serializable
data class LadderSchool(
    val key: String = "",
    val bn: String = "",
    val en: String = "",
    val href: String = "",
    val accent: String = "",
    val blurb: String = "",
    /** What kind of thing this is, in Bangla: `কোর্স` for every
        school. It is the chip on the card, and it comes from the
        nav table rather than being spelled here, so the day a
        school is something other than a course the app says so
        with no release. */
    val kind: String = "",
)

/** The routine tool's vocabulary, out of `shared/routine.ts`. */
@Serializable
data class RoutineWords(
    val moods: List<MoodWord> = emptyList(),
    val seasons: List<SeasonWord> = emptyList(),
    val garden: List<PlantWord> = emptyList(),
    /** The two task ids: `{"birds": "brd", "plants": "pln"}`. */
    val grown: Map<String, String> = emptyMap(),
)

@Serializable
data class MoodWord(
    val id: String = "",
    val bn: String = "",
    val en: String = "",
    /** A hex value, because it is data rather than a token: a
        reader can change it and there is no stylesheet to edit. */
    val colour: String = "",
)

@Serializable
data class SeasonWord(
    val id: String = "",
    val bn: String = "",
    val en: String = "",
    /** When it starts: `[month, day]`.

        The boundaries are DATA on the site, carried on each row
        of `SEASONS`, which was a surprise worth acting on: the
        first version of this reasoned that mid-December being
        winter is a fact about Bangladesh's calendar and therefore
        arithmetic, and the site had already decided otherwise. A
        seventh season works with no app release because of this
        field. */
    val from: List<Int> = emptyList(),
    val colour: String = "",
)

@Serializable
data class PlantWord(
    /** How many times the task has to have been marked. */
    val at: Int = 0,
    val bn: String = "",
    val en: String = "",
)

/** The front door's words. `DOOR` in `shared/content.ts`. */
@Serializable
data class Door(
    val eyebrow: String = "",
    /** Keyed by audience id, plus `open` for a reader who has not
        answered the switch. */
    val copy: Map<String, DoorCopy> = emptyMap(),
    val facts: List<DoorFact> = emptyList(),
)

@Serializable
data class DoorCopy(
    val headline: String = "",
    /** The marked words, which the site guarantees are a
        substring of `headline`: `check-content.ts` fails
        otherwise, so nothing here has to cope with a mark that
        is not in the sentence beyond not finding it. */
    val mark: String = "",
    val lede: String = "",
    val lang: String = "bn",
)

@Serializable
data class DoorFact(
    /** Already in Bangla numerals, from the site's own `bnNum`. */
    val n: String = "",
    val label: String = "",
    val en: String = "",
    /** The key of `counts` this came from, so a client that wants
        to redraw it from a fresher count can. */
    val count: String = "",
)

@Serializable
data class ReadingSection(
    val id: String = "",
    val en: String = "",
    val bn: String = "",
    val mount: String = "",
    val hub: String = "",
    val lang: String = "",
    val blurb: String = "",
    /** Which list in the site's own manifest backs this section,
        by name. The app does not resolve it: the endpoint sends
        the resolved rows separately, and this is the name so that
        the two can be told apart when both arrive. */
    val list: String = "",
)

/** A practice book: a slug and how many days it runs for.

    The DAYS are the whole of its structure. A book is not a
    ladder of lessons: it is one page a learner returns to, thirty
    or sixty or ninety times, writing into it. */
@Serializable
data class Workbook(val slug: String = "", val days: Int = 0)

@Serializable
data class Tool(val id: String = "", val bn: String = "", val en: String = "", val blurb: String = "")

@Serializable
data class Skill(
    val slug: String = "",
    val bn: String = "",
    val en: String = "",
    val icon: String = "",
    val status: String = "live",
    val blurb: String = "",
    val url: String? = null,
    val course: Boolean = false,
    val note: String? = null,
)

/** One hub page's own words: what `/skills` and `/portfolio`
    say about themselves.

    `lede` arrives with any `{n}` ALREADY FILLED. The endpoint
    resolves it from `COUNTS` before it leaves, so nothing here
    has to know that a lede can carry a count, and nothing here
    can print the characters `{n}` to a reader. */
@Serializable
data class PageHeadWords(
    val eyebrow: String = "",
    val title: String = "",
    val lede: String = "",
    /** `bn` or `en`, which decides the face. */
    val lang: String = "en",
)

/* ---------- /api/schools/<school> ---------- */

@Serializable
data class LadderResponse(
    val ok: Boolean = true,
    val stages: List<Stage> = emptyList(),
    val counts: LadderCounts = LadderCounts(),
)

@Serializable
data class LadderCounts(val total: Int = 0, val written: Int = 0)

@Serializable
data class Stage(
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val de: String? = null,
    val kicker: String? = null,
    val icon: String? = null,
    val who: String? = null,
    val blurb: String? = null,
    val status: String = "live",

    /** What a learner will be able to DO at the end of a stage,
        in one sentence. The school's own promise, and the thing a
        stage card is really selling. */
    val can: String? = null,

    /** The practice book that goes with this stage, where there
        is one. Two schools have books and two do not, so this is
        absent rather than empty most of the time. */
    val workbook: Workbook? = null,

    /** And what a stage says INSTEAD of a book, where the
        practice is not a book.

        The German school's fourth stage carries "real news every
        day, a book, a series, a friendship: at this level not a
        practice book, life". A stage with neither a workbook nor
        one of these is a stage that simply has no practice
        attached, and shows nothing. */
    val uebung: String? = null,

    /** Stage slugs a reader should have read first.

        A SUGGESTION and never a lock. Nothing on this site is
        gated: a reader who wants stage six on their first day
        gets stage six, and the site has never had a padlock on
        it. What this earns is a quiet line saying where the
        ground under a stage was laid, which is the difference
        between a ladder and a corridor.

        Sent by the endpoint from the school's own curriculum and
        read by nothing on the site itself, which is why it took a
        surface check to notice it existed. */
    val needs: List<String> = emptyList(),

    /** Where this stage's lessons live. `basics-1` carries
        `/money/terms/` because those pages were the glossary
        before the school had a builder. Absent means the ordinary
        `/<school>/<stage>/` shape. */
    val base: String? = null,
    val sections: List<Section> = emptyList(),
) {
    /** Every lesson of the stage, in ladder order, whichever noun
        the school files them under. */
    val lessons: List<Lesson> get() = sections.flatMap { it.lessons }
}

/** A school names its lessons differently: money and Qur'anic
    Arabic say `lessons`, German says `teile`, English says
    `parts`. One shape here, three names on the wire. */
@Serializable
data class Section(
    val id: String = "",
    val bn: String = "",
    val en: String? = null,
    @SerialName("lessons") private val lessonsKey: List<Lesson> = emptyList(),
    @SerialName("teile") private val teileKey: List<Lesson> = emptyList(),
    @SerialName("parts") private val partsKey: List<Lesson> = emptyList(),

    /** The section's name in the language being LEARNT, where it
        has one. German sections carry it; the money school's do
        not, because a section about compounding has no second
        name to give. */
    val de: String? = null,
) {
    val lessons: List<Lesson>
        get() = when {
            lessonsKey.isNotEmpty() -> lessonsKey
            teileKey.isNotEmpty() -> teileKey
            else -> partsKey
        }
}

@Serializable
data class Lesson(
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val de: String? = null,
    val ar: String? = null,
    val blurb: String? = null,
    val icon: String? = null,
    val minutes: Int = 0,
    val risk: String? = null,
    val status: String = "live",
    /** Whether the lesson has prose yet. A promised-but-unwritten
        lesson is listed and answers with an empty body, so the
        ladder can show it without pretending it is readable. */
    val written: Boolean = false,
) {
    val isWritten: Boolean get() = written
    val isSoon: Boolean get() = status == "soon"
}

/* ---------- /api/schools/<school>/<stage>/<lesson> ---------- */

@Serializable
data class LessonResponse(val ok: Boolean = true, val lesson: LessonPage? = null)

@Serializable
data class LessonPage(
    val school: String = "",
    val stage: String = "",
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val blurb: String? = null,
    val minutes: Int = 0,
    val status: String = "live",
    val body: String = "",
)

/* ---------- /api/articles ---------- */

@Serializable
data class ArticlesResponse(val ok: Boolean = true, val articles: List<ArticleCard> = emptyList())

@Serializable
data class ArticleCard(
    val slug: String = "",
    val section: String = "insights",
    val title: String = "",
    val dek: String = "",
    val tag: String = "",
    val lang: String = "en",
    val minutes: Int = 1,
    val cover: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val topics: JsonElement? = null,
)

/* ---------- addresses ---------- */

/** Where a lesson lives on the site. Kept because it is the id in
    somebody's reading list and the target of every term link, and
    because the app's own deep links use the site's addresses
    rather than inventing a second vocabulary.

    The `.html` is part of the SLUG here, not a file extension the
    site forgot to drop. */
fun lessonUrl(school: String, stage: Stage, lessonSlug: String): String {
    val base = stage.base ?: "/$school/${stage.slug}/"
    return "$base$lessonSlug.html"
}

/** A stage's own ladder page, which carries no suffix. */
fun stageUrl(school: String, stageSlug: String): String = "/$school/$stageSlug"

/** A term link inside a lesson body is relative
    (`href="dividend.html"`), so it only means something against
    the stage it was written in. */
fun resolveHref(href: String, school: String, stage: Stage): String = when {
    href.startsWith("http://") || href.startsWith("https://") -> href
    href.startsWith("/") -> href
    href.startsWith("#") -> href
    else -> (stage.base ?: "/$school/${stage.slug}/") + href
}

/* ---------- /api/articles ---------- */

/* A piece: an article, a recipe, a travel note. One table, one
   endpoint, three sections, and which section a piece is in
   decides where it lives and what colour it wears.

   `/api/articles` answers with every LIVE piece and no bodies;
   `/api/articles/<slug>` answers with one, body included. That
   split is the endpoint's, and it is the right one for a handset
   too: a hub of six pieces should not pull six bodies. */

@Serializable
data class PiecesResponse(
    val ok: Boolean = true,
    val articles: List<Piece> = emptyList(),
)

@Serializable
data class PieceResponse(
    val ok: Boolean = true,
    val article: Piece = Piece(),
)

@Serializable
data class Piece(
    val slug: String = "",
    val title: String = "",
    /** The standfirst: one or two sentences under the title. */
    val dek: String = "",
    /** The topics as one string, which is what the row holds.
        `topics` below is the same thing split, and both arrive. */
    val tag: String = "",
    val topics: List<String> = emptyList(),
    /** `bn` or `en`. Decides the face and the leading, and it is
        a fact about the piece rather than about the reader. */
    val lang: String = "en",
    val minutes: Int = 0,
    val status: String = "live",
    /** `insights`, `cooking` or `travel`. */
    val section: String = "insights",
    /** The share card, drawn rather than borrowed: a 1200x630
        JPEG made from the lead photo. Empty where a piece has
        none, and empty is not an error. */
    val cover: String = "",
    /** The date the piece went live, `2026-08-14`. */
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    /** Only on the single-piece answer. */
    val body: String = "",
) {
    /** An address of the shape the site actually serves.

        A piece keeps its `.html`, and that is not an oversight:
        the suffix is part of the SLUG rather than part of a
        route. It is in every link inside every lesson body in the
        database and in the `public.library` row of everybody who
        has saved a piece, so inventing the suffixless spelling
        here would be a dead link with a plausible shape. */
    val url: String get() = "/$section/$slug.html"

    val isBangla: Boolean get() = lang == "bn"
}

/* ---------- /api/book/<stage> ---------- */

/* A practice book, with every answer taken out.

   The site's own note is why the endpoint exists at all: the
   books are read on the server and never sent as data, because
   every prompt has its answer beside it. `/api/book/<stage>`
   sends the days with `say[].a` stripped and
   `/api/book/<stage>/key/<day>` sends one day's answers when the
   reader presses the button.

   **So there is no `a` on `Prompt` here, and that is deliberate.**
   A field for it would be a field that is always null, and a
   field that is always null is one somebody later fills in from
   the wrong place. */

@Serializable
data class BookResponse(
    val ok: Boolean = true,
    val stage: String = "",
    val book: Book = Book(),
)

@Serializable
data class Book(
    /** `deutsch` or `english`, which decides the storage key and
        the language a target line is tagged with. */
    val school: String = "",
    /** The second line of every day's footer tick, which grows
        with the level: Stufe 1 asks whether yesterday's page was
        read first, Stufe 3 asks for a whole story. */
    val foot: String = "",
    val lede: BookLine = BookLine(),
    /** The sound key from the front of the book. Only the first
        book of each school has one: after that the sounds are
        behind you, and a section repeating them would be the book
        treating a reader as if they had not moved. */
    val sounds: List<BookSound> = emptyList(),
    val collect: BookCollection = BookCollection(),
    val end: BookLine = BookLine(),
    val motto: BookLine = BookLine(),
    val days: List<BookDay> = emptyList(),
)

/** A pair of lines: what you say, and what it means. `target` is
    the language being learnt, whichever that is. */
@Serializable
data class BookLine(val target: String = "", val bn: String = "")

@Serializable
data class BookSound(val pair: String = "", val words: String = "", val how: String = "")

@Serializable
data class BookCollection(
    val key: String = "",
    val target: String = "",
    val bn: String = "",
    val blurb: String = "",
    val columns: List<BookColumn> = emptyList(),
)

@Serializable
data class BookColumn(val key: String = "", val head: String = "", val placeholder: String = "")

/** One day. Always the same four parts, which is the whole point
    of the book: the shape of the page never changes, only what is
    poured into it. */
@Serializable
data class BookDay(
    val n: Int = 0,
    /** The day's title, in the language being learnt. */
    val target: String = "",
    val bn: String = "",
    val pattern: BookPattern = BookPattern(),
    /** Model lines to read aloud. */
    val watch: List<BookLine> = emptyList(),
    /** Prompts to translate. Speak first, write second. */
    val say: List<BookPrompt> = emptyList(),
    /** The free writing. */
    val heart: BookLine = BookLine(),
)

@Serializable
data class BookPattern(
    val shape: String = "",
    val why: String = "",
    val examples: String = "",
    val tip: String = "",
)

/** A prompt, and NO answer. See the note above this section. */
@Serializable
data class BookPrompt(val q: String = "")

/** One day's answers, in the order its prompts are in. */
@Serializable
data class BookKeyResponse(
    val ok: Boolean = true,
    val stage: String = "",
    val day: Int = 0,
    val answers: List<String> = emptyList(),
)
