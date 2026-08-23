package uk.co.reiad.library.core.routine

import kotlin.math.min

/* ============================================================
   What the routine says about itself.

   ---- the one rule every function here obeys ----

   **A MEAN IS OVER THE DAYS SOMEBODY MARKED, never over calendar
   days.** That is one line in each of them and it is the whole
   property that stops any of this punishing anybody: a mean over
   the days somebody turned up stays where it was through a quiet
   fortnight, and a mean over calendar days falls towards nought
   while somebody is ill. The second is not a description of a
   routine. It is a chart of a life, scored.

   Every mean therefore carries the COUNT of the days it was taken
   over. Nought out of nothing and nought out of thirty are the
   same number and a drawing has to tell them apart: the first is
   "nothing yet" and only the second is a fact.

   None of these has a "current" anything, none resets, and none
   can go down when the history grows. If a `since` argument ever
   appears in the counters, that is the moment this stopped being
   a gift.
   ============================================================ */

/** A day in the window that `done()` has something to say about.

    The one place that gate is written, so `momentum`, `weekdays`
    and `bandRates` cannot come to disagree about which days they
    are means over. */
private data class MarkedDay(val date: String, val entry: Entry, val fraction: Double)

private fun markedDays(
    shape: RoutineShape,
    entries: List<Entry>,
    today: String,
    days: Int,
): List<MarkedDay> {
    val by = entries.associateBy { it.date }
    return walkBack(today, days).mapNotNull { date ->
        val entry = by[date] ?: return@mapNotNull null
        val fraction = done(shape, entry) ?: return@mapNotNull null
        MarkedDay(date, entry, fraction)
    }
}

/* ============================================================
   The year, and the line
   ============================================================ */

/** One cell of the heatmap.

    `fraction` is null for a day with nothing on it, which the
    drawing renders as PAPER rather than as an empty slot: an
    unmarked day is not a hole and must not read as one. */
data class Cell(val date: String, val fraction: Double?, val mood: String?)

/** The last `weeks` weeks, oldest first, one cell per day. */
fun heat(shape: RoutineShape, entries: List<Entry>, today: String, weeks: Int = 12): List<Cell> {
    val by = entries.associateBy { it.date }
    return walkBack(today, weeks * 7).map { date ->
        val entry = by[date]
        Cell(date, done(shape, entry), entry?.mood)
    }
}

/** The last `days` days, oldest first, for a sparkline.

    `value` is 0..1, or null for a day never marked, WHICH A CHART
    MUST DRAW AS A GAP RATHER THAN AS A ZERO. A line plotted
    through nought on the days somebody was busy draws a cliff,
    and a cliff is the picture this tool exists not to draw.

    Every day in the window is here, including the empty ones, so
    the axis is dates rather than "days I turned up" and two of
    these line up with each other and with `moodRibbon`. */
data class Point(val date: String, val value: Double?)

fun series(shape: RoutineShape, entries: List<Entry>, today: String, days: Int): List<Point> {
    val by = entries.associateBy { it.date }
    return walkBack(today, days).map { Point(it, done(shape, by[it])) }
}

/**
 * One entry per day, oldest first, for a colour ribbon.
 *
 * NO SHAPE, AND IT MUST NEVER TAKE ONE. A mood is not scored here
 * and enters no arithmetic anywhere: the ribbon sits on the same
 * date axis as `series` so a pattern is VISIBLE, and the tool
 * never says one is there, because it does not know. The day a
 * number joins this shape is the day it starts asserting a
 * correlation.
 *
 * `days` is an argument rather than a default for the same
 * reason: a caller hands the ribbon and the line one number, and
 * two windows of different lengths drawn one above the other line
 * up wrongly and read as a pattern that is not there.
 */
data class MoodDay(val date: String, val mood: String?)

fun moodRibbon(entries: List<Entry>, today: String, days: Int): List<MoodDay> {
    val by = entries.associateBy { it.date }
    /* An empty string is a mood nobody chose and comes back as
       null, so the drawing has one test to make rather than two. */
    return walkBack(today, days).map { MoodDay(it, by[it]?.mood?.ifBlank { null }) }
}

/* ============================================================
   What is real and what was aspirational
   ============================================================ */

data class TaskTally(val task: Task, val marked: Int, val of: Int)

/**
 * How often each task was marked over the last `days` days.
 *
 * The most actionable panel in the tool: it shows which parts of
 * a routine are real and which were aspirational. Sorted by
 * frequency, and the tasks at the BOTTOM are the point.
 */
