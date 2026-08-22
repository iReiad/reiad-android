package uk.co.reiad.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.stock.NOTHING
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.core.stock.fmtNum
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.tools.CALCULATORS
import uk.co.reiad.library.core.tools.Calculator
import uk.co.reiad.library.core.tools.FORMATS
import uk.co.reiad.library.core.tools.Field
import uk.co.reiad.library.core.tools.Kind
import uk.co.reiad.library.core.tools.Outcome
import kotlin.math.abs
import kotlin.math.roundToInt

/* ============================================================
   The five calculators.

   Compounding, sanchayapatra against FDR, inflation, loan EMI and
   position sizing, over `core/tools/Calculators.kt`, which is
   asserted number for number against the site's own model.

   ---- what this file may and may not hold ----

   Drawing, and nothing else. Not a formula, not a threshold, and
   above all not a sentence: a calculator hands back numbers by
   name and the key of a sentence, and this fills that sentence
   from the table `/api/tools` serves.

   A phrase typed here would be the sixty-ninth copy of something
   that already exists in two languages on the site, and it would
   be the one nobody edits.
   ============================================================ */

/** Which calculator is open and what has been typed into each.

    All five are kept, not just the open one: a reader who sets up
    a loan, looks at inflation and comes back should find their
    loan still there. It is this session's only, deliberately: a
    half-filled calculator is not a thing to restore three days
    later. */
data class CalcState(
    val open: String = CALCULATORS.first().id,
    val values: Map<String, Map<String, Double>> =
        CALCULATORS.associate { it.id to it.defaults },
)

@Composable
fun CalculatorsScreen(
    words: ToolWords,
    state: CalcState,
    onState: (CalcState) -> Unit,
    lang: String,
    onLang: (String) -> Unit,
    /** The full names, out of `/api/site`. NOT a second copy in
        the phrase table: `TOOLS` in `content.ts` already holds
        both languages of every calculator's name, and the app
        reads that table like every other. */
    titles: Map<String, Pair<String, String>> = emptyMap(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = LocalReiad.current
    val calc = CALCULATORS.first { it.id == state.open }
    val inputs = state.values.getValue(calc.id)
    val out = remember(calc.id, inputs) { calc.run(inputs) }

    fun show(name: String, v: Double) = printed(name, v, words, lang)

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
    ) {
        item {
            Column(Modifier.padding(top = Gap.s6)) {
                Text(
                    words.t("calc.eyebrow", lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.accent,
                )
                val name = titles[calc.id]
                    ?.let { (en, bn) -> if (lang == "bn") bn else en }
                    ?: words.t("calc.${calc.id}.short", lang)
                Text(
                    name,
                    style = headingStyle(name),
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Gap.s5))
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s3)) {
                    for ((id, label) in listOf("en" to "English", "bn" to "বাংলা")) {
                        Box(Modifier.clickable { onLang(id) }) {
                            Chip(label, tone = if (lang == id) c.accent else c.inkSoft)
                        }
                    }
                }
            }
        }

        /* Which of the five. A row of chips rather than a tab bar,
           because on a handset five tabs are five words nobody can
           read; the site's own picker is a tab set for the same
           reason in reverse. */
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Gap.s4),
            ) {
                for (other in CALCULATORS) {
                    Box(Modifier.clickable { onState(state.copy(open = other.id)) }) {
                        Chip(
                            words.t("calc.${other.id}.short", lang),
                            tone = if (other.id == calc.id) c.accent else c.inkSoft,
                        )
                    }
                }
            }
        }

        /* ---------- the answer, first ---------- */
        item {
            Pane {
                for ((i, key) in calc.figures.withIndex()) {
                    if (i > 0) Spacer(Modifier.height(Gap.s5))
                    Text(
                        words.t("calc.${calc.id}.$key", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                    Text(
                        show(key, out.values.getValue(key)),
                        style = if (i == 0) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleMedium,
                        color = if (i == 0) c.accent else c.ink,
                    )
                    val note = out.notes[key]
                    if (!note.isNullOrEmpty()) {
                        Text(
                            fill(words, note, lang, out),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.inkSoft,
                        )
                    }
                }
            }
        }

        /* ---------- and why ---------- */
        item {
            Pane {
                val sentence = fill(words, "calc.${calc.id}.${out.verdict}", lang, out)
                Text(sentence, style = bodyStyle(sentence), color = c.ink)
            }
        }

        /* ---------- the chart, where there is one ---------- */
        if (calc.lines.size == 2) {
            item {
                Pane {
                    TwoLines(
                        out.series.getValue(calc.lines[0]),
                        out.series.getValue(calc.lines[1]),
                    )
                    Spacer(Modifier.height(Gap.s4))
                    Row {
                        for ((i, line) in calc.lines.withIndex()) {
                            if (i > 0) Spacer(Modifier.width(Gap.s6))
                            Text(
                                words.t("calc.${calc.id}.line.$line", lang),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (i == 0) c.accent else c.inkSoft,
                            )
                        }
                    }
                }
            }
        }

        /* ---------- the comparison's working ---------- */
        if (calc.id == "sanchayapatra") {
            item { Comparison(out, words, lang) }
        }

        /* ---------- what a reader changes ---------- */
        item {
            Pane {
                for (field in calc.fields) {
                    CalcField(calc, field, inputs, state, onState, words, lang)
                }
            }
        }

        item {
            Pane {
                Text(
                    words.t("calc.disclaimer", lang),
                    style = bodyStyle(words.t("calc.disclaimer", lang)),
                    color = c.inkSoft,
                )
            }
        }
    }
}

