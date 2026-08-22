package uk.co.reiad.library.core.stock

/* ============================================================
   What a reader fills in, in the order the site asks for it.

   `FIELDS` in `aab/tools/stock.js`, ported. It is presentation
   rather than model: which group a number belongs to, how far a
   slider runs, whether a field is a choice rather than a number.

   ---- why porting this is safe, when porting the words was not ----

   Because it CANNOT go stale invisibly. A field added to the
   model on the site is a field `Inputs` does not have, and
   `ShareTest` fails on the day it lands: the site's own
   `DEFAULTS` is in the fixture and the two key sets are compared.
   So a missing field here is a red test rather than a silent gap,
   which is the whole difference. The words had no such guard,
   which is why they are fetched.

   `FieldsTest` closes the other half: every field of `Inputs` is
   in exactly one group here. A field the model reads and no group
   shows is a number a reader cannot change and cannot see.
   ============================================================ */

/** A number, or a choice between named things. */
data class Field(
    val id: String,
    val group: String,
    /** How much one press of a stepper moves it. */
    val step: Double,
    /** Low, high and increment where this is worth feeling your
        way around rather than copying off a report. Null for the
        statement figures, which are typed once. */
    val slider: Slider? = null,
    /** The choices, for a field that is one. */
    val choices: List<String>? = null,
    /** The phrase key prefix a choice's label is under, or null
        where the choice is its own label. A sector has a name in
        both languages; a market category is the letter A. */
    val choicePrefix: String? = null,
)

data class Slider(val low: Double, val high: Double, val by: Double)

/** The eight groups, in the order the page asks for them.

    The first two are open when the page opens: a reader who wants
    a quick answer types a price, a share count and last year's
    profit, and everything else has a working default. That is not
    a shortcut, it is the difference between a tool somebody tries
    and a form of fifty-six boxes nobody finishes. */
val GROUPS: List<String> =
    listOf("company", "income", "balance", "cash", "dividend", "prior", "bank", "benchmarks")

val OPEN_BY_DEFAULT: Set<String> = setOf("company", "income")

/** The bank group is shown only where it means anything, which is
    the same decision the metrics make one layer down: a capital
    adequacy ratio on a textile mill is a box that can only be
    filled in wrongly. */
fun groupsFor(sector: String): List<String> =
    if (isFinancialSector(sector)) GROUPS else GROUPS - "bank"

private fun n(
    id: String,
    group: String,
    step: Double,
    slider: Slider? = null,
) = Field(id, group, step, slider)

