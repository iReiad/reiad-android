package uk.co.reiad.library.core.tools

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/* ============================================================
   The five calculators, ported.

   Compounding, sanchayapatra against FDR, inflation, loan EMI and
   position sizing. `shared/calculators.ts` on the website is the
   original and stays the original; this is a second
   implementation, and `content/calculators.fixtures.json` is what
   keeps the two in step.

   ---- why these need a fixture more than the stock check did ----

   Because every number in them is PLAUSIBLE. A stock model that
   drops a metric produces a verdict a reader might question; a
   compounding calculator whose loop runs one month short still
   returns a sensible-looking balance, over a sensible-looking
   curve, and nothing on the screen could tell anybody. The only
   way to know is to compare it with what the other one said.

   ---- the split this file honours ----

   **Nothing here is prose and nothing here is a format.** A
   calculator hands back numbers by name and the key of a
   sentence. The screen looks the sentence up in the table
   `/api/tools` serves and fills its `{placeholders}` from those
   numbers, printing each the way `FORMATS` says.

   That is what stops sixty-eight bilingual sentences existing
   twice. They are edited on the site and reach a phone on the
   next fetch, like every other phrase.
   ============================================================ */

/** How a named number is printed, wherever it appears. */
enum class Kind { MONEY, PERCENT, COUNT, YEARS, PRICE }

/** The site's own table, by name. Asserted against the fixture,
    because a value that is money on one side and a bare decimal
    on the other is a figure a reader cannot read. */
val FORMATS: Map<String, Kind> = mapOf(
    /* compounding */
    "final" to Kind.MONEY, "paid" to Kind.MONEY, "growth" to Kind.MONEY,
    "growthPct" to Kind.PERCENT, "doubles" to Kind.COUNT,

    /* sanchayapatra vs FDR */
    "sGross" to Kind.MONEY, "sPaidTax" to Kind.MONEY, "sNet" to Kind.MONEY,
    "sTotal" to Kind.MONEY, "fGross" to Kind.MONEY, "fPaidTax" to Kind.MONEY,
    "fNet" to Kind.MONEY, "fTotal" to Kind.MONEY,
    "gap" to Kind.MONEY, "gapPct" to Kind.PERCENT,

    /* inflation */
    "worth" to Kind.MONEY, "lost" to Kind.MONEY, "lostPct" to Kind.PERCENT,
    "real" to Kind.PERCENT, "grown" to Kind.MONEY, "grownReal" to Kind.MONEY,

    /* EMI */
    "emi" to Kind.MONEY, "interest" to Kind.MONEY, "total" to Kind.MONEY,
    "interestPct" to Kind.PERCENT, "shorterEmi" to Kind.MONEY,
    "saved" to Kind.MONEY, "shorter" to Kind.YEARS,

    /* position sizing */
    "shares" to Kind.COUNT, "cost" to Kind.MONEY, "riskTaka" to Kind.MONEY,
    "exposure" to Kind.PERCENT, "after20" to Kind.MONEY,

    /* inputs, which sentences quote back */
    "amount" to Kind.MONEY, "years" to Kind.YEARS, "rate" to Kind.PERCENT,
    "nominal" to Kind.PERCENT, "inflation" to Kind.PERCENT,
    "principal" to Kind.MONEY, "capital" to Kind.MONEY, "risk" to Kind.PERCENT,
    "entry" to Kind.PRICE, "stop" to Kind.PRICE,
    "srate" to Kind.PERCENT, "frate" to Kind.PERCENT,
)

/** One thing a reader can change. */
data class Field(
    val name: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val value: Double,
    /** A number box rather than a slider. Two of the twenty-one
        are: an entry price and a stop are typed off a broker's
        screen, not felt for. */
    val typed: Boolean = false,
)

data class Outcome(
    /** Every number, by name. The figures and every placeholder
        are looked up here. */
    val values: Map<String, Double>,
    val series: Map<String, List<Double>>,
    /** The phrase key for the line under each figure, chosen by
        the calculator: three of them turn on the answer. */
    val notes: Map<String, String>,
    /** Which sentence to print, under `calc.<id>.<verdict>`. */
    val verdict: String,
)

