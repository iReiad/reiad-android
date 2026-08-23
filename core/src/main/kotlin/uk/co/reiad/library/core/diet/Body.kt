package uk.co.reiad.library.core.diet

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/* ============================================================
   The body and the energy, ported from `shared/diet.ts`.

   `DIET.md` is the plan and that file is the original. This is a
   second implementation of the same equations, locked to
   `content/diet.fixtures.json`, and the lock matters more here
   than anywhere else in this app for one reason: **every number
   this produces is plausible**. A resting burn computed with the
   wrong sex constant is 166 kcal out, which is a fifth of a
   deficit and looks exactly like a number. Nobody reading the
   screen could tell, and neither could a check that only asked
   whether a figure appeared.

   ---- what a range means here, and why nothing is a point ----

   `FatEstimate` carries a `Range` rather than a percentage. The
   Navy tape method is three to four points against DXA and worse
   at the extremes, so a body fat percentage stated as one number
   is a false precision the site refuses to print. A port that
   flattened it to a midpoint would look tidier and would be
   telling the reader something the tool does not know.

   ---- and the one function that can refuse ----

   `target()` clamps in a fixed order and reports EVERY bound it
   hit, in the order it hit them. More than one can hold at once:
   a small person on a fast rate is capped by the rate, lands
   under their resting burn AND under the absolute floor, and a
   reader told only the last of the three has been told the wrong
   thing.
   ============================================================ */

enum class Sex(val id: String) { MALE("male"), FEMALE("female") }

/**
 * Which set of BMI cut-offs.
 *
 * `general` is the familiar 25 and 30, derived from European
 * populations; `asian` is the WHO's 2004 action points. A tool
 * serving Bangladesh that quietly used the first would tell a
 * large number of its readers they are fine when their own health
 * service would not.
 */
enum class Ancestry(val id: String) { GENERAL("general"), ASIAN("asian") }

enum class GoalKind(val id: String) { LOSE("lose"), MAINTAIN("maintain"), GAIN("gain") }

/** A figure with its error bars attached. Never flattened. */
data class Range(val low: Double, val mid: Double, val high: Double)

internal fun rangeOf(mid: Double, half: Double) = Range(mid - half, mid, mid + half)

/**
 * What the reader has told the tool about their body.
 *
 * Only the first four are ever required. Everything else unlocks
 * a better estimate and its absence is answered with `null`
 * rather than with a guess, which is the difference between this
 * and a tool that fills in an average waist.
 */
data class Body(
    val heightCm: Double,
    val weightKg: Double,
    val ageYears: Double,
    val sex: Sex,
    val ancestry: Ancestry,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val neckCm: Double? = null,
)

/* ---------------------------------------------------------- */
/* BMI, and the two sets of cut-offs                          */
/* ---------------------------------------------------------- */

enum class BmiBand(val id: String) {
    UNDER("under"), HEALTHY("healthy"), RAISED("raised"), HIGH("high")
}

data class BmiCuts(val under: Double, val raised: Double, val high: Double)

val BMI_CUTS: Map<Ancestry, BmiCuts> = mapOf(
    Ancestry.GENERAL to BmiCuts(18.5, 25.0, 30.0),
    Ancestry.ASIAN to BmiCuts(18.5, 23.0, 27.5),
)

fun bmi(weightKg: Double, heightCm: Double): Double =
    weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

fun bmiBand(value: Double, ancestry: Ancestry): BmiBand {
    val cut = BMI_CUTS.getValue(ancestry)
    return when {
        value < cut.under -> BmiBand.UNDER
        value < cut.raised -> BmiBand.HEALTHY
        value < cut.high -> BmiBand.RAISED
        else -> BmiBand.HIGH
    }
}

/* ---------------------------------------------------------- */
/* waist to height, which leads                               */
/* ---------------------------------------------------------- */

enum class WhtrBand(val id: String) {
    LOW("low"), HEALTHY("healthy"), RAISED("raised"), HIGH("high")
}

/** Waist over height, same units. It needs one tape measure and
    no assumption about population, which is exactly the property
    BMI lacks, so it is the number shown first. */
fun whtr(waistCm: Double, heightCm: Double): Double = waistCm / heightCm