fun consistency(
    shape: RoutineShape,
    entries: List<Entry>,
    today: String,
    days: Int = 28,
): List<TaskTally> {
    val since = dayBefore(today, days - 1)
    val window = entries.filter { it.date >= since && it.date <= today }
    return shape.tasks
        .filter { !it.archived }
        .map { task ->
            TaskTally(task, window.count { (it.marks[task.id] ?: 0.0) > 0 }, days)
        }
        .sortedWith(compareByDescending<TaskTally> { it.marked }.thenBy { it.task.order })
}

/**
 * The tasks never marked, ever.
 *
 * THE MOST IMPORTANT THING IN THE TOOL, and it is four lines. A
 * routine full of aspirational tasks is what makes a tracker feel
 * bad, and the fix is taking them out rather than trying harder.
 * The interface lists them with an Archive beside each and says
 * nothing else: no nagging, no count, no suggestion that they
 * ought to have been done.
 */
fun neverMarked(shape: RoutineShape, entries: List<Entry>): List<Task> {
    val seen = buildSet {
        for (e in entries) for ((id, m) in e.marks) if (m > 0) add(id)
    }
    return shape.tasks.filter { !it.archived && it.id !in seen }
}

/**
 * What has changed, factually.
 *
 * In place of a streak: "marked on 11 of the last 14 days, and 4
 * of the 14 before that". Two numbers, no arrow, no colour, no
 * verdict. It is the honest version of what streaks reach for,
 * and it CANNOT punish anybody: both halves are the same length,
 * so a quiet fortnight makes a smaller number rather than a
 * broken chain.
 */
data class Change(val now: Int, val before: Int, val of: Int)

fun changed(entries: List<Entry>, taskId: String, today: String, window: Int = 14): Change {
    val marked = entries.filter { (it.marks[taskId] ?: 0.0) > 0 }.map { it.date }.toSet()
    val now = walkBack(today, window).count { it in marked }
    val before = walkBack(dayBefore(today, window), window).count { it in marked }
    return Change(now, before, window)
}

/** How a typical day divides across the bands, as fractions that
    sum to one. Empty where there is nothing yet. */
data class Share(val band: Band, val share: Double)

fun balance(shape: RoutineShape, entries: List<Entry>): List<Share> {
    val per = mutableMapOf<String, Double>()
    var all = 0.0
    for (e in entries) {
        for ((id, m) in e.marks) {
            if (m <= 0) continue
            val task = shape.tasks.firstOrNull { it.id == id } ?: continue
            per[task.band] = (per[task.band] ?: 0.0) + m
            all += m
        }
    }
    if (all == 0.0) return emptyList()
    return shape.bands.sortedBy { it.order }
        .map { Share(it, (per[it.id] ?: 0.0) / all) }
        .filter { it.share > 0 }
}

/* ============================================================
   Two windows of the same length
   ============================================================ */

data class Momentum(
    /** Mean completion over the window, counting ONLY marked days. */
    val now: Double,
    /** The same for the window before it. */
    val before: Double,
    val days: Int,
    /** How many days in the current window were marked at all.
        Beside the mean because an empty history has no mean:
        `now` is nought there for want of any other number, and
        this is how a caller tells that apart from a month
        somebody genuinely marked nothing in. */
    val marked: Int,
)

/**
 * `changed()` one level up: that one counts a single task, this
 * one reads whole days.
 *
 * BOTH HALVES ARE THE SAME LENGTH and neither is "since". There
 * is no arrow, no colour and no verdict in the shape for a
 * component to reach for.
 */
fun momentum(
    shape: RoutineShape,
    entries: List<Entry>,
    today: String,
    days: Int = 28,
): Momentum {
    fun mean(window: List<MarkedDay>): Double =
        if (window.isEmpty()) 0.0 else window.sumOf { it.fraction } / window.size

    val now = markedDays(shape, entries, today, days)
    /* The window BEFORE this one, ending the day before this one
       starts, so the two never share a day and the pair is a
       comparison rather than an overlap. */
    val before = markedDays(shape, entries, dayBefore(today, days), days)
    return Momentum(mean(now), mean(before), days, now.size)
}

data class Weekday(
    /** Sunday is 0, matching `Date.getUTCDay()`. */
    val day: Int,
    val en: String,
    val bn: String,
    val rate: Double,
    val marked: Int,
)