class Calculator(
    val id: String,
    val fields: List<Field>,
    /** The three headline figures, in the order shown. */
    val figures: List<String>,
    /** The two chart series, in drawing order. Empty where a
        calculator has no chart, which two of them do not. */
    val lines: List<String>,
    private val compute: (Map<String, Double>) -> Outcome,
) {
    fun run(v: Map<String, Double>): Outcome = compute(v)

    /** What this calculator opens with. */
    val defaults: Map<String, Double> get() = fields.associate { it.name to it.value }
}

/* ---------- the browser's own coercions ----------

   `Number(v.start) || 0` turns an absent field, an empty box and
   a nonsense string all into nought, and does the same to a real
   nought. The `|| 1` form is the one that matters: a term of
   nought divides by zero three lines later.

   Written out rather than left to a Kotlin default, because these
   two lines ARE the site's behaviour and the fixture compares
   against it. */
private fun n(v: Map<String, Double>, key: String, fallback: Double = 0.0): Double {
    val x = v[key]
    return if (x != null && x.isFinite() && x != 0.0) x else fallback
}

/** A percentage as a fraction. Never falls back: nought per cent
    is a real answer and every calculator has a branch for it. */
private fun asRate(v: Map<String, Double>, key: String): Double {
    val x = v[key]
    return (if (x != null && x.isFinite()) x else 0.0) / 100
}

/* ============================================================
   1. COMPOUNDING
   ============================================================ */

val compounding = Calculator(
    id = "compounding",
    fields = listOf(
        Field("start", 0.0, 1_000_000.0, 5000.0, 50_000.0),
        Field("monthly", 0.0, 100_000.0, 500.0, 5000.0),
        Field("rate", 0.0, 25.0, 0.5, 10.0),
        Field("years", 1.0, 40.0, 1.0, 20.0),
    ),
    figures = listOf("final", "paid", "growth"),
    lines = listOf("totals", "contributed"),
) { v ->
    val start = n(v, "start")
    val monthly = n(v, "monthly")
    val rate = asRate(v, "rate")
    val years = n(v, "years", 1.0)
    val r = rate / 12

    val totals = mutableListOf<Double>()
    val contributed = mutableListOf<Double>()
    var balance = start
    var paidIn = start
    /* Month by month rather than by formula, and the loop bound
       is `years * 12` as a DOUBLE compared with an Int counter,
       exactly as the site's is: a term of 20.5 years runs 246
       months on both sides rather than 246 on one and 240 on the
       other. */
    var m = 0
    while (m <= years * 12) {
        if (m > 0) {
            balance = balance * (1 + r) + monthly
            paidIn += monthly
        }
        if (m % 12 == 0) {
            totals += balance
            contributed += paidIn
        }
        m += 1
    }

    val growth = balance - paidIn
    Outcome(
        values = mapOf(
            "final" to balance,
            "paid" to paidIn,
            "growth" to growth,
            "growthPct" to if (paidIn > 0) (growth / paidIn) * 100 else 0.0,
            /* The rule of 72, and the sentence that prints it
               says it is an approximation. */
            "doubles" to if (rate > 0) 72 / (rate * 100) else Double.POSITIVE_INFINITY,
            "rate" to rate * 100,
            "years" to years,
        ),
        series = mapOf("totals" to totals, "contributed" to contributed),
        notes = mapOf(
            "final" to "calc.compounding.final.note",
            "paid" to "calc.compounding.paid.note",
            /* Nothing was put in, so "0% on top" would be a
               division dressed up as a finding. */
            "growth" to if (paidIn > 0) "calc.compounding.growth.note" else "",
        ),
        verdict = if (rate > 0) "grows" else "flat",
    )
}

/* ============================================================
   2. SANCHAYAPATRA vs FDR

   The difference is not the headline rate: sanchayapatra pays its
   profit OUT so nothing compounds, while an FDR's interest rolls
   up. Over five years that turns a rate gap into a smaller one,
   and sometimes into none at all.
   ============================================================ */

