package uk.co.reiad.library.core.stock

/* ============================================================
   The whole analysis as a spreadsheet.

   `toCsv()` in `aab/tools/stock.js`, ported section for section
   and in the same order, so a file exported on a phone and a file
   exported on the site open the same way in the same columns.

   ---- why it is in English and always ----

   The site's own does the same, and it is deliberate rather than
   an oversight: this file is going into somebody's spreadsheet
   beside other spreadsheets, and a column heading that changes
   language depending on which device made it cannot be sorted,
   filtered or compared with last quarter's.

   ---- and why the disclaimer is the first two rows ----

   Because a spreadsheet outlives the page it came from. What this
   model cannot see travels with the numbers or it is lost, and a
   row of ratios with no caveat attached is exactly the artefact
   this tool was written not to produce.
   ============================================================ */

private fun q(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

/** A number with a fixed number of decimals, or an empty cell.

    Empty rather than a dash or a nought: a spreadsheet reads an
    empty cell as missing and averages around it, and reads `0` as
    a measurement. Half this model's correctness is in that
    distinction, and it has to survive the export. */
private fun cell(v: Double?, digits: Int): String =
    if (v == null || !v.isFinite()) "" else {
        java.math.BigDecimal.valueOf(v)
            .setScale(digits, java.math.RoundingMode.HALF_UP)
            .toPlainString()
    }

fun toCsv(a: Analysis, words: ToolWords, weights: Weights, style: String): String {
    val lines = mutableListOf<String>()
    fun t(key: String) = words.t(key, "en")

    lines += "STOCK CHECK: reiad.co.uk/tools/stock"
    lines += q(t(Keys.DISC_BODY))
    lines += q(t(Keys.DISC_UNITS))
    lines += ""

    lines += "VERDICT"
    lines += "Score,${cell(a.score, 1)}"
    lines += "Band,${q(t(if (a.vetoed) Keys.VETOED else "verdict.${a.verdict.id}"))}"
    lines += "Investor style,$style"
    lines += "Weights,${PILLARS.joinToString(" ") { "$it=${cell(weights[it], 0)}" }}"
    lines += ""

    /* Every input, so the file can be typed back in. Through the
       serialiser rather than a list, for the reason `fieldsOf`
       gives: a field somebody forgets is a column the reader
       cannot reconstruct their own analysis from. */
    lines += "INPUTS"
    for ((key, value) in fieldsOf(a.d)) lines += "$key,${jsNumber(value)}"
    lines += ""

    lines += "PILLARS"
    for (p in PILLARS) {
        lines += listOf(
            q(t("pillar.$p")),
            cell(a.pillars[p]?.score, 1),
            cell(weights[p], 0),
        ).joinToString(",")
    }
    lines += ""

    lines += "SCORECARD"
    lines += "Ratio,Pillar,Value,Score,Applies"
    for (s in a.scored) {
        lines += listOf(
            q(t("m.${s.id}")),
            s.pillar,
            if (s.na) "" else cell(s.raw, 4),
            if (s.na) "" else cell(s.score, 1),
            if (s.na) "no" else "yes",
        ).joinToString(",")
    }
    lines += ""

    lines += "FAIR VALUE ANCHORS"
    for (anchor in a.fair.anchors) lines += "${q(t("fv.${anchor.id}"))},${cell(anchor.value, 2)}"
    lines += "Median,${cell(a.fair.mid, 2)}"
    lines += "Margin of safety %,${cell(a.fair.marginOfSafety, 1)}"
    lines += ""

    lines += "FLAGS"
    for (f in a.flags) lines += "${f.level},${q(t("f.${f.id}"))}"
    lines += ""

    lines += "PATTERNS"
    for (s in a.signals) lines += "${s.tone},${q(t("s.${s.id}"))}"

    return lines.joinToString("\n")
}
