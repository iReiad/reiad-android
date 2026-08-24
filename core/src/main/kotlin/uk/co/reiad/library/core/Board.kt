package uk.co.reiad.library.core

import kotlinx.serialization.Serializable

/* ============================================================
   The front page is a board the reader arranges.

   ---- what was wrong with the old one ----

   A door, then a card per school, then a card per tool. Every
   card said the same thing in the same voice, "here is a link",
   and none of them said anything the reader did not already
   know. That is a menu, and a menu is what you read once.

   Everything worth putting there was already being computed one
   screen deeper: how far through a school somebody is, what they
   were reading, what today's log comes to, what was published
   this week.

   ---- the split, which is CLAUDE.md's own ----

   **The CATALOGUE is data. A widget's DRAWING is code.**

   So `shared/widgets.ts` reaches this app through `/api/site`
   and a widget renamed there is renamed here at the next fetch.
   What a widget draws is a composable, so a kind this build has
   no renderer for is SKIPPED rather than drawn as a blank
   rectangle with a title on it.

   That is not a hypothetical: the two sides will be at different
   versions for as long as there is an app. A phone installed in
   March reads a catalogue deployed in August, and a browser
   reads a board a newer phone wrote. `layoutOf` is where both
   crossings are handled, and the rule for both is the same: drop
   what cannot be read. A board one card short is recoverable and
   a board that will not load is not.

   `Board.kt` and `shared/widgets.ts` are the same arithmetic
   twice, which is what `BoardTest` and `scripts/widgets.test.ts`
   both assert. The alternative was the app fetching a parse,
   which is not a thing an endpoint can send.
   ============================================================ */

/** How much room a widget takes, in a phone home screen's own
    three steps: half the row and square-ish, the row, or the row
    with a LIST in it. The kind draws differently at each size it
    offers, which is what makes this a size and not a stretch.

    `half` and `full` were the first two and are inside real
    accounts under `home-board`, so `of()` reads them for ever as
    SMALL and WIDE. They are never written: `id` is the new
    spelling, so a board saved today round-trips through the
    three. Dropping the aliases would quietly empty the board of
    everybody who arranged one before this shipped. */
enum class WidgetSize(val id: String) {
    SMALL("small"),
    WIDE("wide"),
    TALL("tall"),
    ;

    companion object {
        private val ALIASES = mapOf("half" to SMALL, "full" to WIDE)

        fun of(id: String?): WidgetSize? =
            ALIASES[id] ?: entries.firstOrNull { it.id == id }
    }
}

/** One kind of widget, as the site describes it.

    Every field is optional on the wire, because this arrives from
    a deploy that may be newer than this build and a missing half
    must fall back rather than crash. */
@Serializable
data class WidgetKind(
    val id: String = "",
    val bn: String = "",
    val en: String = "",
    /** One Bangla line: what it SHOWS. This is what a reader
        reads in the picker before adding it. */
    val note: String = "",
    val sizes: List<String> = emptyList(),
    /** What has to be true before it has anything to say:
        `account`, `school` or `network`. */
    val needs: String? = null,
    val icon: String = "",
) {
    fun name(lang: String): String = if (lang == "bn") bn.ifBlank { en } else en.ifBlank { bn }

    /** The size it gets when it is added: the first the site
        offers, and the row where it offers nothing, because a
        widget that arrives half-width reads as one somebody has
        already fiddled with. */
    fun added(): WidgetSize = WidgetSize.of(sizes.firstOrNull()) ?: WidgetSize.WIDE

    fun offers(size: WidgetSize): Boolean = WidgetSize.of(sizes.firstOrNull { s ->
        WidgetSize.of(s) == size
    }) != null

    /** The next size along the kind's OWN list, wrapping, or null
        where it offers one: a resize control on a widget with one
        size is a control that does nothing twice. The order is
        the site's, so the two boards cycle the same way. */
    fun other(size: WidgetSize): WidgetSize? {
        val offered = sizes.mapNotNull { WidgetSize.of(it) }.distinct()
        if (offered.size < 2) return null
        val at = offered.indexOf(size)
        return offered[(if (at < 0) 0 else at + 1) % offered.size]
    }
}

/** The catalogue and the default board, as `/api/site` sends
    them. Empty until a deployment carries it: `FLOOR` is what a
    first run with no network draws. */
@Serializable
data class Widgets(
    val kinds: List<WidgetKind> = emptyList(),
    val home: List<String> = emptyList(),
)

/** One thing on the board: which kind, and how wide. */
data class Placed(val id: String, val size: WidgetSize)

/** The key a board is filed under, in the browser and here. */
const val BOARD_KEY = "home-board"

/** What is under that key: the ordered list, and the stamp that
    makes it a MARK.

    A board is REPLACED, not accumulated, so the newer of two
    devices wins on `ts`. The union of two devices' boards would
    hold everything either of them ever had, so a widget removed
    on a phone would come back off the laptop, and nothing would
    look broken. */