/* ---------- printing ---------- */

/** A named number, the way `FORMATS` says. One place, so a figure
    and the same number inside a sentence cannot disagree about
    whether it is money. */
private fun printed(name: String, v: Double, words: ToolWords, lang: String): String {
    if (!v.isFinite()) return NOTHING
    return when (FORMATS[name]) {
        Kind.MONEY, Kind.PRICE -> money(v, words, lang)
        Kind.PERCENT -> "${fmtNum(v, lang, 1)}%"
        Kind.YEARS -> fmtNum(v, lang, 0)
        else -> fmtNum(v, lang, 2)
    }
}

/** ৳12,34,567, shortened once it stops being readable.

    The site's own `money()`, thresholds included: a crore at ten
    million and a lakh at a hundred thousand, which is how anybody
    in Bangladesh says a number this size out loud. */
private fun money(v: Double, words: ToolWords, lang: String): String {
    if (!v.isFinite()) return NOTHING
    val magnitude = abs(v)
    return when {
        magnitude >= 1e7 -> "৳${fmtNum(v / 1e7, lang, 2)} ${words.t("t.crore", lang)}"
        magnitude >= 1e5 -> "৳${fmtNum(v / 1e5, lang, 2)} ${if (lang == "bn") "লাখ" else "lakh"}"
        else -> "৳${fmtNum(v, lang, 0)}"
    }
}

/** A sentence with its numbers in it, each printed by name. */
private fun fill(words: ToolWords, key: String, lang: String, out: Outcome): String =
    words.t(key, lang, out.values.mapValues { (n, v) -> printed(n, v, words, lang) })

/* ---------- the chart ---------- */

/** Two lines over the same span, the first in the accent.

    Deliberately not a library and deliberately not filled: on a
    handset a stacked area of two series is one shape a reader
    cannot separate, and the whole point of every one of these
    charts is the GAP between the two. */
@Composable
private fun TwoLines(first: List<Double>, second: List<Double>) {
    val c = LocalReiad.current
    if (first.size < 2) return
    val top = (first + second).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        fun path(values: List<Double>): Path = Path().apply {
            values.forEachIndexed { i, v ->
                val x = size.width * (i.toFloat() / (values.size - 1))
                val y = size.height * (1f - (v / top).toFloat())
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        /* A baseline, so a line at nought is a line rather than
           the bottom of the box. */
        drawLine(
            c.hairline,
            Offset(0f, size.height),
            Offset(size.width, size.height),
            strokeWidth = 1f,
        )
        val stroke = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path(second), c.inkSoft, style = stroke)
        drawPath(path(first), c.accent, style = stroke)
    }
}