/** Indexed BY DAY NUMBER, which is why this is a list and not a
    table keyed by name: the index IS the day, so there is no
    arrangement in which Sunday's bar can draw Monday's figure.
    Sunday first in both languages, because the Bengali week
    starts there too. */
private val WEEKDAY_NAMES = listOf(
    "Sun" to "রবিবার", "Mon" to "সোমবার", "Tue" to "মঙ্গলবার", "Wed" to "বুধবার",
    "Thu" to "বৃহস্পতিবার", "Fri" to "শুক্রবার", "Sat" to "শনিবার",
)

/**
 * Seven, always, in week order starting Sunday.
 *
 * A weekday with no marked days is still in the list with
 * `marked` at nought, BECAUSE A CHART WITH A MISSING BAR IS A
 * CHART THAT HAS LOST A DAY: a reader cannot tell an absent
 * Wednesday from a bad one, and the tool would be asserting the
 * second while meaning the first.
 *
 * NOTHING HERE NAMES A BEST DAY and nothing built on it should: a
 * best day names a worst one, and a worst one is a thing to feel
 * behind on every time it comes round.
 */
fun weekdays(
    shape: RoutineShape,
    entries: List<Entry>,
    today: String,
    days: Int = 84,
): List<Weekday> {
    val sum = DoubleArray(7)
    val seen = IntArray(7)
    for (d in markedDays(shape, entries, today, days)) {
        val i = weekdayOf(d.date)
        sum[i] += d.fraction
        seen[i] += 1
    }
    return WEEKDAY_NAMES.mapIndexed { day, (en, bn) ->
        Weekday(day, en, bn, if (seen[day] == 0) 0.0 else sum[day] / seen[day], seen[day])
    }
}

data class BandRate(
    val id: String,
    val bn: String,
    val en: String,
    val rate: Double,
    val marked: Int,
    /** How many of this band's tasks are live. */
    val tasks: Int,
)

fun bandRates(
    shape: RoutineShape,
    entries: List<Entry>,
    today: String,
    days: Int = 84,
): List<BandRate> {
    val window = markedDays(shape, entries, today, days)
    val out = mutableListOf<BandRate>()
    for (band in shape.bands.sortedBy { it.order }) {
        val tasks = bandTasks(shape, band.id)
        if (tasks.isEmpty()) continue
        var sum = 0.0
        var seen = 0
        for (d in window) {
            var did = 0.0
            for (t in tasks) {
                val m = d.entry.marks[t.id] ?: continue
                if (m <= 0) continue
                did += min(1.0, m)
            }
            if (did == 0.0) continue
            sum += did / tasks.size
            seen += 1
        }
        out += BandRate(
            band.id, band.bn, band.en,
            if (seen == 0) 0.0 else sum / seen,
            seen, tasks.size,
        )
    }
    return out
}

/**
 * The best run of marked days inside the window, and the current
 * one.
 *
 * WHAT IT IS NOT: nothing is lost by breaking it, nothing counts
 * down, nothing names the number after this one, and no drawing
 * is hung on it. It is a fact about the past in the way "marked
 * on 11 of the last 14 days" is a fact, and `best` standing above
 * `now` is the ordinary case rather than a fall from grace.
 *
 * A RUN THAT ENDED YESTERDAY IS STILL THE CURRENT ONE. Without
 * that line this number drops to nought at midnight and climbs
 * back when somebody marks their first task at nine, which is a
 * thing to be broken however carefully it is worded. Today is not
 * over.
 *
 * No shape, deliberately, so this cannot quietly become a
 * completion figure. A day counts when its row holds a mark above
 * nought: unticking DELETES the key rather than writing a zero,
 * and a row carrying nothing but a note is not a day somebody
 * marked.
 */
data class Runs(val now: Int, val best: Int)

fun runs(entries: List<Entry>, today: String, days: Int = 365): Runs {
    val marked = entries.filter { e -> e.marks.values.any { it > 0 } }.map { it.date }.toSet()
    val window = walkBack(today, days)

    var best = 0
    var run = 0
    for (date in window) {
        run = if (date in marked) run + 1 else 0
        if (run > best) best = run
    }

    var now = 0
    var i = window.size - 1
    /* The line the paragraph above is about: today unmarked steps
       back one day rather than answering nought. Removing it
       looks like a simplification and is the punishing version. */
    if (i >= 0 && window[i] !in marked) i -= 1
    while (i >= 0 && window[i] in marked) {
        now += 1
        i -= 1
    }
    return Runs(now, best)
}
