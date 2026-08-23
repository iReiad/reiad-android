package uk.co.reiad.library.core.routine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

/* ============================================================
   The routine tool.

   `shared/routine.ts` on the website is the original and this is
   a second implementation of it, locked to
   `content/routine.fixtures.json`.

   ---- the rule the whole thing obeys ----

   **Nothing here can go down when the history grows, and nothing
   resets.** `ROUTINE.md` §0. The counters that drawings hang on
   only ever grow; every mean is taken over the days somebody
   MARKED rather than over calendar days, so a quiet fortnight
   makes a smaller sample rather than a falling line.

   That is one line inside each function and it is the whole
   difference between this and a habit tracker. A mean over
   calendar days falls towards nought while somebody is ill, which
   is not a description of a routine: it is a chart of a life,
   scored.

   ---- null is not nought, anywhere ----

   `done()` answers null for a day with nothing on it, and every
   drawing has to tell that apart from a bad day. A person opening
   yesterday must never be shown a nought: the number describes
   what happened, and on a day where nothing happened there is
   nothing to describe.
   ============================================================ */

@Serializable
data class Band(
    val id: String,
    val en: String,
    val bn: String,
    /** A hex value, because it is DATA rather than a token: a
        reader can change it in the builder and there is no
        stylesheet to edit when they do. */
    val colour: String,
    val order: Int,
)

/** One thing you might do in a day. */
@Serializable
data class Task(
    val id: String,
    val band: String,
    val en: String,
    val bn: String,
    /** Roughly how long. Absent for a task with no sensible
        length: "something I chose" and "went easy on my knuckles"
        are both real and neither is an hour. */
    val hours: Double? = null,
    /** Whether it enters the arithmetic at all. False means
        tracked and excluded: leisure must never be able to
        fail. */
    val counts: Boolean = true,
    val order: Int = 0,
    /** Deleted, and kept. Ids are never reused and never removed,
        because an entry keys its marks by id and a person tidying
        their list must not lose the days they marked it on. */
    val archived: Boolean = false,
)

@Serializable
data class RoutineShape(
    val bands: List<Band> = emptyList(),
    val tasks: List<Task> = emptyList(),
)

/** A day. `marks` is task id to 1 or 0.5, and an ABSENT KEY IS
    NOT A ZERO: it is a day that has nothing to say about that
    task. A zero is a judgement wearing a number's clothes. */
@Serializable
data class Entry(
    @SerialName("entry_date") val date: String,
    val marks: Map<String, Double> = emptyMap(),
    val mood: String? = null,
    val note: String? = null,
    /** What "something I chose" actually was. */
    val chose: String? = null,
)

/**
 * How much of a day was marked, 0 to 1, or null for a day with
 * nothing on it.
 *
 * NULL RATHER THAN NOUGHT, and that is the whole reason this
 * returns a nullable. An empty day renders as "today is still
 * empty" and never as "0%".
 *
 * A day with only leisure marked is not empty, but it has nothing
 * this arithmetic can describe, so it answers null too. Both
 * roads lead to null on purpose: there is no state in which
 * somebody is shown a nought.
 */
fun done(shape: RoutineShape, entry: Entry?): Double? {
    val counting = shape.tasks.filter { it.counts && !it.archived }
    if (counting.isEmpty()) return null
    val marks = entry?.marks ?: emptyMap()

    var sum = 0.0
    var any = false
    /* Only the counting ones, and only the ones still on the
       list. A mark on an ARCHIVED task stays in the row for ever
       and renders on the day it was made, and it does not move
       today's figure: a person who tidied their list should not
       find yesterday's percentage changed. */
    for (t in counting) {
        val m = marks[t.id] ?: continue
        if (m <= 0) continue
        any = true
        sum += min(1.0, m)
    }
    if (!any) return null
    return sum / counting.size
}

/** The tasks of one band, in order, archived ones left out. */
fun bandTasks(shape: RoutineShape, band: String): List<Task> =
    shape.tasks.filter { it.band == band && !it.archived }.sortedBy { it.order }

/** Hours the routine plans for, and what is left of a day.

    Leisure is counted here even though it never counts towards
    progress: an hour of television is an hour of the day whatever
    else is true. */
fun hours(shape: RoutineShape): Pair<Double, Double> {
    val planned = shape.tasks.filter { !it.archived }.sumOf { it.hours ?: 0.0 }
    return planned to max(0.0, 24 - planned)
}

/** A day's work in hours rather than in ticks, because a
    fourteen-minute task and a two-hour one are not one tick each.

    The same filter `done` uses, on both halves. A task with no
    `hours` adds nothing to either side, so nothing is scored
    against a length nobody gave it.

    Two numbers rather than a nullable, and that is not a hole in
    the rule above: `planned` is a fact about the routine and is
    true before anybody has marked anything. THE CALLER STILL ASKS
    `done()` FIRST, and an empty day gets the sentence rather than
    a bar drawn at nought. */
