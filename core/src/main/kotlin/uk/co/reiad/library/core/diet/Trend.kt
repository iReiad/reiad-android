package uk.co.reiad.library.core.diet

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/* ============================================================
   The trend, the fit and the learned maintenance.

   The other half of `shared/diet.ts`, ported the same way
   `Body.kt` was: every constant named, every number asserted
   against `content/diet.fixtures.json` at 1e-9 by `DietTest`.

   ---- what a wrong port of this looks like ----

   Nothing. That is the reason the fixtures exist. A trend
   weighted by ROW instead of by elapsed time draws a line that
   is smooth, plausible and confidently wrong; a slope fitted to
   the trend instead of to the readings understates a real loss
   by roughly a third and does it in the FLATTERING direction.
   Neither shows up in a screenshot, and both are what somebody
   writes if they read the formula rather than the comment above
   it.

   Two things here are deliberately not idiomatic Kotlin and
   both are about matching the site bit for bit:

   - `ln(2)` is written out as a literal, because JavaScript's
     `Math.LN2` is a specified constant and `Math.log` is only
     required to be within one ulp. They agree today on both
     runtimes; a literal cannot stop agreeing.
   - every sum is an explicit loop in the array's own order.
     Floating-point addition is not associative, so a `sumOf`
     that a future Kotlin release parallelises would change an
     answer this file promises is unchanged.
   ============================================================ */

/**
 * One reading. `day` is a whole number of days from any fixed
 * origin: the caller decides which, and this file never touches a
 * clock, so a test can seed it and a screen can seed it and both
 * get the same answer.
 */
data class Point(val day: Int, val kg: Double)

/** `Math.LN2`. Written out: see the note at the top of the file. */
private const val LN2 = 0.6931471805599453

/**
 * About a week, which puts the daily weight on roughly a tenth of
 * each new reading. Named rather than typed into `trend()` because
 * `DIET.md` states it and the fixture pins it.
 */
const val TREND_HALF_LIFE_DAYS = 7.0

/**
 * An exponentially weighted moving average, weighted by ELAPSED
 * TIME rather than by row.
 *
 * That is the whole difference between this and the version every
 * tracker ships. Weighting by row treats a reading three weeks
 * after the last one as the next day's, so a reader who weighs
 * three times a week gets a trend that is confidently wrong
 * instead of correct with a wider band. Seeded from the first
 * reading, so the first point is itself.
 */
fun trend(points: List<Point>, halfLifeDays: Double = TREND_HALF_LIFE_DAYS): List<Point> {
    val sorted = points.sortedBy { it.day }
    val out = ArrayList<Point>(sorted.size)
    var value = 0.0
    var last = 0
    for ((i, p) in sorted.withIndex()) {
        if (i == 0) {
            value = p.kg
            last = p.day
        } else {
            val dt = max(p.day - last, 0).toDouble()
            val alpha = 1 - exp((-LN2 * dt) / halfLifeDays)
            value += alpha * (p.kg - value)
            last = p.day
        }
        out.add(Point(p.day, value))
    }
    return out
}

data class Fit(
    /** Change per day. Negative is loss. */
    val slope: Double,
    val intercept: Double,
    /**
     * The standard error of the slope, which is what every band
     * drawn downstream of this is made of.
     */
    val se: Double,
    val n: Int,
)

/**
 * Ordinary least squares of kg against day. Needs three points to
 * have a residual to measure, so two returns null rather than a
 * slope with no error bar, which is the shape this file refuses
 * everywhere else.
 *
 * NOT sorted, deliberately: the site does not sort here either,
 * and a least-squares fit does not depend on the order except in
 * the last bit of the sums, which is exactly the difference the
 * fixtures are checked to.
 */
fun fit(points: List<Point>): Fit? {
    val n = points.size
    if (n < 3) return null
    var sumX = 0.0
    var sumY = 0.0
    for (p in points) { sumX += p.day }
    for (p in points) { sumY += p.kg }
    val mx = sumX / n
    val my = sumY / n

    var sxx = 0.0
    for (p in points) { val d = p.day - mx; sxx += d * d }
    if (sxx == 0.0) return null

    var sxy = 0.0
    for (p in points) { sxy += (p.day - mx) * (p.kg - my) }
    val slope = sxy / sxx
    val intercept = my - slope * mx

    var rss = 0.0
    for (p in points) { val r = p.kg - (intercept + slope * p.day); rss += r * r }
    val se = sqrt(rss / (n - 2) / sxx)
    return Fit(slope, intercept, se, n)
}