/* ---------- the comparison ---------- */

/** Sanchayapatra beside FDR, with the working shown: what each
    pays, what tax takes, what is left and what comes back.

    The site shows this and the app must too. A comparison that
    prints two totals and hides the arithmetic is asking to be
    trusted, which is the one thing every tool here refuses. */
@Composable
private fun Comparison(out: Outcome, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    val sWins = out.values.getValue("sTotal") >= out.values.getValue("fTotal")

    Pane {
        for ((prefix, wins) in listOf("s" to sWins, "f" to !sWins)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    words.t("calc.sanchayapatra.${prefix}Total", lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (wins) c.accent else c.ink,
                    modifier = Modifier.weight(1f),
                )
                if (wins) Chip(words.t("calc.ahead", lang))
            }
            Spacer(Modifier.height(Gap.s3))
            for (part in listOf("Gross", "PaidTax", "Net", "Total")) {
                val name = prefix + part
                Row(Modifier.padding(vertical = Gap.s2)) {
                    Text(
                        words.t("calc.part.${part.replaceFirstChar { it.lowercase() }}", lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        (if (part == "PaidTax") "− " else "") +
                            printed(name, out.values.getValue(name), words, lang),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (part == "PaidTax") c.gold else c.ink,
                    )
                }
            }
            Spacer(Modifier.height(Gap.s6))
        }
    }
}

/* ---------- one input ---------- */

@Composable
private fun CalcField(
    calc: Calculator,
    field: Field,
    inputs: Map<String, Double>,
    state: CalcState,
    onState: (CalcState) -> Unit,
    words: ToolWords,
    lang: String,
) {
    val c = LocalReiad.current
    val now = inputs.getValue(field.name)

    fun put(v: Double) {
        onState(
            state.copy(
                values = state.values + (calc.id to (inputs + (field.name to v))),
            ),
        )
    }

    Column(Modifier.padding(vertical = Gap.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                words.t("calc.${calc.id}.f.${field.name}", lang),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
                modifier = Modifier.weight(1f),
            )
            /* The live value beside the label, which is what makes
               a slider readable: a track with no number on it is a
               control a reader has to guess at. */
            Text(
                printed(field.name, now, words, lang),
                style = MaterialTheme.typography.labelLarge,
                color = c.ink,
                textAlign = TextAlign.End,
            )
        }

        if (field.typed) {
            /* Text, not a Double: a box bound to a number cannot
               hold "1." or an empty string, so deleting the last
               digit makes a nought appear under the caret. */
            var typed by remember(calc.id, field.name) { mutableStateOf(plain(now)) }
            LaunchedEffect(now) {
                if (typed.toDoubleOrNull() != now) typed = plain(now)
            }
            TextField(
                value = typed,
                onValueChange = { text ->
                    typed = text
                    text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::put)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.paperSunk,
                    unfocusedContainerColor = c.paperSunk,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                    cursorColor = c.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        Slider(
            value = now.toFloat().coerceIn(field.min.toFloat(), field.max.toFloat()),
            onValueChange = { v ->
                /* Snapped to the field's own step, or a drag
                   writes 12.700000000000001 into a link. */
                put(((v / field.step).roundToInt() * field.step))
            },
            valueRange = field.min.toFloat()..field.max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.paperSunk,
            ),
            modifier = Modifier.semantics {
                contentDescription = words.t("calc.${calc.id}.f.${field.name}", lang)
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                inScript(plain(field.min), lang),
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
                modifier = Modifier.weight(1f),
            )
            Text(
                inScript(plain(field.max), lang),
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
    }
}

/** `40` rather than `40.0`, and `12.7` rather than
    `12.700000000000001`. */
private fun plain(v: Double): String {
    val s = java.math.BigDecimal.valueOf(v)
        .setScale(4, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return s.toPlainString()
}
