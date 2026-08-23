package uk.co.reiad.library.core.routine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The routine tool against the site's own model.

   `content/routine.fixtures.json` is what `shared/routine.ts`
   produced for a year of days, and every number of it is asserted
   here.

   ---- the history in it is built to break things ----

   Not a plausible month. Every day is there because some obvious
   way of writing one of these functions gets it wrong: a dead
   fortnight, a day with only leisure on it, a mark on an archived
   task, a mark above one, a note with no marks, a run that ended
   yesterday, and the 31st of a month.

   ---- and the rule underneath all of it ----

   ROUTINE.md §0: nothing here can go down when the history grows,
   and nothing resets. Every mean is over the days somebody
   MARKED, never over calendar days. A mean over calendar days
   falls towards nought while somebody is ill, which is not a
   description of a routine: it is a chart of a life, scored.
   ============================================================ */
class RoutineTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val fixture: JsonObject = Json
        .parseToJsonElement(
            requireNotNull(javaClass.getResourceAsStream("/fixtures/routine.json")) {
                "fixtures/routine.json is missing; the website generates it."
            }.readBytes().decodeToString(),
        ).jsonObject

    private val today = fixture["today"]!!.jsonPrimitive.content

    private val shape: RoutineShape =
        json.decodeFromJsonElement(RoutineShape.serializer(), fixture["shape"]!!)

    private val entries: List<Entry> =
        json.decodeFromJsonElement(ListSerializer(Entry.serializer()), fixture["entries"]!!)

    private fun same(what: String, want: Double, got: Double) {
        val scale = maxOf(abs(want), abs(got), 1.0)
        assertTrue(abs(want - got) / scale < 1e-9, "$what: said $want, this said $got")
    }

    private fun wantDouble(o: JsonObject, key: String): Double? {
        val v = o[key] ?: return null
        return if (v is JsonNull) null else v.jsonPrimitive.content.toDouble()
    }

    /* ============================================================
       0. The fixture is not vacuous
       ============================================================ */

    /* It was, once, for one run. The generator asked TEMPLATES for
       a template that lives in PRIVATE_TEMPLATES, fell back to the
       six-task starter, and every mark in the history referenced a
       task that did not exist: every `done` came back null and the
       file asserted nothing at all, while generating and
       committing perfectly cleanly. */
    @Test
    fun `the fixture has a routine and a history in it`() {
        assertTrue(shape.tasks.size >= 12, "only ${shape.tasks.size} tasks")
        assertTrue(entries.size >= 15, "only ${entries.size} days")
        assertTrue(
            shape.tasks.any { it.archived },
            "no archived task, so the rule about marks on one is untested",
        )
        assertTrue(shape.tasks.any { !it.counts }, "no leisure, so the tool's whole shape is untested")
        assertTrue(shape.tasks.any { it.hours == null }, "no task without hours")

        /* And the marks land on tasks that exist, which is the
           exact thing that silently went wrong. */
        val ids = shape.tasks.map { it.id }.toSet()
        val landed = entries.flatMap { it.marks.keys }.count { it in ids }
        assertTrue(landed > 50, "only $landed marks landed on a real task")
    }

    /* ============================================================
       1. One day at a time, which is where null-not-nought lives
       ============================================================ */

    @Test
    fun `every day reads the way the site reads it`() {
        val by = entries.associateBy { it.date }
        for (case in fixture["days"]!!.jsonArray.map { it.jsonObject }) {
            val name = case["name"]!!.jsonPrimitive.content
            assertTrue(
                case["why"]!!.jsonPrimitive.content.length > 20,
                "$name does not say what it is for",
            )
            val date = case["date"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            val entry = date?.let { by[it] }
            val out = case["out"]!!.jsonObject

            val wantDone = wantDouble(out, "done")
            val gotDone = done(shape, entry)
            if (wantDone == null) {
                assertEquals(
                    null, gotDone,
                    "$name: the site says nothing about this day and this said $gotDone. " +
                        "A nought is a judgement wearing a number's clothes.",
                )
            } else {
                assertTrue(gotDone != null, "$name: the site said $wantDone and this said nothing")
                same("$name.done", wantDone, gotDone!!)
            }

            val wantHours = out["hours"]!!.jsonObject
            val (did, planned) = hoursDone(shape, entry)
            same("$name.hours.done", wantHours["done"]!!.jsonPrimitive.content.toDouble(), did)
            same("$name.hours.planned", wantHours["planned"]!!.jsonPrimitive.content.toDouble(), planned)
        }
    }

    /* The three roads to null, said out loud. There is no state in
       which somebody is shown a nought for a day. */
    @Test
    fun `a day with nothing to describe is never a nought`() {
        val by = entries.associateBy { it.date }
        assertEquals(null, done(shape, null), "no entry at all")
        assertEquals(null, done(shape, by["2026-03-25"]), "a date with no row")
        assertEquals(null, done(shape, by["2026-03-12"]), "a note and no marks")
        assertEquals(
            null, done(shape, by["2026-03-10"]),
            "only leisure: not an empty day, and nothing this arithmetic can describe",
        )
        assertEquals(
            null, done(shape, by["2026-03-11"]),
            "a mark on an archived task moves no figure",
        )
    }

    @Test
    fun `a mark above one is capped rather than counted`() {
        val by = entries.associateBy { it.date }
        /* 2026-03-13 marks eng at 1.5, qur at 0.5 and slp at 1.
           Capped that is 2.5 of eleven counting tasks. Uncapped it
           would be 3 of eleven, and one task would be carrying a
           quarter of a day. */
        val capped = done(shape, by["2026-03-13"])!!
        val counting = shape.tasks.count { it.counts && !it.archived }
        same("capped", 2.5 / counting, capped)
    }

    /* ============================================================
       2. Everything read over the whole history
       ============================================================ */

    @Test
    fun `the heatmap and the two ribbons match, day for day`() {
        val wantHeat = fixture["heat"]!!.jsonArray.map { it.jsonObject }
        val gotHeat = heat(shape, entries, today, 12)
        assertEquals(84, gotHeat.size)
        assertEquals(wantHeat.size, gotHeat.size)
        for ((i, cell) in gotHeat.withIndex()) {
            assertEquals(wantHeat[i]["date"]!!.jsonPrimitive.content, cell.date, "heat[$i]")
            val want = wantDouble(wantHeat[i], "fraction")
            if (want == null) assertEquals(null, cell.fraction, "heat[$i] on ${cell.date}")
            else same("heat[$i]", want, cell.fraction!!)
        }

        val wantSeries = fixture["series"]!!.jsonArray.map { it.jsonObject }
        val gotSeries = series(shape, entries, today, 30)
        assertEquals(wantSeries.size, gotSeries.size)
        for ((i, p) in gotSeries.withIndex()) {
            assertEquals(wantSeries[i]["date"]!!.jsonPrimitive.content, p.date, "series[$i]")
            val want = wantDouble(wantSeries[i], "value")
            if (want == null) assertEquals(null, p.value, "series[$i] on ${p.date}")
            else same("series[$i]", want, p.value!!)
        }

        val wantMood = fixture["moodRibbon"]!!.jsonArray.map { it.jsonObject }
        val gotMood = moodRibbon(entries, today, 30)
        assertEquals(wantMood.size, gotMood.size)
        for ((i, m) in gotMood.withIndex()) {
            assertEquals(wantMood[i]["date"]!!.jsonPrimitive.content, m.date)
            val want = wantMood[i]["mood"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.content }
            assertEquals(want, m.mood, "moodRibbon[$i] on ${m.date}")
        }

        /* The three walk the same dates, so a chart and the ribbon
           under it line up. Two windows of different lengths drawn
           one above the other read as a pattern that is not
           there. */
        assertEquals(gotSeries.map { it.date }, gotMood.map { it.date })
    }

    @Test
    fun `what is real and what was aspirational`() {
        val want = fixture["consistency"]!!.jsonArray.map { it.jsonObject }
        val got = consistency(shape, entries, today, 28)
        assertEquals(want.size, got.size, "a task went missing")
        for ((i, t) in got.withIndex()) {
            assertEquals(want[i]["id"]!!.jsonPrimitive.content, t.task.id, "consistency[$i]: order")
            assertEquals(want[i]["marked"]!!.jsonPrimitive.content.toInt(), t.marked, t.task.id)
            assertEquals(want[i]["of"]!!.jsonPrimitive.content.toInt(), t.of)
        }

        assertEquals(
            fixture["neverMarked"]!!.jsonArray.map { it.jsonPrimitive.content },
            neverMarked(shape, entries).map { it.id },
        )
        /* The point of the panel. If this is empty the fixture has
           stopped testing the most important thing in the tool. */
        assertTrue(neverMarked(shape, entries).isNotEmpty(), "no aspirational task to find")
    }

    @Test
    fun `what changed, factually, with no arrow in it`() {
        val want = fixture["changed"]!!.jsonObject
        for (id in listOf("eng", "tv")) {
            val w = want[id]!!.jsonObject
            val got = changed(entries, id, today, 14)
            assertEquals(w["now"]!!.jsonPrimitive.content.toInt(), got.now, "$id.now")
            assertEquals(w["before"]!!.jsonPrimitive.content.toInt(), got.before, "$id.before")
            assertEquals(
                w["of"]!!.jsonPrimitive.content.toInt(), got.of,
                "both halves are the same length, or this is a streak again",
            )
        }
    }

    @Test
    fun `the bands, the balance and the two windows`() {
        val wantBalance = fixture["balance"]!!.jsonArray.map { it.jsonObject }
        val gotBalance = balance(shape, entries)
        assertEquals(wantBalance.size, gotBalance.size)
        for ((i, b) in gotBalance.withIndex()) {
            assertEquals(wantBalance[i]["band"]!!.jsonPrimitive.content, b.band.id, "balance[$i]")
            same("balance[$i]", wantBalance[i]["share"]!!.jsonPrimitive.content.toDouble(), b.share)
        }
        same("balance sums to one", 1.0, gotBalance.sumOf { it.share })

        val wantMomentum = fixture["momentum"]!!.jsonObject
        val gotMomentum = momentum(shape, entries, today, 28)
        same("momentum.now", wantMomentum["now"]!!.jsonPrimitive.content.toDouble(), gotMomentum.now)
        same("momentum.before", wantMomentum["before"]!!.jsonPrimitive.content.toDouble(), gotMomentum.before)
        assertEquals(wantMomentum["days"]!!.jsonPrimitive.content.toInt(), gotMomentum.days)
        assertEquals(
            wantMomentum["marked"]!!.jsonPrimitive.content.toInt(), gotMomentum.marked,
            "the count of days the mean was taken over, which is how a caller tells " +
                "'nothing yet' apart from a month somebody marked nothing in",
        )

        val wantDays = fixture["weekdays"]!!.jsonArray.map { it.jsonObject }
        val gotDays = weekdays(shape, entries, today, 84)
        assertEquals(7, gotDays.size, "seven, always: a chart with a missing bar has lost a day")
        for ((i, d) in gotDays.withIndex()) {
            assertEquals(wantDays[i]["day"]!!.jsonPrimitive.content.toInt(), d.day, "weekday[$i]")
            assertEquals(wantDays[i]["en"]!!.jsonPrimitive.content, d.en)
            assertEquals(wantDays[i]["marked"]!!.jsonPrimitive.content.toInt(), d.marked, d.en)
            same("weekday.${d.en}", wantDays[i]["rate"]!!.jsonPrimitive.content.toDouble(), d.rate)
        }

        val wantRates = fixture["bandRates"]!!.jsonArray.map { it.jsonObject }
        val gotRates = bandRates(shape, entries, today, 84)
        assertEquals(wantRates.size, gotRates.size, "a band with no live tasks must be skipped")
        for ((i, r) in gotRates.withIndex()) {
            assertEquals(wantRates[i]["id"]!!.jsonPrimitive.content, r.id, "bandRate[$i]")
            assertEquals(wantRates[i]["marked"]!!.jsonPrimitive.content.toInt(), r.marked, r.id)
            assertEquals(wantRates[i]["tasks"]!!.jsonPrimitive.content.toInt(), r.tasks, r.id)
            same("bandRate.${r.id}", wantRates[i]["rate"]!!.jsonPrimitive.content.toDouble(), r.rate)
        }
    }

    /* ============================================================
       3. §0: nothing falls, and nothing resets
       ============================================================ */

    /* The fixture's history has a dead fortnight in it. A mean
       over calendar days drops through one; a mean over the days
       somebody marked does not. */
    @Test
    fun `a dead fortnight does not move a mean`() {
        val gap = entries.map { it.date }.sorted()
        assertTrue("2026-02-23" !in gap && "2026-03-01" !in gap, "the fortnight is not dead")

        val m = momentum(shape, entries, today, 28)
        assertTrue(
            m.now > 0.2,
            "the mean fell to ${m.now} through a quiet fortnight, which is a chart of a " +
                "life rather than of a routine",
        )
        assertTrue(m.marked in 1..27, "the window has ${m.marked} marked days of 28")
    }

    /* The counters a drawing may be hung on: no window, no
       "recently", no reset. */
    @Test
    fun `the flock and the garden only ever grow`() {
        val wantEver = fixture["everMarked"]!!.jsonObject
        assertEquals(wantEver["eng"]!!.jsonPrimitive.content.toInt(), everMarked(entries, "eng"))
        assertEquals(wantEver["tv"]!!.jsonPrimitive.content.toInt(), everMarked(entries, "tv"))

        for (case in fixture["flock"]!!.jsonArray.map { it.jsonObject }) {
            val times = case["times"]!!.jsonPrimitive.content.toInt()
            assertEquals(case["birds"]!!.jsonPrimitive.content.toInt(), flock(times), "flock($times)")
        }
        /* Monotone, which is the whole promise. */
        var last = 0
        for (n in 0..300) {
            val birds = flock(n)
            assertTrue(birds >= last, "the flock shrank between ${n - 1} and $n")
            last = birds
        }

        for (case in fixture["garden"]!!.jsonArray.map { it.jsonObject }) {
            val times = case["times"]!!.jsonPrimitive.content.toInt()
            val want = case["plants"]!!.jsonArray.map { it.jsonObject["en"]!!.jsonPrimitive.content }
            assertEquals(want, garden(times).map { it.en }, "garden($times)")
        }
        var grown = 0
        for (n in 0..200) {
            val plants = garden(n).size
            assertTrue(plants >= grown, "a plant died between ${n - 1} and $n")
            grown = plants
        }
    }

    /* A run that ended yesterday is still the current one. Without
       that line the number drops to nought at midnight and climbs
       back when somebody marks their first task at nine, which is
       a thing to be broken however carefully it is worded. */
    @Test
    fun `today is not over`() {
        val want = fixture["runs"]!!.jsonObject
        val got = runs(entries, today, 365)
        assertEquals(want["now"]!!.jsonPrimitive.content.toInt(), got.now)
        assertEquals(want["best"]!!.jsonPrimitive.content.toInt(), got.best)

        /* The fixture's history ends with four marked days and an
           unmarked today. */
        assertEquals(null, done(shape, entries.firstOrNull { it.date == today }))
        assertEquals(4, got.now, "a run that ended yesterday is still the current one")
        assertTrue(got.best >= got.now, "best standing above now is the ordinary case")
    }

    /* ============================================================
       4. Dates, the way JavaScript does them
       ============================================================ */

    @Test
    fun `a year ago today, and it prefers the oldest it can reach`() {
        val want = fixture["echo"]!!
        val got = echo(entries, today)
        assertTrue(got != null, "the fixture has a written day a year back")
        assertEquals(
            want.jsonObject["entry"]!!.jsonObject["entry_date"]!!.jsonPrimitive.content,
            got!!.entry.date,
        )
        assertEquals(want.jsonObject["en"]!!.jsonPrimitive.content, got.en)
        /* Six months back is also written, so preferring the year
           is a decision this actually tests. */
        assertEquals("A year ago today", got.en)
    }

    /* JavaScript's `setUTCMonth` OVERFLOWS rather than clamping,
       and `java.time` would give the clamped answer and be right
       about calendars and wrong about this. The 31st of March
       minus one month is the 3rd of March. */
    @Test
    fun `a month before the thirty-first is not the twenty-eighth`() {
        /* Every one of these was read off `Date.setUTCMonth` in
           node rather than reasoned about, because the whole point
           is that reasoning gives the calendar-correct answer and
           JavaScript gives a different one. */
        assertEquals("2026-03-03", shiftMonths("2026-03-31", -1), "February has no 31st")
        assertEquals("2024-03-02", shiftMonths("2024-03-31", -1), "and 29 days in a leap year")
        assertEquals("2026-03-03", shiftMonths("2026-05-31", -3), "three months, same overflow")
        assertEquals("2025-12-31", shiftMonths("2026-01-31", -1), "and back across a year")
        assertEquals("2025-03-31", shiftMonths("2026-03-31", -12), "a year back is exact")
        assertEquals("2025-09-30", shiftMonths("2026-03-30", -6))
        /* A leap day a year back, which does not exist. */
        assertEquals("2027-03-01", shiftMonths("2028-02-29", -12))
    }

    /* The day walk underneath everything else. An off-by-one here
       shifts every chart in the tool by a day, and every one of
       them would still look plausible. */
    @Test
    fun `the day walk lands where the calendar does`() {
        assertEquals("2026-03-30", dayBefore("2026-03-31", 1))
        assertEquals("2026-02-28", dayBefore("2026-03-01", 1), "2026 is not a leap year")
        assertEquals("2024-02-29", dayBefore("2024-03-01", 1), "2024 is")
        assertEquals("2025-12-31", dayBefore("2026-01-01", 1))
        assertEquals("2025-03-31", dayBefore("2026-03-31", 365))

        val week = walkBack("2026-03-31", 7)
        assertEquals(7, week.size)
        assertEquals("2026-03-25", week.first(), "oldest first, always")
        assertEquals("2026-03-31", week.last())

        /* Sunday is 0, matching `Date.getUTCDay()`, and the index
           IS the day: there is no arrangement in which Sunday's
           bar can draw Monday's figure. */
        assertEquals(0, weekdayOf("2026-03-29"), "29 March 2026 is a Sunday")
        assertEquals(2, weekdayOf("2026-03-31"))
        assertEquals(4, weekdayOf("1970-01-01"), "the epoch was a Thursday")
    }

    @Test
    fun `the six seasons, at every boundary`() {
        for (case in fixture["seasons"]!!.jsonArray.map { it.jsonObject }) {
            val date = case["date"]!!.jsonPrimitive.content
            assertEquals(case["season"]!!.jsonPrimitive.content, seasonOf(date).id, date)
        }
        /* Winter WRAPS THE YEAR: mid December to mid February is
           one season with January inside it, so anything before
           mid February is winter rather than falling off the end
           of the list. */
        assertEquals("sheet", seasonOf("2026-01-01").id)
        assertEquals("sheet", seasonOf("2026-12-31").id)
        assertEquals(6, SEASONS.size, "Bangladesh has six")
    }

    @Test
    fun `the greeting, hour by hour`() {
        for (case in fixture["greeting"]!!.jsonArray.map { it.jsonObject }) {
            val hour = case["hour"]!!.jsonPrimitive.content.toInt()
            val (bn, en) = greeting(hour)
            assertEquals(case["bn"]!!.jsonPrimitive.content, bn, "hour $hour")
            assertEquals(case["en"]!!.jsonPrimitive.content, en, "hour $hour")
        }
    }

    @Test
    fun `the days a person wrote on, newest first`() {
        assertEquals(
            fixture["written"]!!.jsonArray.map { it.jsonPrimitive.content },
            written(entries).map { it.date },
        )
    }

    @Test
    fun `the routine's own hours`() {
        val want = fixture["shapeHours"]!!.jsonObject
        val (planned, free) = hours(shape)
        same("planned", want["planned"]!!.jsonPrimitive.content.toDouble(), planned)
        same("free", want["free"]!!.jsonPrimitive.content.toDouble(), free)
        /* Leisure counts here even though it never counts towards
           progress: an hour of television is an hour of the day
           whatever else is true. */
        assertTrue(planned > shape.tasks.filter { it.counts }.sumOf { it.hours ?: 0.0 })
    }

    /* ============================================================
       5. None of the four moods is bad
       ============================================================ */

    @Test
    fun `heavy is a description rather than a failure`() {
        assertEquals(4, MOODS.size, "four, and there are deliberately no more")
        val heavy = MOODS.first { it.id == "heavy" }
        assertEquals("#6E52A8", heavy.colour, "the quiet violet, and never a red")
        for (m in MOODS) {
            assertTrue(m.bn.isNotBlank() && m.en.isNotBlank(), "${m.id} is missing a language")
            assertTrue(m.colour.startsWith("#"), "a mood's colour is DATA and travels with it")
        }
        assertEquals("", moodColour(null), "a mood nobody chose has no colour")
        assertEquals("", moodColour("nope"))
    }
}