fun whtrBand(value: Double): WhtrBand = when {
    value < 0.4 -> WhtrBand.LOW
    value < 0.5 -> WhtrBand.HEALTHY
    value < 0.6 -> WhtrBand.RAISED
    else -> WhtrBand.HIGH
}

/* ---------------------------------------------------------- */
/* body fat, with its error bars visible                      */
/* ---------------------------------------------------------- */

enum class FatMethod(val id: String) { NAVY("navy"), DEURENBERG("deurenberg") }

data class FatEstimate(
    val method: FatMethod,
    /** Percentage points, one standard error. */
    val se: Double,
    val pct: Range,
    val leanKg: Double,
    val fatKg: Double,
)

/** At the same BMI, South Asians carry several points more fat
    than white Europeans, which is the same finding the lower BMI
    cut-off rests on. Applied to Deurenberg ONLY: the Navy method
    measures the body rather than inferring from mass, so it does
    not inherit the bias. */
private const val DEURENBERG_ASIAN = 3.5

/**
 * The tape method.
 *
 * Needs neck and waist for men, plus hips for women, and that
 * asymmetry is the one place the two sexes take different
 * arguments. Null rather than a guess when a measurement is
 * missing, and null when the logarithm's argument is not
 * positive, which happens with a mistyped tape rather than with a
 * real body.
 */
fun navyFat(b: Body): Double? {
    val waist = b.waistCm ?: return null
    val neck = b.neckCm ?: return null
    if (waist <= 0 || neck <= 0 || b.heightCm <= 0) return null
    if (b.sex == Sex.MALE) {
        val girth = waist - neck
        if (girth <= 0) return null
        return 495.0 / (1.0324 - 0.19077 * log10(girth) + 0.15456 * log10(b.heightCm)) - 450.0
    }
    val hip = b.hipCm ?: return null
    val girth = waist + hip - neck
    if (girth <= 0) return null
    return 495.0 / (1.29579 - 0.35004 * log10(girth) + 0.22100 * log10(b.heightCm)) - 450.0
}

/** From BMI, for a reader with no tape. Worse, and it inherits
    every problem BMI has, which is why it is second. */
fun deurenbergFat(b: Body): Double {
    val male = if (b.sex == Sex.MALE) 1.0 else 0.0
    val base = 1.20 * bmi(b.weightKg, b.heightCm) + 0.23 * b.ageYears - 10.8 * male - 5.4
    return if (b.ancestry == Ancestry.ASIAN) base + DEURENBERG_ASIAN else base
}

/** The tape if there is one, the equation if not, and the method
    NAMED in the result so the page can say which it used. */
fun fatEstimate(b: Body): FatEstimate {
    val tape = navyFat(b)
    val method = if (tape == null) FatMethod.DEURENBERG else FatMethod.NAVY
    val pct = tape ?: deurenbergFat(b)
    val se = if (method == FatMethod.NAVY) 3.5 else 5.0
    val clamped = min(max(pct, 2.0), 70.0)
    val fatKg = b.weightKg * clamped / 100.0
    return FatEstimate(
        method = method,
        se = se,
        pct = rangeOf(clamped, se),
        leanKg = b.weightKg - fatKg,
        fatKg = fatKg,
    )
}

/** Fat free mass index: lean mass over height squared. The number
    that tells a lifter their BMI is lying. */
fun ffmi(leanKg: Double, heightCm: Double): Double =
    leanKg / ((heightCm / 100.0) * (heightCm / 100.0))

/** Adjusted to a 1.8m frame, which is how the published reference
    values are stated: without it a short lifter and a tall one
    cannot be read off the same table. */
fun ffmiNormalised(leanKg: Double, heightCm: Double): Double =
    ffmi(leanKg, heightCm) + 6.1 * (1.8 - heightCm / 100.0)

/* ---------------------------------------------------------- */
/* energy: the estimate, then the measurement                 */
/* ---------------------------------------------------------- */

data class ActivityLevel(
    val key: String,
    val factor: Double,
    val en: String,
    val bn: String,
)

/** A starting guess and nothing more. Self-reported activity is
    optimistic and self-reported intake is under-recorded, and both
    errors push the same way, which is the entire reason the site's
    `learnedBurn()` exists. */
