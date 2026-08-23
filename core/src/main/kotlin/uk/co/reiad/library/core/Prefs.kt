package uk.co.reiad.library.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ============================================================
   What a reader has asked for, in the site's own words.

   Seven settings, one key, and the key is `reader-prefs`, which
   is a string already in real browsers and, for a signed-in
   reader, in `public.progress` under the same name. The rule at
   the top of `README.md` covers this exactly as it covers a tick:
   **renaming a key does not move somebody's preference, it loses
   it**, and a reader who set the type larger on their laptop
   should find it larger here.

   So the JSON this reads and writes is the same JSON
   `aab/src/prefs.ts` reads and writes, field for field, value for
   value. `PrefsTest` asserts every id by name.

   ---- what is not here, and why ----

   `text` and `measure` are the type size and the line length, and
   they arrive with the reading screen in a later block rather
   than being modelled now and unused. `lang` decides which
   language a calculator opens in, and there are no calculators
   yet. All three are still PARSED and still WRITTEN BACK
   untouched, because a device that drops a field it does not
   understand is a device that resets a setting somebody made on
   their laptop, silently, the first time they open the app.
   ============================================================ */

/** The one key. */
const val PREFS_KEY = "reader-prefs"

/** And the two the site writes BESIDE it, both of which predate
    `reader-prefs` and are still read by things that predate it.

    `theme` is written separately because the pre-paint boot
    script has to answer "which theme" before it can afford to
    parse JSON, and `tool-lang` because the calculators have read
    that name since long before there were accounts. Neither is a
    duplicate to be tidied: they are the names other code uses. */
const val THEME_KEY = "theme"
const val TOOL_LANG_KEY = "tool-lang"

/** Which groups lead. A preference, never a gate. */
const val AUDIENCE_KEY = "audience"
const val TRACK_KEY = "track"

/** One option a reader can pick, with the site's own label and
    the sentence under it. The sentence is not decoration: a
    setting whose effect a reader cannot predict is a setting they
    will not touch. */
data class PrefOption<T>(val id: T, val label: String, val note: String = "")

/* ---- the tables are top level, and that is not a style choice ----

   Every one of these was a `companion object` inside its enum
   first, and the Kotlin 2.0.21 serialization plugin crashes on
   that: "Plugin generated companion object for class ..., but it
   is already present in class", an internal compiler error naming
   the file and no line, with nothing in the source that looks
   wrong. It is the plugin adding a companion for `serializer()`
   to an enum that already has one, and it happens to every enum
   in a module the plugin is applied to, annotated or not.

   Do NOT give an enum in this module a companion object. Top
   level is also what the site does, `THEMES`, `GLASSES`, `BLURS`,
   `VEILS`, so the two files read the same way. */

enum class Theme(val id: String) { SYSTEM("system"), LIGHT("light"), DARK("dark") }

/* One word each, and that is a constraint rather than a style.
   These ride in a segmented control three across a handset, so a
   label is about eleven characters before it wraps: "Follow my
   system" came out on two lines and spilled outside its own
   thumb. The note beside each is where the sentence goes. */
val THEMES = listOf(
    PrefOption(Theme.SYSTEM, "System", "whatever the phone is set to"),
    PrefOption(Theme.LIGHT, "Light", "always, whatever the phone says"),
    PrefOption(Theme.DARK, "Dark", "always, whatever the phone says"),
)

fun themeOf(id: String?): Theme = Theme.entries.firstOrNull { it.id == id } ?: Theme.SYSTEM

/* ---- three FINISHES, not three blurs ----

   What separates them is what the surface is made of. `frost` is
   cold and sees a long way through, `paper` is the site's own
   weave with the blur pulled back so the texture reads, and
   `plain` is not glass at all.

   `plain` is the one to keep working. It is what a reader who has
   asked their system for less transparency gets, and what
   anybody who finds moving text under a bar hard to read
   chooses, so it is a real finish with its own solid grounds
   rather than the others with a feature switched off. */
enum class Finish(val id: String) { FROST("frost"), PAPER("paper"), PLAIN("plain") }

val GLASSES = listOf(
    PrefOption(Finish.FROST, "Frost", "cold, and you see a long way through"),
    PrefOption(Finish.PAPER, "Paper", "the site's own weave, held closer"),
    PrefOption(Finish.PLAIN, "Plain", "no blur at all, solid grounds"),
)

fun finishOf(id: String?): Finish = Finish.entries.firstOrNull { it.id == id } ?: Finish.FROST

/** A multiplier rather than a radius, so one step moves every
    surface together and a bar stays thicker than a chip. */
enum class Blur(val id: String, val amount: Double) {
    SOFT("soft", 0.55), NORMAL("normal", 1.0), DEEP("deep", 1.7)
}

val BLURS = listOf(
    PrefOption(Blur.SOFT, "Soft", "barely there"),
    PrefOption(Blur.NORMAL, "Normal", "what this site has always been"),
    PrefOption(Blur.DEEP, "Deep", "properly frosted"),
)