fun hoursDone(shape: RoutineShape, entry: Entry?): Pair<Double, Double> {
    val counting = shape.tasks.filter { it.counts && !it.archived }
    val marks = entry?.marks ?: emptyMap()
    var did = 0.0
    var planned = 0.0
    for (t in counting) {
        val h = t.hours ?: 0.0
        planned += h
        val m = marks[t.id] ?: continue
        if (m <= 0) continue
        did += min(1.0, m) * h
    }
    return did to planned
}

/* ============================================================
   The things that only ever grow
   ============================================================ */

/**
 * How many times a task has been marked, ever.
 *
 * The birds and the garden are this number. No window, no
 * "recently", no reset: a person who stops for a fortnight and
 * comes back finds the flock exactly as they left it.
 */
fun everMarked(entries: List<Entry>, taskId: String): Int =
    entries.count { (it.marks[taskId] ?: 0.0) > 0 }

/** How many birds are on the page.

    Thresholds rather than a ratio, so the flock grows in visible
    steps and then stops rather than becoming a crowd. Nothing
    announces the next one: a named threshold is a target, and
    there are none of those here. */
fun flock(times: Int): Int = when {
    times >= 200 -> 7
    times >= 100 -> 6
    times >= 50 -> 5
    times >= 25 -> 4
    times >= 10 -> 3
    times >= 3 -> 2
    times >= 1 -> 1
    else -> 0
}

data class Plant(val at: Int, val bn: String, val en: String)

/** The garden, in the order things arrive in it.

    Bangladeshi plants, because this is a Bangladeshi garden.
    NOTHING WILTS: the list only ever gets longer, and a plant
    that could die would be a streak with leaves on. */
val GARDEN: List<Plant> = listOf(
    Plant(1, "তুলসী", "Tulsi"),
    Plant(5, "জবা", "Hibiscus"),
    Plant(20, "বেলি", "Beli"),
    Plant(60, "কামিনী", "Kamini"),
    Plant(150, "শিউলি", "Shiuli"),
)

fun garden(times: Int): List<Plant> = GARDEN.filter { times >= it.at }

/* ============================================================
   Six seasons, because Bangladesh has six
   ============================================================ */

data class Season(
    val id: String,
    val bn: String,
    val en: String,
    val colour: String,
    /** When it starts: month, day. The site carries this on each
        row, so the six boundaries are data like the names. */
    val from: Pair<Int, Int> = 0 to 0,
)

/** ষড়ঋতু. Almost no software knows there are six rather than
    four, and the page in বর্ষা should not look like the page in
    শীত.

    Each is two Bengali months beginning around the middle of a
    Gregorian one, which is close enough for a colour and a word
    and is not pretending to be a calendar conversion. */
val SEASONS: List<Season> = listOf(
    Season("grishmo", "গ্রীষ্ম", "Summer", "#C4711F", 4 to 15),
    Season("barsha", "বর্ষা", "Monsoon", "#4C61A8", 6 to 15),
    Season("sharat", "শরৎ", "Autumn", "#2F8A64", 8 to 15),
    Season("hemanta", "হেমন্ত", "Late autumn", "#A2790B", 10 to 15),
    Season("sheet", "শীত", "Winter", "#6E52A8", 12 to 15),
    Season("bosonto", "বসন্ত", "Spring", "#B45570", 2 to 15),
)

/** Which of the six a date falls in. */
/** Which of the six a date falls in, against the compiled
    table. The overload below takes the site's. */
fun seasonOf(iso: String): Season = seasonOf(iso, SEASONS)

/** সুপ্রভাত, শুভ দুপুর, শুভ সন্ধ্যা, শুভ রাত্রি. */
fun greeting(hour: Int): Pair<String, String> = when {
    hour < 5 -> "শুভ রাত্রি" to "Good night"
    hour < 12 -> "সুপ্রভাত" to "Good morning"
    hour < 16 -> "শুভ দুপুর" to "Good afternoon"
    hour < 20 -> "শুভ সন্ধ্যা" to "Good evening"
    else -> "শুভ রাত্রি" to "Good night"
}

/* ============================================================
   The four moods, and there are deliberately no more
   ============================================================ */

data class Mood(val id: String, val bn: String, val en: String, val colour: String)

/** NONE OF THEM IS BAD. "Heavy" is the honest bottom of this
    scale and it is a description rather than a failure, which is
    the difference between this and every mood tracker that offers
    a frowning face. So none of the four is red, and heavy is the
    quiet violet rather than a warning.

    The colours are DATA and travel with the mood, the same way a
    band's colour does. */
val MOODS: List<Mood> = listOf(
    Mood("light", "হালকা", "Light", "#2F8A64"),
    Mood("steady", "শান্ত", "Steady", "#4C61A8"),
    Mood("full", "ভরা", "Full", "#A2790B"),
    Mood("heavy", "ভারী", "Heavy", "#6E52A8"),
)