val FIELDS: List<Field> = listOf(
    /* ---------------- the company and its price ---------------- */
    n("price", "company", 0.1, Slider(1.0, 500.0, 0.5)),
    n("shares", "company", 1.0),
    Field("sector", "company", 0.0, choices = SECTORS.keys.toList(), choicePrefix = "sector."),
    Field("category", "company", 0.0, choices = listOf("A", "B", "N", "Z")),
    Field("benchmark", "company", 0.0, choices = INDICES.keys.toList()),
    n("high52", "company", 0.1),
    n("low52", "company", 0.1),
    n("ma50", "company", 0.1),
    n("ma200", "company", 0.1),
    n("turnover", "company", 1.0, Slider(0.0, 500.0, 1.0)),
    n("freeFloat", "company", 1.0, Slider(0.0, 100.0, 1.0)),
    n("stockReturn12m", "company", 1.0, Slider(-80.0, 150.0, 1.0)),
    n("indexReturn12m", "company", 1.0, Slider(-50.0, 80.0, 1.0)),

    /* ---------------- the income statement ---------------- */
    n("revenue", "income", 100.0),
    n("grossProfit", "income", 100.0),
    n("ebit", "income", 100.0),
    n("depreciation", "income", 50.0),
    n("interestExpense", "income", 50.0),
    n("netIncome", "income", 100.0),

    /* ---------------- the balance sheet ---------------- */
    n("totalAssets", "balance", 100.0),
    n("currentAssets", "balance", 100.0),
    n("inventory", "balance", 100.0),
    n("cash", "balance", 100.0),
    n("currentLiabilities", "balance", 100.0),
    n("totalDebt", "balance", 100.0),
    n("equity", "balance", 100.0),
    n("reserves", "balance", 100.0),

    /* ---------------- cash flow ---------------- */
    n("cfo", "cash", 100.0),
    n("capex", "cash", 100.0),

    /* ---------------- the dividend ---------------- */
    n("dps", "dividend", 0.1, Slider(0.0, 30.0, 0.1)),
    n("divTax", "dividend", 1.0, Slider(0.0, 30.0, 1.0)),
    n("yearsPaid", "dividend", 1.0, Slider(0.0, 25.0, 1.0)),

    /* ---------------- last year ----------------
       Every one of these is optional, and leaving one out is not
       the same as putting a nought in it: a trend metric with no
       prior year reports "not testable" and is dropped, and a
       Piotroski test that cannot be asked is skipped rather than
       failed. */
    n("revenuePrev", "prior", 100.0),
    n("grossProfitPrev", "prior", 100.0),
    n("netIncomePrev", "prior", 100.0),
    n("totalAssetsPrev", "prior", 100.0),
    n("currentAssetsPrev", "prior", 100.0),
    n("currentLiabilitiesPrev", "prior", 100.0),
    n("totalDebtPrev", "prior", 100.0),
    n("cfoPrev", "prior", 100.0),
    n("sharesPrev", "prior", 1.0),
    n("netIncome3y", "prior", 100.0),

    /* ---------------- a bank's own five ---------------- */
    n("car", "bank", 0.1, Slider(0.0, 25.0, 0.1)),
    n("npl", "bank", 0.1, Slider(0.0, 30.0, 0.1)),
    n("provisionCover", "bank", 1.0, Slider(0.0, 200.0, 1.0)),
    n("costIncome", "bank", 1.0, Slider(0.0, 100.0, 1.0)),
    n("adr", "bank", 0.1, Slider(0.0, 110.0, 0.5)),

    /* ---------------- what it is measured against ---------------- */
    n("sectorPE", "benchmarks", 0.1, Slider(3.0, 40.0, 0.1)),
    n("sectorPB", "benchmarks", 0.1, Slider(0.2, 10.0, 0.1)),
    n("sectorROE", "benchmarks", 0.5, Slider(0.0, 50.0, 0.5)),
    n("sectorMargin", "benchmarks", 0.5, Slider(0.0, 40.0, 0.5)),
    n("marketPE", "benchmarks", 0.1, Slider(4.0, 30.0, 0.1)),
    n("riskFree", "benchmarks", 0.01, Slider(0.0, 20.0, 0.05)),
    n("fdr", "benchmarks", 0.1, Slider(0.0, 15.0, 0.1)),
    n("inflation", "benchmarks", 0.1, Slider(0.0, 25.0, 0.1)),
    n("nonCompliantIncome", "benchmarks", 0.5, Slider(0.0, 40.0, 0.5)),
)

/** Read a field off an `Inputs`, by name.

    Through the serialiser rather than a fifty-six-arm `when`,
    for the reason `fieldsOf` gives: the arm somebody forgets is
    a box that shows the wrong number. Writing one back is
    `applyField`'s job in `Share.kt`, and it has to be a `when`
    because there is no serialiser going the other way for one
    key at a time. */
fun valueOf(d: Inputs, id: String): String = fieldsOf(d)[id]?.let(::jsNumber) ?: ""

/** Set one field. The same decoder a shared link goes through,
    so a number typed into a box and a number arriving in a link
    are read by one piece of code. */
fun withField(d: Inputs, id: String, text: String): Inputs =
    readShare("${encodeQuery(id)}=${encodeQuery(text)}", base = d).inputs
