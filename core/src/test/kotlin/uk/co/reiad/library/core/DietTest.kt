package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import uk.co.reiad.library.core.diet.ACTIVITY
import uk.co.reiad.library.core.diet.Ancestry
import uk.co.reiad.library.core.diet.BMI_CUTS
import uk.co.reiad.library.core.diet.Body
import uk.co.reiad.library.core.diet.GoalKind
import uk.co.reiad.library.core.diet.Intake
import uk.co.reiad.library.core.diet.KCAL_PER_KG
import uk.co.reiad.library.core.diet.LEARN_AFTER_DAYS
import uk.co.reiad.library.core.diet.Point
import uk.co.reiad.library.core.diet.Range
import uk.co.reiad.library.core.diet.Sex
import uk.co.reiad.library.core.diet.TREND_HALF_LIFE_DAYS
import uk.co.reiad.library.core.diet.UNLOGGED_SE_SHARE
import uk.co.reiad.library.core.diet.activityFactor
import uk.co.reiad.library.core.diet.bmi
import uk.co.reiad.library.core.diet.bmiBand
import uk.co.reiad.library.core.diet.deurenbergFat
import uk.co.reiad.library.core.diet.estimatedBurn
import uk.co.reiad.library.core.diet.fatEstimate
import uk.co.reiad.library.core.diet.ffmi
import uk.co.reiad.library.core.diet.ffmiNormalised
import uk.co.reiad.library.core.diet.fit
import uk.co.reiad.library.core.diet.floorKcal
import uk.co.reiad.library.core.diet.katch
import uk.co.reiad.library.core.diet.learnedBurn
import uk.co.reiad.library.core.diet.mifflin
import uk.co.reiad.library.core.diet.navyFat
import uk.co.reiad.library.core.diet.proteinFloor
import uk.co.reiad.library.core.diet.restingBurn
import uk.co.reiad.library.core.diet.slopePerWeek
import uk.co.reiad.library.core.diet.target
import uk.co.reiad.library.core.diet.toFeetInches
import uk.co.reiad.library.core.diet.toStone
import uk.co.reiad.library.core.diet.trend
import uk.co.reiad.library.core.diet.whtr
import uk.co.reiad.library.core.diet.whtrBand

/* ============================================================
   The body and the energy, against the site's own numbers.

   `content/diet.fixtures.json` is written by
   `scripts/export-diet-fixtures.ts` from `shared/diet.ts`, and
   every value below is compared to it rather than to a number
   typed here. That is the whole point: a number typed here would
   agree with the site on the day it was typed.

   ---- and the tolerance is deliberately tiny ----

   1e-9. These are the same equations over the same doubles, so
   anything larger would be hiding a real difference: the failure
   this is written against is not a rounding wobble, it is a
   constant transposed, a table read for the wrong ancestry, or
   `roundToInt` where JavaScript rounds half up.
   ============================================================ */
class DietTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val root: JsonObject = json.parseToJsonElement(
        requireNotNull(javaClass.getResourceAsStream("/fixtures/diet.json")) {
            "fixtures/diet.json is missing. It is a copy of " +
                "content/diet.fixtures.json in the website repository."
        }.readBytes().decodeToString(),
    ).jsonObject

    private val cases = root.getValue("cases").jsonObject

    private fun near(want: Double, got: Double, what: String) {
        assertTrue(
            abs(want - got) < 1e-9,
            "$what: the site says $want and this says $got",
        )
    }

    private fun nearRange(want: JsonObject, got: Range, what: String) {
        near(want.getValue("low").jsonPrimitive.double, got.low, "$what.low")
        near(want.getValue("mid").jsonPrimitive.double, got.mid, "$what.mid")
        near(want.getValue("high").jsonPrimitive.double, got.high, "$what.high")
    }

    private fun bodyOf(o: JsonObject) = Body(
        heightCm = o.getValue("heightCm").jsonPrimitive.double,
        weightKg = o.getValue("weightKg").jsonPrimitive.double,
        ageYears = o.getValue("ageYears").jsonPrimitive.double,
        sex = if (o.getValue("sex").jsonPrimitive.content == "male") Sex.MALE else Sex.FEMALE,
        ancestry = if (o.getValue("ancestry").jsonPrimitive.content == "asian") {
            Ancestry.ASIAN
        } else {
            Ancestry.GENERAL
        },
        waistCm = o["waistCm"]?.jsonPrimitive?.double,
        hipCm = o["hipCm"]?.jsonPrimitive?.double,
        neckCm = o["neckCm"]?.jsonPrimitive?.double,
    )

    /** The tables, not only the answers. A port that computed
        every case correctly off a cut-off it had copied wrongly is
        a port that is right today and wrong the day one moves. */
    @Test fun theTablesAgree() {
        val cuts = root.getValue("cuts").jsonObject
        for ((id, ancestry) in listOf("general" to Ancestry.GENERAL, "asian" to Ancestry.ASIAN)) {
            val want = cuts.getValue(id).jsonObject
            val have = BMI_CUTS.getValue(ancestry)
            near(want.getValue("under").jsonPrimitive.double, have.under, "$id.under")
            near(want.getValue("raised").jsonPrimitive.double, have.raised, "$id.raised")
            near(want.getValue("high").jsonPrimitive.double, have.high, "$id.high")
        }

        val activity = root.getValue("activity").jsonArray
        assertEquals(activity.size, ACTIVITY.size, "the activity ladder has a different length")
        for ((at, row) in activity.withIndex()) {
            val want = row.jsonObject
            val have = ACTIVITY[at]
            assertEquals(want.getValue("key").jsonPrimitive.content, have.key)
            near(want.getValue("factor").jsonPrimitive.double, have.factor, "${have.key}.factor")
            assertEquals(want.getValue("en").jsonPrimitive.content, have.en, "${have.key}.en")
            assertEquals(want.getValue("bn").jsonPrimitive.content, have.bn, "${have.key}.bn")
        }
    }

    @Test fun everyCaseAgrees() {
        assertTrue(cases.size >= 10, "the fixture should hold every branch, not a happy path")
        for ((name, raw) in cases) {
            val c = raw.jsonObject
            val body = bodyOf(c.getValue("body").jsonObject)
            val lean = c["leanKg"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

            near(c.getValue("bmi").jsonPrimitive.double, bmi(body.weightKg, body.heightCm), "$name.bmi")
            assertEquals(
                c.getValue("bmiBand").jsonPrimitive.content,
                bmiBand(bmi(body.weightKg, body.heightCm), body.ancestry).id,
                "$name.bmiBand",
            )

            val waist = body.waistCm
            if (waist == null) {
                assertTrue(c["whtr"]?.jsonPrimitive?.contentOrNull == null, "$name.whtr should be null")
            } else {
                near(c.getValue("whtr").jsonPrimitive.double, whtr(waist, body.heightCm), "$name.whtr")
                assertEquals(
                    c.getValue("whtrBand").jsonPrimitive.content,
                    whtrBand(whtr(waist, body.heightCm)).id,
                    "$name.whtrBand",
                )
            }

            /* Null is an ANSWER here rather than a failure: the
               tape method refuses without a measurement instead
               of guessing one, and a port that returned a number
               would be inventing a body. */
            val wantNavy = c["navyFat"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val gotNavy = navyFat(body)
            if (wantNavy == null) {
                assertTrue(gotNavy == null, "$name.navyFat should be null and is $gotNavy")
            } else {
                near(wantNavy, assertNotNull(gotNavy), "$name.navyFat")
            }

            near(
                c.getValue("deurenbergFat").jsonPrimitive.double,
                deurenbergFat(body),
                "$name.deurenbergFat",
            )

            val wantFat = c.getValue("fat").jsonObject
            val fat = fatEstimate(body)
            assertEquals(wantFat.getValue("method").jsonPrimitive.content, fat.method.id, "$name.fat.method")
            near(wantFat.getValue("se").jsonPrimitive.double, fat.se, "$name.fat.se")
            nearRange(wantFat.getValue("pct").jsonObject, fat.pct, "$name.fat.pct")
            near(wantFat.getValue("leanKg").jsonPrimitive.double, fat.leanKg, "$name.fat.leanKg")
            near(wantFat.getValue("fatKg").jsonPrimitive.double, fat.fatKg, "$name.fat.fatKg")

            if (lean != null) {
                near(c.getValue("ffmi").jsonPrimitive.double, ffmi(lean, body.heightCm), "$name.ffmi")
                near(
                    c.getValue("ffmiNormalised").jsonPrimitive.double,
                    ffmiNormalised(lean, body.heightCm),
                    "$name.ffmiNormalised",
                )
                near(c.getValue("katch").jsonPrimitive.double, katch(lean), "$name.katch")
            }

            near(c.getValue("mifflin").jsonPrimitive.double, mifflin(body), "$name.mifflin")

            val wantResting = c.getValue("resting").jsonObject
            val resting = restingBurn(body, lean)
            near(wantResting.getValue("kcal").jsonPrimitive.double, resting.kcal, "$name.resting.kcal")
            assertEquals(
                wantResting.getValue("method").jsonPrimitive.content,
                resting.method.id,
                "$name.resting.method",
            )

            val factor = activityFactor(c.getValue("activity").jsonPrimitive.content)
            near(c.getValue("factor").jsonPrimitive.double, factor, "$name.factor")
            near(
                c.getValue("estimatedBurn").jsonPrimitive.double,
                estimatedBurn(resting.kcal, factor),
                "$name.estimatedBurn",
            )
            assertEquals(
                c.getValue("floorKcal").jsonPrimitive.int,
                floorKcal(body.sex),
                "$name.floorKcal",
            )

            val stone = c.getValue("stone").jsonObject
            assertEquals(stone.getValue("st").jsonPrimitive.int, toStone(body.weightKg).st, "$name.stone.st")
            near(stone.getValue("lb").jsonPrimitive.double, toStone(body.weightKg).lb, "$name.stone.lb")
            val feet = c.getValue("feetInches").jsonObject
            assertEquals(feet.getValue("ft").jsonPrimitive.int, toFeetInches(body.heightCm).ft, "$name.ft")
            near(feet.getValue("inch").jsonPrimitive.double, toFeetInches(body.heightCm).inch, "$name.inch")
        }
    }

    /**
     * The goal engine, and every floor it hit, in order.
     *
     * Split from the case above because this is the one function
     * in the file that can REFUSE, and because the floors are a
     * list: a port that reported the last one would pass an
     * assertion written as "did it clamp".
     */
    @Test fun everyTargetAgrees() {
        for ((name, raw) in cases) {
            val c = raw.jsonObject
            val body = bodyOf(c.getValue("body").jsonObject)
            val lean = c["leanKg"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            val resting = restingBurn(body, lean)
            val factor = activityFactor(c.getValue("activity").jsonPrimitive.content)
            val want = c.getValue("target").jsonObject

            /* The maintenance the fixture used: an explicit one
               where the case set it, the estimate otherwise. It
               has to be the same figure or every number below
               differs for a reason that is not a bug. */
            val maintenance = maintenanceFor(name, estimatedBurn(resting.kcal, factor))

            val got = target(
                body = body,
                maintenance = maintenance,
                restingKcal = resting.kcal,
                kind = kindFor(name),
                ratePct = rateFor(name),
            )
            assertEquals(want.getValue("kcal").jsonPrimitive.int, got.kcal, "$name.target.kcal")
            assertEquals(want.getValue("offset").jsonPrimitive.int, got.offset, "$name.target.offset")
            assertEquals(
                want.getValue("floors").jsonArray.map { it.jsonPrimitive.content },
                got.floors.map { it.id },
                "$name.target.floors, in the order they were hit",
            )
            near(want.getValue("ratePct").jsonPrimitive.double, got.ratePct, "$name.target.ratePct")

            /* Absent for a case with no lean mass, and it is
               written as JSON null rather than left out, so the
               test asks for the OBJECT rather than for a
               primitive: `jsonPrimitive` on an object throws
               rather than answering null. */
            val wantProtein = c["proteinFloor"] as? JsonObject
            if (lean != null && wantProtein != null) {
                nearRange(wantProtein, proteinFloor(lean, rateFor(name)), "$name.protein")
            }
        }
    }

    /* The two inputs the fixture does not write out as fields,
       because they are the CASE rather than the answer. Named
       here so the test drives the same numbers the exporter did;
       a mismatch shows up as every figure in that case differing
       at once, which is unmistakable. */
    private fun rateFor(name: String): Double = when (name) {
        "middle" -> 0.5
        "the two tables disagree" -> 0.5
        "the same body, general" -> 0.5
        "no tape" -> 0.0
        "a woman with the full tape" -> 0.75
        "lean mass known" -> 0.25
        "small and in a hurry" -> 2.0
        "already underweight" -> 0.5
        "waist to height, low" -> 0.5
        "waist to height, high" -> 1.0
        "maintenance, which is a phase" -> 0.0
        else -> error("no rate for the case '$name'; add it beside the exporter's")
    }

    /* --------------------------------------------------------
       The trend, the fit and the learned maintenance
       -------------------------------------------------------- */

    private val histories = root.getValue("histories").jsonObject

    private fun pointsOf(a: JsonArray) = a.map {
        val o = it.jsonObject
        Point(o.getValue("day").jsonPrimitive.int, o.getValue("kg").jsonPrimitive.double)
    }

    private fun intakesOf(a: JsonArray) = a.map {
        val o = it.jsonObject
        Intake(o.getValue("day").jsonPrimitive.int, o.getValue("kcal").jsonPrimitive.double)
    }

    /** The four numbers the trend is made of, by name.

        Every one of them is already implied by a history below,
        and asserting them anyway is what turns "a history
        disagrees" into "the half-life is wrong": a port that read
        the fortnight as `<=` and one that seeded the average from
        the wrong reading both fail the same case otherwise, and
        whoever reads the failure has to work out which. */
    @Test fun theTrendConstantsAgree() {
        val k = root.getValue("constants").jsonObject
        near(
            k.getValue("trendHalfLifeDays").jsonPrimitive.double,
            TREND_HALF_LIFE_DAYS,
            "trendHalfLifeDays",
        )
        near(k.getValue("kcalPerKg").jsonPrimitive.double, KCAL_PER_KG, "kcalPerKg")
        assertEquals(
            k.getValue("learnAfterDays").jsonPrimitive.int,
            LEARN_AFTER_DAYS,
            "learnAfterDays",
        )
        near(
            k.getValue("unloggedSeShare").jsonPrimitive.double,
            UNLOGGED_SE_SHARE,
            "unloggedSeShare",
        )
    }

    /** Every reading of every history, point by point.

        The pair that matters is "every morning" against "eight
        readings in four weeks": the same underlying line, one
        weighed daily and one on uneven gaps. A trend weighted by
        ROW rather than by elapsed time agrees with the site on the
        first and disagrees on the second, which is the only way to
        catch it, because both lines look right. */
    @Test fun everyTrendAgrees() {
        assertTrue(histories.size >= 7, "the fixture should hold every edge, not a happy path")
        for ((name, raw) in histories) {
            val h = raw.jsonObject
            val weights = pointsOf(h.getValue("weights").jsonArray)
            val want = h.getValue("trend").jsonArray
            val got = trend(weights)
            assertEquals(want.size, got.size, "$name.trend has a different number of points")
            for ((at, row) in want.withIndex()) {
                val o = row.jsonObject
                assertEquals(
                    o.getValue("day").jsonPrimitive.int,
                    got[at].day,
                    "$name.trend[$at].day",
                )
                near(o.getValue("kg").jsonPrimitive.double, got[at].kg, "$name.trend[$at].kg")
            }
        }
    }

    /** The fit and the weekly slope, including the two the site
        answers null for.

        Null is an ANSWER here, exactly as it is for the tape
        method above: two readings have no residual to measure, so
        a slope from them would be a rate with no error bar, and
        this file refuses those everywhere. A port that returned
        one would look better and be worse. */
    @Test fun everyFitAgrees() {
        for ((name, raw) in histories) {
            val h = raw.jsonObject
            val weights = pointsOf(h.getValue("weights").jsonArray)

            val wantFit = h.getValue("fit")
            val got = fit(weights)
            if (wantFit !is JsonObject) {
                assertTrue(got == null, "$name.fit should be null and is $got")
            } else {
                val o = wantFit
                val f = assertNotNull(got, "$name.fit should not be null")
                near(o.getValue("slope").jsonPrimitive.double, f.slope, "$name.fit.slope")
                near(
                    o.getValue("intercept").jsonPrimitive.double,
                    f.intercept,
                    "$name.fit.intercept",
                )
                near(o.getValue("se").jsonPrimitive.double, f.se, "$name.fit.se")
                assertEquals(o.getValue("n").jsonPrimitive.int, f.n, "$name.fit.n")
            }

            val wantSlope = h.getValue("slopePerWeek")
            val gotSlope = slopePerWeek(weights)
            if (wantSlope !is JsonObject) {
                assertTrue(gotSlope == null, "$name.slopePerWeek should be null")
            } else {
                nearRange(wantSlope, assertNotNull(gotSlope), "$name.slopePerWeek")
            }
        }
    }

    /** The learned maintenance, and the band around it.

        Two are null on purpose and they fail differently. "two
        readings" is under the three a residual needs. "one day
        short of a fortnight" is thirteen days against a
        `LEARN_AFTER_DAYS` of fourteen.

        THE THRESHOLD IS PINNED FROM BOTH SIDES, and the first
        draft of this pinned it from one. A single window thirteen
        days wide is null under `<` and null under `<=` alike, so
        it asserted that a threshold exists and nothing about where
        it is: reversing the comparison here failed nothing. "a
        fortnight exactly" is the fourteen-day window that ANSWERS,
        and it is the case that catches it.

        And "three days logged in twenty" is the third error term:
        its band is forty times wider than the daily case's, on
        intakes that never vary, because the days that are not
        there are a gap where the mean might not be rather than
        noise around one. A port that added two errors in
        quadrature instead of three produces a number the reader
        would be right to believe and should not. */
    @Test fun everyLearnedBurnAgrees() {
        var nulls = 0
        for ((name, raw) in histories) {
            val h = raw.jsonObject
            val weights = pointsOf(h.getValue("weights").jsonArray)
            val intakes = intakesOf(h.getValue("intakes").jsonArray)
            val want = h.getValue("learned")
            val got = learnedBurn(weights, intakes)
            if (want !is JsonObject) {
                nulls += 1
                assertTrue(got == null, "$name.learned should be null and is $got")
                continue
            }
            val l = assertNotNull(got, "$name.learned should not be null")
            nearRange(want.getValue("kcal").jsonObject, l.kcal, "$name.learned.kcal")
            assertEquals(want.getValue("days").jsonPrimitive.int, l.days, "$name.learned.days")
            assertEquals(want.getValue("logged").jsonPrimitive.int, l.logged, "$name.learned.logged")
            near(
                want.getValue("meanIntake").jsonPrimitive.double,
                l.meanIntake,
                "$name.learned.meanIntake",
            )
            near(
                want.getValue("trendKgPerWeek").jsonPrimitive.double,
                l.trendKgPerWeek,
                "$name.learned.trendKgPerWeek",
            )
        }
        assertEquals(
            2,
            nulls,
            "two of the histories are under a threshold on purpose; if this is not two, " +
                "the fixture lost an edge case rather than the port passing it",
        )
    }

    /** The sparse case widens the band, and by how much.

        Asserted as a RELATION rather than as a number, because the
        numbers are already checked above and what this is really
        holding is the reason the third error term exists. If a
        future change made a reader who logs three days in twenty
        as confident as one who logs every morning, every figure
        above would still agree with the site and the tool would
        still be lying. */
    @Test fun sparseLoggingIsLessConfident() {
        fun bandOf(name: String): Double {
            val h = histories.getValue(name).jsonObject
            val l = assertNotNull(
                learnedBurn(
                    pointsOf(h.getValue("weights").jsonArray),
                    intakesOf(h.getValue("intakes").jsonArray),
                ),
            )
            return l.kcal.high - l.kcal.low
        }
        val dense = bandOf("every morning")
        val sparse = bandOf("three days logged in twenty")
        assertTrue(
            sparse > dense * 10,
            "logging three days in twenty should be far less certain than logging all " +
                "of them: the dense band is $dense kcal and the sparse one is $sparse",
        )
    }

    /** The goal each case asked for. Named rather than inferred
        from the answer: a test that read the KIND out of the
        result it is checking would agree with any result. */
    private fun kindFor(name: String): GoalKind = when (name) {
        "no tape" -> GoalKind.MAINTAIN
        "maintenance, which is a phase" -> GoalKind.MAINTAIN
        "lean mass known" -> GoalKind.GAIN
        "waist to height, low" -> GoalKind.GAIN
        else -> GoalKind.LOSE
    }

    private fun maintenanceFor(name: String, estimate: Double): Double =
        if (name == "maintenance, which is a phase") 2100.0 else estimate
}