val sanchayapatra = Calculator(
    id = "sanchayapatra",
    fields = listOf(
        Field("amount", 50_000.0, 5_000_000.0, 50_000.0, 1_000_000.0),
        Field("years", 1.0, 10.0, 1.0, 5.0),
        Field("srate", 5.0, 15.0, 0.01, 11.04),
        Field("stax", 0.0, 20.0, 1.0, 10.0),
        Field("frate", 3.0, 15.0, 0.01, 9.0),
        Field("ftax", 0.0, 20.0, 1.0, 10.0),
    ),
    figures = listOf("sTotal", "fTotal", "gap"),
    lines = emptyList(),
) { v ->
    val amount = n(v, "amount")
    val years = n(v, "years", 1.0)
    val sRate = asRate(v, "srate")
    val fRate = asRate(v, "frate")
    val sTax = asRate(v, "stax")
    val fTax = asRate(v, "ftax")

    val sGross = amount * sRate * years
    val sNet = sGross * (1 - sTax)
    val fGross = amount * ((1 + fRate).pow(years) - 1)
    val fNet = fGross * (1 - fTax)

    val sTotal = amount + sNet
    val fTotal = amount + fNet
    val gap = abs(sTotal - fTotal)

    Outcome(
        values = mapOf(
            "sGross" to sGross, "sPaidTax" to sGross - sNet, "sNet" to sNet, "sTotal" to sTotal,
            "fGross" to fGross, "fPaidTax" to fGross - fNet, "fNet" to fNet, "fTotal" to fTotal,
            "gap" to gap,
            "gapPct" to if (amount > 0) (gap / amount) * 100 else 0.0,
            "amount" to amount, "years" to years,
        ),
        series = emptyMap(),
        notes = mapOf(
            "sTotal" to "calc.sanchayapatra.sTotal.note",
            "fTotal" to "calc.sanchayapatra.fTotal.note",
            "gap" to if (sTotal >= fTotal) "calc.sanchayapatra.gap.note.s"
            else "calc.sanchayapatra.gap.note.f",
        ),
        /* Half a per cent of the principal apart over five years
           is not a difference anybody should choose on. The rules
           are: the purchase ceiling, and how fast the money comes
           back out. */
        verdict = when {
            gap < amount * 0.005 -> "close"
            sTotal >= fTotal -> "sanchayapatra"
            else -> "fdr"
        },
    )
}

/* ============================================================
   3. INFLATION
   ============================================================ */

val inflation = Calculator(
    id = "inflation",
    fields = listOf(
        Field("amount", 10_000.0, 10_000_000.0, 10_000.0, 500_000.0),
        Field("inflation", 0.0, 20.0, 0.25, 9.0),
        Field("nominal", 0.0, 25.0, 0.25, 9.0),
        Field("years", 1.0, 30.0, 1.0, 10.0),
    ),
    figures = listOf("worth", "lost", "real"),
    lines = listOf("nominalSeries", "realSeries"),
) { v ->
    val amount = n(v, "amount")
    val inf = asRate(v, "inflation")
    val years = n(v, "years", 1.0)
    val nominal = asRate(v, "nominal")

    val worth = amount / (1 + inf).pow(years)
    val lost = amount - worth
    /* Fisher, done properly rather than by subtraction. At 15
       against 10 the two methods differ by half a point, which is
       most of a fixed deposit's real return. */
    val real = (1 + nominal) / (1 + inf) - 1
    val grown = amount * (1 + nominal).pow(years)
    val grownReal = grown / (1 + inf).pow(years)

    val nominalSeries = mutableListOf<Double>()
    val realSeries = mutableListOf<Double>()
    var y = 0
    while (y <= years) {
        nominalSeries += amount * (1 + nominal).pow(y)
        realSeries += (amount * (1 + nominal).pow(y)) / (1 + inf).pow(y)
        y += 1
    }

    Outcome(
        values = mapOf(
            "worth" to worth, "lost" to lost,
            "lostPct" to if (amount > 0) (lost / amount) * 100 else 0.0,
            "real" to real * 100,
            "grown" to grown, "grownReal" to grownReal,
            "amount" to amount, "years" to years,
            "nominal" to nominal * 100, "inflation" to inf * 100,
        ),
        series = mapOf("nominalSeries" to nominalSeries, "realSeries" to realSeries),
        notes = mapOf(
            "worth" to "calc.inflation.worth.note",
            "lost" to "calc.inflation.lost.note",
            "real" to if (real >= 0) "calc.inflation.real.note"
            else "calc.inflation.real.note.losing",
        ),
        verdict = if (real >= 0) "beats" else "loses",
    )
}

/* ============================================================
   4. LOAN EMI
   ============================================================ */

private fun instalment(p: Double, r: Double, m: Double): Double =
    if (r > 0) (p * r * (1 + r).pow(m)) / ((1 + r).pow(m) - 1) else p / m

