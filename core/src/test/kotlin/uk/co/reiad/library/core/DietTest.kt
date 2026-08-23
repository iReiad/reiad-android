package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
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
import uk.co.reiad.library.core.diet.Range
import uk.co.reiad.library.core.diet.Sex
import uk.co.reiad.library.core.diet.activityFactor
import uk.co.reiad.library.core.diet.bmi
import uk.co.reiad.library.core.diet.bmiBand
import uk.co.reiad.library.core.diet.deurenbergFat
import uk.co.reiad.library.core.diet.estimatedBurn
import uk.co.reiad.library.core.diet.fatEstimate
import uk.co.reiad.library.core.diet.ffmi
import uk.co.reiad.library.core.diet.ffmiNormalised
import uk.co.reiad.library.core.diet.floorKcal
import uk.co.reiad.library.core.diet.katch
import uk.co.reiad.library.core.diet.mifflin
import uk.co.reiad.library.core.diet.navyFat
import uk.co.reiad.library.core.diet.proteinFloor
import uk.co.reiad.library.core.diet.restingBurn
import uk.co.reiad.library.core.diet.target
import uk.co.reiad.library.core.diet.toFeetInches
import uk.co.reiad.library.core.diet.toStone
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