fun moodColour(id: String?): String = MOODS.firstOrNull { it.id == id }?.colour ?: ""

/** Every line written, newest first, for the jar and the
    reflection log. */
fun written(entries: List<Entry>): List<Entry> =
    entries.filter { !it.note.isNullOrBlank() }.sortedByDescending { it.date }

/* ============================================================
   A year ago today
   ============================================================ */

data class Echo(val entry: Entry, val bn: String, val en: String)

/**
 * Something written on this date before.
 *
 * Nothing else in this tool will be as good as reading "the birds
 * ate from my hand" twelve months later on a Tuesday, and it
 * costs one lookup.
 *
 * A year first, then six months, then a month: the further back
 * it reaches the more it is worth, so it prefers the oldest it
 * can find rather than the nearest.
 */
fun echo(entries: List<Entry>, today: String): Echo? {
    val by = entries.associateBy { it.date }
    val tries = listOf(
        Triple(shiftMonths(today, -12), "এক বছর আগে আজ", "A year ago today"),
        Triple(shiftMonths(today, -6), "ছয় মাস আগে আজ", "Six months ago today"),
        Triple(shiftMonths(today, -1), "এক মাস আগে আজ", "A month ago today"),
    )
    for ((date, bn, en) in tries) {
        val entry = by[date] ?: continue
        /* Only where something was WRITTEN. A day with ticks and
           no words has nothing to say back. */
        if (!entry.note.isNullOrBlank()) return Echo(entry, bn, en)
    }
    return null
}

/* ============================================================
   The site's copy wins, and the one here is the floor.

   `MOODS`, `SEASONS`, `GARDEN` and the two ids above are a second
   statement of `shared/routine.ts`, which is exactly the thing
   the DATA/CODE contract says must not be a second statement: a
   fifth mood or a sixth plant should reach a phone on the next
   fetch rather than in a release.

   So `/api/site` sends them and these four functions prefer what
   it sent. The tables stay because a first run with no network
   still has to draw a mood row, and because a table that is
   NEVER used is a table nobody notices has gone wrong; these are
   used every time the site cannot be reached.

   The COLOURS travel with the words for the same reason a band's
   colour does: they are values a reader can change, not tokens
   with a stylesheet behind them.
   ============================================================ */

fun moodsFrom(words: uk.co.reiad.library.core.RoutineWords?): List<Mood> =
    words?.moods?.takeIf { it.isNotEmpty() }
        ?.map { Mood(it.id, it.bn, it.en, it.colour) }
        ?: MOODS

fun seasonsFrom(words: uk.co.reiad.library.core.RoutineWords?): List<Season> =
    words?.seasons?.takeIf { it.isNotEmpty() }
        ?.map {
            Season(
                it.id, it.bn, it.en, it.colour,
                from = it.from.takeIf { pair -> pair.size == 2 }
                    ?.let { pair -> pair[0] to pair[1] }
                    /* A row with no boundary sorts LAST, so it is
                       the one the year wraps on to rather than
                       one that silently claims January. */
                    ?: (0 to 0),
            )
        }
        ?: SEASONS

fun gardenFrom(words: uk.co.reiad.library.core.RoutineWords?): List<Plant> =
    words?.garden?.takeIf { it.isNotEmpty() }
        ?.map { Plant(it.at, it.bn, it.en) }
        ?: GARDEN

/**
 * Which of the six a date falls in, against whichever list of
 * seasons is current.
 *
 * The BOUNDARIES stay here rather than travelling, and that is
 * the line between the two halves: mid-December to mid-February
 * is winter is a fact about Bangladesh's calendar, which is
 * arithmetic. What শীত is called and what colour it is are
 * values, and those come down.
 */
fun seasonOf(iso: String, seasons: List<Season>): Season {
    val order = seasons.takeIf { it.isNotEmpty() } ?: SEASONS
    val parts = iso.split("-")
    val m = parts.getOrNull(1)?.toIntOrNull()
    val d = parts.getOrNull(2)?.take(2)?.toIntOrNull()
    /* The latest boundary first, and the LAST of them is what an
       unmatched date gets: winter runs from mid-December through
       January to mid-February, so a date in January is before
       every boundary in the year and belongs to the one that
       started in the year before. That wrap is the whole reason
       this is not a plain lookup. */
    val byBoundary = order.sortedByDescending { it.from.first * 100 + it.from.second }
    if (m == null || d == null) return byBoundary.first()
    val on = m * 100 + d
    return byBoundary.firstOrNull { it.from.first * 100 + it.from.second <= on }
        ?: byBoundary.first()
}

/** The garden as it stands, against a list that may have come
    down from the site. */
fun garden(times: Int, plants: List<Plant>): List<Plant> =
    plants.filter { times >= it.at }