/**
 * Kilograms per week, with its own error.
 *
 * FITTED TO THE READINGS, NOT TO THE TREND, and that is not the
 * obvious choice so it is written down here as well as on the
 * site.
 *
 * An exponentially weighted average is the right estimator of a
 * LEVEL and the wrong one for a RATE. Seeded from the first
 * reading it lags the true line by about 1.44 half-lives while the
 * transient settles, so on a fortnight's data its endpoints
 * understate a real loss by roughly a third, and its own fitted
 * slope understates it too. Nothing about that is visible: the
 * line looks right, the number is wrong, and it is wrong in the
 * flattering direction, which would make the tool report a smaller
 * deficit than the reader is running.
 *
 * `trend()` is still what a screen draws and still what "your
 * trend weight today" means.
 */
fun slopePerWeek(points: List<Point>): Range? {
    val f = fit(points) ?: return null
    return rangeOf(f.slope * 7, 1.96 * f.se * 7)
}

/**
 * The standard approximation for a kilogram of body tissue. Right
 * for fat and wrong for water, which is precisely why everything
 * here reads the trend.
 */
const val KCAL_PER_KG = 7700.0

/**
 * Fourteen days before this is shown at all. A shorter window is
 * mostly the first week's water, and the whole point of this
 * figure is that it is not that.
 */
const val LEARN_AFTER_DAYS = 14

/**
 * What a day nobody wrote down is worth in uncertainty, as a share
 * of the mean intake. `DIET.md` section 3 is where the number
 * comes from: self-reported intake is under-recorded "commonly by
 * 20 to 30 percent", so a day with no entry at all is unknown to
 * about that much.
 */
const val UNLOGGED_SE_SHARE = 0.25

/** One day's intake, which is the other series `learnedBurn` reads. */
data class Intake(val day: Int, val kcal: Double)

data class Learned(
    val kcal: Range,
    /**
     * Days spanned, and days with an intake logged. The second
     * over the first is what widens the band and what the screen
     * prints beside the number.
     */
    val days: Int,
    val logged: Int,
    val meanIntake: Double,
    /** Signed, from the fit. Negative is loss. */
    val trendKgPerWeek: Double,
)

/**
 * What this reader appears to burn, given what they appear to eat.
 *
 *     burn = mean intake − (trend change in kg × 7700) / days
 *
 * MINUS a signed change, because a loss is a negative delta and a
 * deficit is a positive addition to intake. Writing it as a plus
 * and meaning the magnitude is how a formula that reads correctly
 * in prose comes out inverted in code.
 *
 * It absorbs metabolic adaptation, a wrong activity guess and
 * consistent under-logging into one honest number, and the gap
 * between it and `estimatedBurn()` IS the under-logging estimate.
 * Returns null before `LEARN_AFTER_DAYS`.
 */
fun learnedBurn(weights: List<Point>, intakes: List<Intake>): Learned? {
    val sorted = weights.sortedBy { it.day }
    if (sorted.size < 3) return null
    val from = sorted.first().day
    val to = sorted.last().day
    val days = to - from
    if (days < LEARN_AFTER_DAYS) return null

    val kept = intakes.filter { it.day >= from && it.day <= to && it.kcal > 0 }
    if (kept.size < 2) return null

    val f = fit(sorted) ?: return null

    var sum = 0.0
    for (i in kept) { sum += i.kcal }
    val meanIntake = sum / kept.size

    /* The change the regression implies over the window, not the
       difference between two trend points: `slopePerWeek()` says
       why. */
    val deltaKg = f.slope * days
    val kcal = meanIntake - (deltaKg * KCAL_PER_KG) / days

    /* Three independent errors, added in quadrature: how well the
       mean intake is known, how well the slope is, and how much of
       the window was written down at all.

       THE THIRD IS THE DAYS THAT ARE NOT THERE, and without it this
       number was at its most confident exactly where it deserved
       least confidence. The first two are both computed from the
       rows that exist, so a reader who logs food three days in
       twenty, identically, got a narrow band on a figure worked out
       as though they had eaten that on all twenty. The missing days
       are not noise around a mean, they are a gap where the mean
       might not be, and no amount of variance inside the logged
       days can measure it. */
    var ss = 0.0
    for (i in kept) { val d = i.kcal - meanIntake; ss += d * d }
    val varIntake = ss / (kept.size - 1)
    val seIntake = sqrt(varIntake / kept.size)
    val seSlope = f.se * KCAL_PER_KG
    val covered = min(kept.size.toDouble() / (days + 1), 1.0)
    val seUnlogged = UNLOGGED_SE_SHARE * meanIntake * (1 - covered)
    val se = sqrt(seIntake * seIntake + seSlope * seSlope + seUnlogged * seUnlogged)

    return Learned(
        kcal = rangeOf(kcal, 1.96 * se),
        days = days,
        logged = kept.size,
        meanIntake = meanIntake,
        trendKgPerWeek = f.slope * 7,
    )
}