val ACTIVITY: List<ActivityLevel> = listOf(
    ActivityLevel("sedentary", 1.20, "Desk work, little walking", "বসে কাজ, হাঁটা কম"),
    ActivityLevel("light", 1.375, "Light activity most days", "প্রতিদিন হালকা চলাফেরা"),
    ActivityLevel(
        "moderate", 1.55,
        "On your feet, or training three times a week",
        "দাঁড়িয়ে কাজ, বা সপ্তাহে তিন দিন ব্যায়াম",
    ),
    ActivityLevel(
        "active", 1.725,
        "Physical work, or training most days",
        "শারীরিক কাজ, বা প্রায় রোজ ব্যায়াম",
    ),
    ActivityLevel("very", 1.90, "Heavy physical work and training", "ভারী শারীরিক কাজ ও ব্যায়াম"),
)

fun activityFactor(key: String): Double =
    ACTIVITY.firstOrNull { it.key == key }?.factor ?: 1.2

fun mifflin(b: Body): Double =
    10 * b.weightKg + 6.25 * b.heightCm - 5 * b.ageYears + (if (b.sex == Sex.MALE) 5.0 else -161.0)

fun katch(leanKg: Double): Double = 370.0 + 21.6 * leanKg

enum class RestingMethod(val id: String) { MIFFLIN("mifflin"), KATCH("katch") }

data class Resting(val kcal: Double, val method: RestingMethod)

/** Katch the moment lean mass is known, because it works FROM
    lean mass and therefore does not have to guess at composition.
    Mifflin otherwise, being the best validated equation for a
    general population. */
fun restingBurn(b: Body, leanKg: Double? = null): Resting =
    if (leanKg != null && leanKg > 0) {
        Resting(katch(leanKg), RestingMethod.KATCH)
    } else {
        Resting(mifflin(b), RestingMethod.MIFFLIN)
    }

fun estimatedBurn(restingKcal: Double, factor: Double): Double = restingKcal * factor

/* ---------------------------------------------------------- */
/* the goal engine, and the floors it will not cross          */
/* ---------------------------------------------------------- */

/* `KCAL_PER_KG` is declared in `Trend.kt`, where the site
   declares it too: it is the constant the learned maintenance is
   made of and the goal engine is the second reader. One
   declaration, because 7700 typed twice is 7700 in one place the
   day somebody changes the other. */

data class Rate(
    val key: String,
    /** Percent of bodyweight per week. A PERCENTAGE rather than a
        number of kilos, because half a kilo a week is gentle at
        110kg and severe at 55kg. */
    val low: Double,
    val high: Double,
    val en: String,
    val bn: String,
)

val RATES: List<Rate> = listOf(
    Rate("gentle", 0.25, 0.5, "Gentle", "ধীরে"),
    Rate("standard", 0.5, 0.75, "Steady", "মাঝারি"),
    Rate("hard", 0.75, 1.0, "Fast", "দ্রুত"),
)

/** A ceiling, and the reason is medical rather than
    motivational: loss faster than about 1.5kg a week measurably
    raises the risk of gallstones, and the people most likely to
    try it are already the people most at risk. */
const val MAX_LOSS_PCT_PER_WEEK = 1.0

/** A surplus above roughly this adds fat faster than any body
    adds muscle, whatever the training. */
const val MAX_GAIN_PCT_PER_WEEK = 0.5

/** The absolute stop. Below this the tool declines and says why.
    It is not a warning and there is no argument that lifts it. */
fun floorKcal(sex: Sex): Int = if (sex == Sex.MALE) 1500 else 1200

/** No loss goal at all below this, on either set of cut-offs. */
const val NO_LOSS_BELOW_BMI = 18.5

enum class FloorHit(val id: String) {
    ABSOLUTE("absolute"), RESTING("resting"), RATE("rate"), UNDERWEIGHT("underweight")
}

data class Target(
    val kcal: Int,
    /** Signed against maintenance. Negative is a deficit. */
    val offset: Int,
    /** EVERY bound that bound, in the order they were applied,
        and empty when the requested rate was deliverable. */
    val floors: List<FloorHit>,
    /** What the rate actually works out at after any clamping. */
    val ratePct: Double,
)