val emi = Calculator(
    id = "emi",
    fields = listOf(
        Field("principal", 50_000.0, 10_000_000.0, 50_000.0, 1_500_000.0),
        Field("rate", 4.0, 20.0, 0.25, 12.0),
        Field("years", 1.0, 25.0, 1.0, 10.0),
    ),
    figures = listOf("emi", "interest", "total"),
    lines = listOf("balances", "paidInterest"),
) { v ->
    val principal = n(v, "principal")
    val rate = asRate(v, "rate") / 12
    val years = n(v, "years", 1.0)
    val months = years * 12

    val monthly = instalment(principal, rate, months)
    val total = monthly * months
    val interest = total - principal

    val balances = mutableListOf<Double>()
    val paidInterest = mutableListOf<Double>()
    var bal = principal
    var cumInterest = 0.0
    var m = 0
    while (m <= months) {
        if (m > 0) {
            val i = bal * rate
            cumInterest += i
            bal = max(0.0, bal - (monthly - i))
        }
        if (m % 12 == 0 || m.toDouble() == months) {
            balances += bal
            paidInterest += cumInterest
        }
        m += 1
    }

    val shorter = max(1.0, years - 2)
    val shorterMonths = shorter * 12
    val shorterEmi = instalment(principal, rate, shorterMonths)
    /* `shorterEmi * shorterMonths`, and the brackets are
       load-bearing on both sides: `emi * shorter * 12` associates
       left and lands one bit away on a ten-million-taka loan. */
    val saved = total - shorterEmi * shorterMonths

    Outcome(
        values = mapOf(
            "emi" to monthly, "interest" to interest, "total" to total,
            "interestPct" to if (principal > 0) (interest / principal) * 100 else 0.0,
            "shorter" to shorter, "shorterEmi" to shorterEmi, "saved" to saved,
            "principal" to principal, "years" to years,
            "rate" to asRate(v, "rate") * 100,
        ),
        series = mapOf("balances" to balances, "paidInterest" to paidInterest),
        notes = mapOf(
            "emi" to "calc.emi.emi.note",
            "interest" to if (principal > 0) "calc.emi.interest.note" else "",
            "total" to "calc.emi.total.note",
        ),
        verdict = if (saved > 0) "shorter" else "plain",
    )
}

/* ============================================================
   5. POSITION SIZING

   The only calculator here that can tell somebody not to take a
   trade, and the one whose answer is most often unwelcome.
   ============================================================ */

val position = Calculator(
    id = "position",
    fields = listOf(
        Field("capital", 10_000.0, 5_000_000.0, 10_000.0, 200_000.0),
        Field("risk", 0.25, 5.0, 0.25, 1.0),
        Field("entry", 1.0, 5000.0, 0.1, 45.0, typed = true),
        Field("stop", 0.0, 5000.0, 0.1, 40.0, typed = true),
    ),
    figures = listOf("shares", "cost", "riskTaka"),
    lines = emptyList(),
) { v ->
    val capital = n(v, "capital")
    val riskPct = asRate(v, "risk")
    val entry = n(v, "entry")
    val stop = n(v, "stop")

    val riskTaka = capital * riskPct
    val perShare = max(0.0, entry - stop)
    /* Floor, never round: one share more than the rule allows is
       a rule that has stopped being one. */
    val shares = if (perShare > 0) floor(riskTaka / perShare) else 0.0
    val cost = shares * entry

    Outcome(
        values = mapOf(
            "shares" to shares, "cost" to cost, "riskTaka" to riskTaka,
            "exposure" to if (capital > 0) (cost / capital) * 100 else 0.0,
            /* Twenty losses in a row at this size, which is the
               number that makes a risk rule feel like one. */
            "after20" to capital * (1 - riskPct).pow(20),
            "capital" to capital, "entry" to entry, "stop" to stop,
            "risk" to riskPct * 100,
        ),
        series = emptyMap(),
        notes = mapOf(
            "shares" to "calc.position.shares.note",
            "cost" to if (cost > capital) "calc.position.cost.note.over"
            else "calc.position.cost.note",
            "riskTaka" to "calc.position.riskTaka.note",
        ),
        verdict = when {
            perShare <= 0 -> "noStop"
            cost > capital -> "tooBig"
            else -> "fits"
        },
    )
}

/** The five, in the order the site shows them. */
val CALCULATORS: List<Calculator> =
    listOf(compounding, sanchayapatra, inflation, emi, position)

fun calculatorFor(id: String): Calculator? = CALCULATORS.firstOrNull { it.id == id }