@Serializable
data class BoardRecord(
    val board: List<String> = emptyList(),
    val ts: Long = 0L,
)

/** What a first run with no network gets.

    The same six `HOME_DEFAULT` holds on the site, and it is here
    for the reason the routine tool's words are: a phone that has
    never fetched anything still has to open on something. The
    manifest's own `home` wins the moment it arrives, so this
    goes stale by design and cannot be what anybody sees twice. */
val BOARD_FLOOR: List<String> = listOf(
    "continue:wide",
    "progress:wide",
    "pulse:tall",
    "market:tall",
    "schools:wide",
    "tools:wide",
)

/** What each kind is called, before the catalogue arrives.

    **Not a second copy of the site's table.** The manifest's own
    name wins the moment it lands, and this is only ever seen on a
    phone that has not fetched one yet: `KindsFloorTest` asserts
    that every id here is one this build can draw, so it cannot
    drift into naming something that does not exist.

    It is here because the alternative shipped: with no catalogue
    the arranging strip fell back to the id and a reader saw
    `continue`, `progress`, `pulse` down the side of their own
    front page, in a Bangla app, as though the screen were
    unfinished. A name a reader can read is the floor; the site's
    is the answer. */
val KIND_NAMES: Map<String, Pair<String, String>> = mapOf(
    "continue" to ("যেখানে ছিলেন" to "Where you left off"),
    "progress" to ("কতটা হলো" to "How far you are"),
    "streak" to ("যে দিনগুলো এসেছেন" to "A year of days"),
    "diet" to ("আজকের খাওয়া" to "Today's log"),
    "routine" to ("আজকের রুটিন" to "Today's routine"),
    "target" to ("লক্ষ্য" to "A target"),
    "library" to ("পরে পড়ব" to "Saved to read"),
    "pulse" to ("নতুন লেখা" to "Latest writing"),
    "market" to ("বাজারের খবর" to "Market pulse"),
    "stock" to ("শেয়ার যাচাই" to "Stock check"),
    "schools" to ("যা যা শেখানো হয়" to "The schools"),
    "tools" to ("যন্ত্রপাতি" to "The tools"),
    "term" to ("আজকের শব্দ" to "A word today"),
)

/** Which sizes each kind offers, before the catalogue arrives.

    The same floor discipline as `KIND_NAMES`: the manifest's own
    catalogue wins the moment it lands, and this exists because
    without it the PICKER offered nothing at all. The manifest
    carries no `widgets` yet, so `site?.widgets?.kinds` was an
    empty list, the picker filtered an empty list, and a reader
    who removed a widget could never get it back, let alone add
    the six that were never on the floor board. */
val KIND_SIZES: Map<String, List<String>> = mapOf(
    "continue" to listOf("wide", "small"),
    "progress" to listOf("wide", "small", "tall"),
    "streak" to listOf("wide", "tall"),
    "diet" to listOf("wide", "small", "tall"),
    "routine" to listOf("wide", "tall"),
    "target" to listOf("wide", "small"),
    "library" to listOf("wide", "tall"),
    "pulse" to listOf("wide", "tall"),
    "market" to listOf("wide", "tall"),
    "stock" to listOf("wide", "small"),
    "schools" to listOf("wide", "tall"),
    "tools" to listOf("wide", "small"),
    "term" to listOf("wide", "small"),
)

/* ============================================================
   A word a day, out of the glossary that is already here.

   The manifest carries eighteen terms with their Bangla, their
   English and a sentence each, and until now the only thing that
   read them was the search box: eighteen explanations of what a
   share is, sitting on the phone, findable only by somebody who
   already knew the word they were looking for.

   ---- the same word all day, a different one tomorrow ----

   Chosen by the DATE rather than at random, which is the whole
   difference between a widget and a slot machine: a reader who
   glances at their board twice before lunch sees the same word
   twice, and can go and look it up. A random one on every
   composition would change while they were reading it.

   The date IS the seed, so nothing is stored, nothing syncs, and
   two devices with the same day show the same word. `hashCode`
   is not used: it is not specified across platforms, and a board
   that showed a different word on the site would be a small lie
   about a shared thing. This is the site's own arithmetic: sum
   the digits of the ISO date and take the remainder.
   ============================================================ */

/** Which of them today gets, or null when the glossary has not
    arrived. */
fun termOfDay(groups: List<TermGroup>, iso: String): Term? {
    val all = groups.flatMap { it.terms }
    if (all.isEmpty()) return null
    var seed = 0
    for (ch in iso) if (ch.isDigit()) seed = seed * 10 + (ch - '0')
    return all[(seed % all.size + all.size) % all.size]
}

/** Where a term lives, which is a lesson of the money school's
    first stage: the same address `Search` gives it, and not a
    second spelling of it. */
fun termHref(term: Term): String = "/money/terms/${term.slug}.html"

/** The whole catalogue, from the floors, for a manifest that has
    not sent one. Every id is one `KIND_NAMES` can name, and the
    caller filters by what IT can draw. */