fun blurOf(id: String?): Blur = Blur.entries.firstOrNull { it.id == id } ?: Blur.NORMAL

/** How much the page shows through. The middle one is the 0.72
    the stylesheet has always carried, and the two either side are
    steps away from what is already there rather than a scale
    invented around it. */
enum class Veil(val id: String, val alpha: Double) {
    CLEAR("clear", 0.54), NORMAL("normal", 0.72), DENSE("dense", 0.90)
}

val VEILS = listOf(
    PrefOption(Veil.CLEAR, "Clear", "the page shows through"),
    PrefOption(Veil.NORMAL, "Normal", "what this site has always been"),
    PrefOption(Veil.DENSE, "Dense", "quieter behind the words"),
)

fun veilOf(id: String?): Veil = Veil.entries.firstOrNull { it.id == id } ?: Veil.NORMAL

/* ---- and the audiences are NOT a table here ----

   There is no `enum class Audience` in this file on purpose. The
   two answers the switch offers, their labels and their Bangla
   subtitles all arrive in the manifest under `audiences`, and
   which groups lead for each arrives under `order`. A copy here
   would be right on the day it was written and wrong the first
   time the site adds a third answer or renames one, which is the
   failure the whole `/api/site` contract exists to prevent.

   What IS here is the key the choice is stored under, because
   that is a fact about somebody's browser rather than about the
   site's menu.

   A stored audience is a `String?` and null is a real answer: a
   reader who has never touched the switch gets the site's own
   order rather than one chosen on their behalf.

   Which groups lead. A learner reads down the learning groups and
   finds the work at the bottom; somebody hiring gets the reverse.
   Both orders hold EVERY group: this reorders, it never filters,
   and `orderFor` below is where that is enforced rather than
   hoped for. A switch that hid things would be a switch a reader
   could not undo without knowing what they had lost. */

/** The groups, in the order this reader should meet them.

    Anything the order does not name is appended rather than
    dropped, which is the "reorders, never hides" rule stated as
    code. A group added to the site tomorrow appears at the bottom
    of both orders instead of vanishing from one of them. */
fun orderFor(
    groups: List<String>,
    order: List<String>?,
): List<String> {
    if (order.isNullOrEmpty()) return groups
    val leading = order.filter { it in groups }
    return leading + groups.filterNot { it in leading }
}

/** Everything at once, in the shape the site stores it.

    `@SerialName` is not cosmetic here. These are the field names
    inside somebody's `reader-prefs` value, and Kotlin's own
    naming would have written `toolLang` into a record the
    browser then failed to read. */
@Serializable
data class Prefs(
    @SerialName("text") val text: String = "normal",
    @SerialName("measure") val measure: String = "normal",
    @SerialName("lang") val lang: String = "bn",
    @SerialName("glass") val glass: String = Finish.FROST.id,
    @SerialName("blur") val blur: String = Blur.NORMAL.id,
    @SerialName("veil") val veil: String = Veil.NORMAL.id,

    /** When this record was last written, in epoch millis.

        `reader-prefs` is the one key in the sync table whose rule
        is MARK rather than SET, and a mark reconciles on the `ts`
        INSIDE its value. A record written without one is a record
        that always loses: it reads as timestamp zero, so a
        preference set on this phone would be overwritten by a
        laptop's every single exchange, silently, for ever.

        The site writes `Date.now()` here on every save and this
        does the same. */
    @SerialName("ts") val ts: Long = 0L,

    /** NOT part of the stored record, and that is the site's
        arrangement rather than an omission here.

        The theme is written to its own `theme` key beside this
        one, because the pre-paint boot script has to answer
        "which theme" before it can afford to parse JSON. So
        `readPrefs` on the site reads this record and then
        overrides the theme from that key, and a device that put
        the theme INSIDE the record would be writing a field the
        site never reads. */
    @kotlinx.serialization.Transient val theme: String = Theme.SYSTEM.id,
) {
    val themeChoice: Theme get() = themeOf(theme)
    val finish: Finish get() = finishOf(glass)
    val blurChoice: Blur get() = blurOf(blur)
    val veilChoice: Veil get() = veilOf(veil)

    /** How strong the blur on a glass surface is, which is the
        finish's own radius times the reader's multiplier. `plain`
        is not glass, so it has none, and that is the finish
        answering rather than a special case somewhere else. */
    fun blurRadius(): Double = when (finish) {
        Finish.PLAIN -> 0.0
        Finish.PAPER -> 9.0 * blurChoice.amount
        Finish.FROST -> 14.0 * blurChoice.amount
    }

    /** And how much of the page a glass surface lets through.
        `plain` is opaque, again by being itself. */
    fun veilAlpha(): Double = if (finish == Finish.PLAIN) 1.0 else veilChoice.alpha
}