/**
 * The one function here that can refuse.
 *
 * It clamps in a fixed order and reports which bounds it hit,
 * because "we gave you 1500 instead of 1100" is a fact the reader
 * needs and a silent clamp is a lie of omission.
 *
 * The floors are a LIST rather than one value, and the fixture's
 * "small and in a hurry" case is why: a 48kg woman asking for 2%
 * a week hits the rate cap, the resting burn and the absolute
 * floor, and reporting only the last would tell her the wrong
 * one.
 */
fun target(
    body: Body,
    /** Maintenance: the learned figure where there is one, the
        estimate before that. */
    maintenance: Double,
    restingKcal: Double,
    kind: GoalKind,
    /** Requested percent of bodyweight per week. */
    ratePct: Double,
): Target {
    val value = bmi(body.weightKg, body.heightCm)

    if (kind == GoalKind.MAINTAIN) {
        return Target(jsRound(maintenance), 0, emptyList(), 0.0)
    }

    if (kind == GoalKind.LOSE && value < NO_LOSS_BELOW_BMI) {
        return Target(jsRound(maintenance), 0, listOf(FloorHit.UNDERWEIGHT), 0.0)
    }

    val cap = if (kind == GoalKind.LOSE) MAX_LOSS_PCT_PER_WEEK else MAX_GAIN_PCT_PER_WEEK
    val floors = mutableListOf<FloorHit>()
    var rate = abs(ratePct)
    if (rate > cap) {
        rate = cap
        floors += FloorHit.RATE
    }

    val kgPerWeek = body.weightKg * rate / 100.0
    val perDay = kgPerWeek * KCAL_PER_KG / 7.0

    var kcal = if (kind == GoalKind.LOSE) maintenance - perDay else maintenance + perDay
    if (kind == GoalKind.LOSE) {
        if (kcal < restingKcal) {
            kcal = restingKcal
            floors += FloorHit.RESTING
        }
        val hard = floorKcal(body.sex).toDouble()
        if (kcal < hard) {
            kcal = hard
            floors += FloorHit.ABSOLUTE
        }
    }

    val delivered = abs(kcal - maintenance) * 7 / KCAL_PER_KG
    return Target(
        kcal = jsRound(kcal),
        offset = jsRound(kcal - maintenance),
        floors = floors,
        ratePct = if (body.weightKg > 0) delivered / body.weightKg * 100.0 else 0.0,
    )
}

/**
 * Grams per kilogram of LEAN mass, rising with the depth of the
 * deficit, because protein is what decides whether the weight lost
 * is fat or muscle.
 *
 * A range: the low end is the floor and the high end is where
 * there is nothing further to gain.
 */
fun proteinFloor(leanKg: Double, ratePct: Double): Range {
    val span = min(max(ratePct, 0.25), 1.0)
    val perKg = 1.6 + ((span - 0.25) / 0.75) * 0.6
    return Range(leanKg * 1.6, leanKg * perKg, leanKg * 2.2)
}

/* ---------------------------------------------------------- */
/* units, because half the readers are in stone               */
/* ---------------------------------------------------------- */

const val KG_PER_LB = 0.45359237

data class Stone(val st: Int, val lb: Double)

fun toStone(kg: Double): Stone {
    val total = kg / KG_PER_LB
    val st = floor(total / 14).toInt()
    return Stone(st, jsRound((total - st * 14) * 10) / 10.0)
}

/** `12 st 4 lb`, never `12.3 st`, which is a number no British
    person has ever said out loud. */
fun stoneLabel(kg: Double): String {
    val (st, lb) = toStone(kg)
    return "$st st ${jsRound(lb)} lb"
}

data class FeetInches(val ft: Int, val inch: Double)

fun toFeetInches(cm: Double): FeetInches {
    val total = cm / 2.54
    val ft = floor(total / 12).toInt()
    return FeetInches(ft, jsRound((total - ft * 12) * 10) / 10.0)
}

/**
 * `Math.round`, which is NOT `roundToInt`.
 *
 * JavaScript rounds half UP, towards positive infinity, so
 * `Math.round(-0.5)` is `-0` and `Math.round(-1.5)` is `-1`.
 * Kotlin's `roundToInt` rounds half AWAY FROM ZERO, so the same
 * two are `-1` and `-2`. Every figure here that can be negative
 * goes through this: `offset` is negative on every deficit, which
 * is the common case rather than an edge one.
 */
internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
