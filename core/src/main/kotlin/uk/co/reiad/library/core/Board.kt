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

/** How wide a widget runs. Two, because a phone is one column
    wide and anything finer is a grid nobody can drag on. */
enum class WidgetSize(val id: String) {
    HALF("half"),
    FULL("full"),
    ;

    companion object {
        fun of(id: String?): WidgetSize? = entries.firstOrNull { it.id == id }
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
        offers, and full width where it offers nothing, because a
        widget that arrives half-width reads as one somebody has
        already fiddled with. */
    fun added(): WidgetSize = WidgetSize.of(sizes.firstOrNull()) ?: WidgetSize.FULL

    fun offers(size: WidgetSize): Boolean = sizes.any { it == size.id }

    /** The other size it offers, for the resize control. Null
        where it offers one, and the control is absent rather
        than present and inert. */
    fun other(size: WidgetSize): WidgetSize? =
        WidgetSize.entries.firstOrNull { it != size && offers(it) }
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
    "continue:full",
    "progress:full",
    "pulse:full",
    "market:full",
    "schools:full",
    "tools:full",
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
)

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