fun catalogueFloor(): List<WidgetKind> = KIND_NAMES.map { (id, names) ->
    WidgetKind(
        id = id,
        bn = names.first,
        en = names.second,
        sizes = KIND_SIZES[id] ?: listOf("wide"),
    )
}

/** The kind a board holds, described: the site's entry where one
    has arrived, and a readable name where it has not. */
fun kindOf(id: String, catalogue: Map<String, WidgetKind>, size: WidgetSize): WidgetKind {
    catalogue[id]?.let { return it }
    val (bn, en) = KIND_NAMES[id] ?: (id to id)
    return WidgetKind(id = id, bn = bn, en = en, sizes = listOf(size.id))
}

/** `"progress:half"` as a pair, or null when this build cannot
    read it. */
fun parsePlaced(entry: String, drawable: Set<String>): Placed? {
    val parts = entry.split(":")
    if (parts.size != 2) return null
    val id = parts[0]
    if (id !in drawable) return null
    val size = WidgetSize.of(parts[1]) ?: return null
    return Placed(id, size)
}

/** A stored board as a list of placings, with anything this build
    cannot draw left out.

    `drawable` is what the CALLER can render, which is not the
    same as what the catalogue holds. That is the whole reason it
    is an argument.

    An EMPTY stored list means "never arranged" and falls back;
    a stored list that parses to nothing means the reader emptied
    their board, and falling back there would be the page
    overruling them. */
fun layoutOf(
    stored: List<String>?,
    drawable: Set<String>,
    fallback: List<String> = BOARD_FLOOR,
): List<Placed> {
    val entries = if (stored.isNullOrEmpty()) fallback else stored
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Placed>()
    for (entry in entries) {
        val placed = parsePlaced(entry, drawable) ?: continue
        /* One of each. There is no reading here whose second copy
           says anything the first does not. */
        if (!seen.add(placed.id)) continue
        out += placed
    }
    return out
}

/** The placings back as a stored board. */
fun storedOf(placed: List<Placed>): List<String> = placed.map { "${it.id}:${it.size.id}" }

/**
 * The board as display rows: two consecutive SMALLS pair, and
 * everything else is a row of one.
 *
 * CONSECUTIVE, and that is the rule rather than a shortcut. The
 * order is the reader's, and pairing a small past an intervening
 * wide would reorder the board for them: a small alone before a
 * wide stays alone, at the row's width, which is also what the
 * site's grid does with an odd small.
 */
/* ============================================================
   The grid a phone home screen taught everybody.

   Two columns, and a widget takes one of them or both. That is
   the whole geometry, and stating it here rather than in the
   composable is what lets `BoardTest` assert a board's shape
   without a screen: the packing, the odd square left at the end
   of a row, and the fact that nothing ever straddles a column
   boundary.

   ---- heights are a UNIT, not a measurement ----

   Everything on this board is a multiple of ONE square: a small
   is that square, a wide is the row at that height, and a large
   is the row two squares deep. A phone's home screen is legible
   at a glance because of exactly this and nothing else, and a
   board of cards that each ended wherever their text ended was
   the thing that stopped reading as widgets.

   They are FLOORS rather than fixed heights, which is the one
   place this departs from the phone it is copying, and it
   departs on purpose: an iOS widget may clip its own content
   because Apple wrote the content, and here a Bangla line that
   ran two words long would be cut off inside somebody's
   progress card. So a widget may grow past its unit when what
   is in it genuinely needs the room, and the rhythm holds for
   every widget that does not.
   ============================================================ */

/** How many of the two columns this size takes. */
fun spanOf(size: WidgetSize): Int = if (size == WidgetSize.SMALL) 1 else 2

/** How many squares deep, before content is allowed to argue. */
fun unitsOf(size: WidgetSize): Int = when (size) {
    WidgetSize.SMALL -> 1
    WidgetSize.WIDE -> 1
    WidgetSize.TALL -> 2
}

/* The PACKING itself is the grid's, not this file's: two
   columns, laid in the reader's order, and a widget that cannot
   fit beside the one before it starts the next row. There was a
   `pairSmalls` here that computed those rows by hand for a
   column of full-width cards to render, and it went when the
   board became a real grid. A second statement of a layout rule
   is a second statement that can disagree, and the one that
   disagrees silently is always the one nobody is looking at. */

/** One moved from `from` to `to`, which is the whole of a drag.

    Written here rather than inside the drag handler because a
    reorder that is right in the middle and wrong at the ends is
    the classic off-by-one, and it is the one thing on this
    screen that can be asserted without a finger. */
fun moved(placed: List<Placed>, from: Int, to: Int): List<Placed> {
    if (from !in placed.indices) return placed
    val target = to.coerceIn(0, placed.lastIndex)
    if (target == from) return placed
    val out = placed.toMutableList()
    out.add(target, out.removeAt(from))
    return out
}
