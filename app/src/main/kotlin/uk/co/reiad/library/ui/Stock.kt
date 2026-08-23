package uk.co.reiad.library.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.stock.Analysis
import uk.co.reiad.library.core.stock.Drag
import uk.co.reiad.library.core.stock.FIELDS
import uk.co.reiad.library.core.stock.Field
import uk.co.reiad.library.core.stock.Flag
import uk.co.reiad.library.core.stock.Inputs
import uk.co.reiad.library.core.stock.Keys
import uk.co.reiad.library.core.stock.SPREAD_TOO_WIDE
import uk.co.reiad.library.core.stock.bandAt
import uk.co.reiad.library.core.stock.bandBelow
import uk.co.reiad.library.core.stock.buyBelow
import uk.co.reiad.library.core.stock.NOTHING
import uk.co.reiad.library.core.stock.PILLARS
import uk.co.reiad.library.core.stock.Pillar
import uk.co.reiad.library.core.stock.Scored
import uk.co.reiad.library.core.stock.Signal
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.core.stock.WEIGHT_PRESETS
import uk.co.reiad.library.core.stock.Weights
import uk.co.reiad.library.core.stock.analyse
import uk.co.reiad.library.core.stock.fmtNum
import uk.co.reiad.library.core.stock.fmtTk
import uk.co.reiad.library.core.stock.fmtValue
import uk.co.reiad.library.core.stock.groupsFor
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.stock.OPEN_BY_DEFAULT
import uk.co.reiad.library.core.stock.valueOf
import uk.co.reiad.library.core.stock.withField

/* ============================================================
   The stock check.

   The site's page, on a handset, over the same model: forty-four
   metrics, six pillars, the vetoes, the flags, the combined
   signals, a triangulated fair value, the Shariah screen and a
   verdict that can be capped by the price.

   ---- what a port of this has to get right ----

   The page's whole argument is that it SHOWS ITS OWN ARITHMETIC.
   A verdict with no reasons under it is a stock rating, which is
   the thing this tool was written not to be. So none of the
   sections below is optional and none is behind a "see more": the
   drags, the flags, the pillar breakdown and the metric table are
   the tool, and the score is a summary of them.

   ---- and the order ----

   Verdict, then what is dragging it, then what could be wrong
   with it, then the pillars, then every metric. A reader who
   stops after two screens has the answer and the two strongest
   reasons to doubt it, which is the right thing to have if you
   are going to stop reading.
   ============================================================ */

/** Everything the screen holds, which is deliberately small: the
    numbers, the weights, and which language. The analysis is
    DERIVED on every recomposition rather than stored, because a
    stored copy is a copy that can be one keystroke behind, and
    the whole model runs in well under a millisecond. */
data class StockState(
    val inputs: Inputs = Inputs(),
    val weights: Weights = WEIGHT_PRESETS.getValue("balanced"),
    val style: String = "balanced",
    val lang: String = "en",
    val open: Set<String> = OPEN_BY_DEFAULT,
)

/**
 * A calculator with no words yet: the shape of one, or what
 * stopped them arriving.
 *
 * The phrases come down from `/api/tools` because they are DATA
 * and an edited Bangla sentence must reach a phone with no app
 * release. That is right, and it means every calculator on this
 * app depends on a fetch, which means every one of them needs all
 * three of `Waiting.kt`'s states rather than two.
 *
 * It had two. The null branch drew an empty Box, on the argument
 * that a spinner flashing for one frame is worse than nothing.
 * The argument holds for a SLOW fetch and says nothing about a
 * FAILED one, and when `/api/tools` was not yet live the stock
 * check and all five calculators opened on a black page.
 */
@Composable
fun ToolsWaiting(
    problem: String?,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(horizontal = Gap.s8, vertical = Gap.s10)) {
        if (problem == null) {
            Skeleton(lines = 4, label = "Reading the calculators")
        } else {
            Problem(
                title = "The calculators could not load",
                detail = "$problem\n\nThe arithmetic is in this app; " +
                    "the words it says are the site's, so it needs one " +
                    "answer from reiad.co.uk before it can show you any of it.",
                onRetry = onRetry,
            )
        }
    }
}

@Composable
fun StockScreen(
    words: ToolWords,
    state: StockState,
    onState: (StockState) -> Unit,
    onCopyLink: () -> Unit,
    onExport: () -> Unit,
    onLang: (String) -> Unit,
    /** Save this check under a name, or null when nobody is
        signed in.

        Null rather than a disabled button: a control that cannot
        do anything is a promise the screen cannot keep, and the
        site does not draw this signed out either. */
    onSave: ((String) -> Unit)? = null,
    /** What the last save said. The site prints the server's own
        words here and so does this. */
    saveNote: String? = null,
    note: String? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = LocalReiad.current
    val lang = state.lang
    fun t(key: String, vars: Map<String, String> = emptyMap()) = words.t(key, lang, vars)

    val a = remember(state.inputs, state.weights) { analyse(state.inputs, state.weights) }

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
    ) {
        item {
            Column(Modifier.padding(top = Gap.s6)) {
                PageHead(
                    title = t(Keys.TITLE),
                    eyebrow = t(Keys.EYEBROW),
                    lede = t(Keys.LEDE),
                    modifier = Modifier.semantics { heading() },
                )

                /* The language, which is the reader's and not
                   this screen's. It is `tool-lang`, the key the
                   calculators have read since long before there
                   were accounts, so choosing Bangla here is the
                   same choice as choosing it on the site and it
                   travels between devices like any other. */
                Spacer(Modifier.height(Gap.s5))
                /* The same switch as everywhere else: hold and
                   slide. Two loose chips were two separate hit
                   targets, which is the shape that stopped six
                   preferences working; see `Segmented.kt`. */
                LangSwitch(lang, onLang)
            }
        }

        /* ---------- the verdict ---------- */
        item { Verdict(a, words, lang) }

        /* ---------- what would have to change ---------- */
        if (a.drags.isNotEmpty()) {
            item { Drags(a.drags, words, lang) }
        }

        /* ---------- what might be wrong with it ---------- */
        if (a.flags.isNotEmpty()) item { Flags(a.flags, words, lang) }
        if (a.signals.isNotEmpty()) item { Signals(a.signals, words, lang) }

        /* ---------- the six pillars, and the reader's weights ---------- */
        item { Pillars(a.pillars, state, onState, words, lang) }

        /* ---------- fair value and the screen ---------- */
        item { FairValue(a, words, lang) }
        item { ShariahBox(a, words, lang) }

        /* ---------- every metric, grouped by pillar ---------- */
        item { SectionHeading(t(Keys.SEC_SCORECARD), t(Keys.SEC_SCORECARD_NOTE)) }
        for (pillar in PILLARS) {
            val rows = a.scored.filter { it.pillar == pillar }
            item(key = "metrics-$pillar") {
                MetricGroup(pillar, rows, words, lang)
            }
        }

        /* ---------- and the numbers themselves ---------- */
        item { SectionHeading(t(Keys.WEIGHTS), t(Keys.WEIGHTS_NOTE)) }
        for (group in groupsFor(state.inputs.sector)) {
            item(key = "fields-$group") {
                FieldGroup(group, state, onState, words, lang)
            }
        }

        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
                    Control(Modifier.clickable { onCopyLink() }) {
                        Text(
                            t(Keys.COPY_LINK),
                            style = MaterialTheme.typography.labelLarge,
                            color = c.ink,
                            maxLines = 1,
                        )
                    }
                    Control(Modifier.clickable { onExport() }) {
                        Text(
                            t(Keys.DOWNLOAD),
                            style = MaterialTheme.typography.labelLarge,
                            color = c.ink,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(Gap.s4))
                Control(Modifier.clickable { onState(StockState(lang = state.lang)) }) {
                    Text(t(Keys.RESET), style = MaterialTheme.typography.labelLarge, color = c.ink)
                }
                if (onSave != null) {
                    Spacer(Modifier.height(Gap.s7))
                    SaveCheck(
                        label = t(Keys.SAVE_LABEL),
                        button = t(Keys.SAVE),
                        needsName = t(Keys.SAVE_NAMED),
                        note = saveNote,
                        onSave = onSave,
                    )
                }
                if (note != null) {
                    Spacer(Modifier.height(Gap.s4))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
                }
            }
        }

        /* The page ends on what it cannot see, deliberately, and
           not in smaller type than the verdict. */
        item {
            Pane {
                Text(
                    t(Keys.DISC_TITLE),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                )
                Spacer(Modifier.height(Gap.s3))
                Text(t(Keys.DISC_BODY), style = bodyStyle(t(Keys.DISC_BODY)), color = c.inkSoft)
                Spacer(Modifier.height(Gap.s3))
                Text(t(Keys.DISC_UNITS), style = bodyStyle(t(Keys.DISC_UNITS)), color = c.inkSoft)
            }
        }
    }
}

/* ============================================================
   The verdict
   ============================================================ */

@Composable
private fun Verdict(a: Analysis, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    val score = a.score
    /* It fills rather than appearing, for the reason the school
       ring does: a bar drawn at its value states a number, a bar
       that runs to it says the reader put it there. */
    val filled by animateFloatAsState(
        ((score ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
        tween(Motion.SLOW_MS),
        label = "score",
    )

    Pane {
        Text(
            words.t(Keys.VERDICT, lang),
            style = MaterialTheme.typography.labelMedium,
            color = c.inkSoft,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            words.t("verdict.${a.verdict.id}", lang),
            style = headingStyle(words.t("verdict.${a.verdict.id}", lang)),
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s4))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (score == null) NOTHING else fmtNum(score, lang, 0),
                style = MaterialTheme.typography.displaySmall,
                color = c.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(Gap.s4))
            Text(
                words.t(Keys.OUT_OF, lang),
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft,
            )
        }
        Spacer(Modifier.height(Gap.s4))
        Groove(filled, height = 10.dp)

        Spacer(Modifier.height(Gap.s5))
        Text(
            words.t("verdict.${a.verdict.id}.why", lang),
            style = bodyStyle(words.t("verdict.${a.verdict.id}.why", lang)),
            color = c.inkSoft,
        )

        /* A cap that bit has to SAY it bit. A silent correction is
           the one thing a tool showing its own arithmetic must
           never do, and the site says so at length. */
        if (a.capped) {
            Spacer(Modifier.height(Gap.s5))
            Plate {
                Text(
                    words.t(Keys.CAPPED, lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.gold,
                )
                Spacer(Modifier.height(Gap.s2))
                Text(
                    words.t(
                        Keys.CAPPED_WHY, lang,
                        mapOf("earned" to words.t("verdict.${a.earned.id}", lang)),
                    ),
                    style = bodyStyle(words.t(Keys.CAPPED_WHY, lang)),
                    color = c.ink,
                )
            }
        }
        if (a.vetoed && a.veto != null) {
            Spacer(Modifier.height(Gap.s5))
            Plate(ground = c.danger.copy(alpha = 0.10f)) {
                Text(
                    words.t(Keys.VETOED, lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.danger,
                )
                Spacer(Modifier.height(Gap.s2))
                Text(
                    words.t(Keys.VETOED_WHY, lang),
                    style = bodyStyle(words.t(Keys.VETOED_WHY, lang)),
                    color = c.ink,
                )
            }
        }

        /* How much room there is either side, so a verdict is not
           presented as though it were solid. */
        val edges = a.edges
        if (score != null && (edges.up != null || edges.down != null)) {
            Spacer(Modifier.height(Gap.s4))
            val bits = buildList {
                edges.down?.let {
                    add(
                        fmtNum(score - it, lang, 1) + " " +
                            (if (lang == "bn") "\u09aa\u09df\u09c7\u09a8\u09cd\u099f \u0995\u09ae\u09b2\u09c7" else "points lower") +
                            " \u2192 " + words.t("verdict.${bandBelow(it)}", lang),
                    )
                }
                edges.up?.let {
                    add(
                        fmtNum(it - score, lang, 1) + " " +
                            (if (lang == "bn") "\u09aa\u09df\u09c7\u09a8\u09cd\u099f \u09ac\u09be\u09dc\u09b2\u09c7" else "points higher") +
                            " \u2192 " + words.t("verdict.${bandAt(it)}", lang),
                    )
                }
            }
            Text(
                bits.joinToString("  \u00b7  "),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        /* The price to compare against: the median fair value
           discounted by the margin a value buyer would want. Blank
           rather than invented when nothing could be valued, since
           that refusal is the fair-value section's whole argument
           and it has to hold here too. */
        Spacer(Modifier.height(Gap.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                words.t(Keys.BUY_BELOW, lang),
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
            Spacer(Modifier.width(Gap.s4))
            val bb = buyBelow(a.fair)
            Text(
                if (bb.isFinite()) fmtTk(bb, lang) else words.t(Keys.NO_BUY_BELOW, lang),
                style = MaterialTheme.typography.titleSmall,
                color = if (bb.isFinite()) c.ink else c.inkSoft,
            )
        }
    }
}

/* ============================================================
   What is dragging it down
   ============================================================ */

@Composable
private fun Drags(drags: List<Drag>, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    Pane {
        SectionHeading(words.t(Keys.SEC_DRAGS, lang), words.t(Keys.SEC_DRAGS_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))
        for (d in drags) {
            Rung {
                Column(Modifier.weight(1f)) {
                    Text(
                        words.t("m.${d.id}", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                    )
                    Spacer(Modifier.height(Gap.s2))
                    Groove((d.score / 100.0).toFloat(), height = 4.dp)
                }
                Spacer(Modifier.width(Gap.s5))
                Text(
                    "-${fmtNum(d.cost, lang, 1)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.danger,
                )
            }
            Spacer(Modifier.height(Gap.s3))
        }
    }
}

/* ============================================================
   Flags, and combined signals
   ============================================================ */

private fun varsFor(f: Flag, lang: String): Map<String, String> =
    f.vars.mapValues { (_, v) -> fmtNum(v, lang, 2) }

@Composable
private fun Flags(flags: List<Flag>, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    Pane {
        SectionHeading(words.t(Keys.SEC_FLAGS, lang), words.t(Keys.SEC_FLAGS_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))
        for (f in flags) {
            val tone = when (f.level) {
                "veto", "bad" -> c.danger
                "warn" -> c.gold
                else -> c.inkSoft
            }
            Row(Modifier.padding(vertical = Gap.s3)) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(Corner.pill))
                        .background(tone),
                )
                Spacer(Modifier.width(Gap.s4))
                Text(
                    words.t("f.${f.id}", lang, varsFor(f, lang)),
                    style = bodyStyle(words.t("f.${f.id}", lang)),
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Signals(signals: List<Signal>, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    Pane {
        SectionHeading(words.t(Keys.SEC_SIGNALS, lang), words.t(Keys.SEC_SIGNALS_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))
        for (s in signals) {
            Column(Modifier.padding(vertical = Gap.s3)) {
                Text(
                    words.t("s.${s.id}", lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = when (s.tone) {
                        "bad" -> c.danger
                        "good" -> c.accent
                        "warn" -> c.gold
                        else -> c.ink
                    },
                )
                Spacer(Modifier.height(Gap.s2))
                Text(
                    words.t("s.${s.id}.why", lang),
                    style = bodyStyle(words.t("s.${s.id}.why", lang)),
                    color = c.inkSoft,
                )
            }
        }
    }
}

/* ============================================================
   The six pillars, and the weights, which are the reader's
   ============================================================ */

@Composable
private fun Pillars(
    pillars: Map<String, Pillar>,
    state: StockState,
    onState: (StockState) -> Unit,
    words: ToolWords,
    lang: String,
) {
    val c = LocalReiad.current
    Pane {
        SectionHeading(words.t(Keys.SEC_PILLARS, lang), words.t(Keys.SEC_PILLARS_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))

        /* The four presets, as a row of chips. A link carrying its
           own weights shows none of them chosen, because a chip
           lit while the page uses something else is a chip
           claiming weights that are not being used. */
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Gap.s4),
        ) {
            for (id in WEIGHT_PRESETS.keys) {
                val chosen = state.style == id
                Box(
                    Modifier.clickable {
                        onState(
                            state.copy(style = id, weights = WEIGHT_PRESETS.getValue(id)),
                        )
                    },
                ) {
                    Chip(
                        words.t("style.$id", lang),
                        tone = if (chosen) c.accent else c.inkSoft,
                    )
                }
            }
        }

        Spacer(Modifier.height(Gap.s5))
        for (p in PILLARS) {
            val pillar = pillars.getValue(p)
            val weight = state.weights[p] ?: 0.0
            Column(Modifier.padding(vertical = Gap.s3)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        words.t("pillar.$p", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (pillar.score == null) NOTHING else fmtNum(pillar.score!!, lang, 0),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (pillar.score == null) c.inkSoft else c.accent,
                    )
                    Spacer(Modifier.width(Gap.s4))
                    /* How much of the pillar could be asked, which
                       a bank makes matter: three of eight is a
                       weaker statement than eight of eight, and a
                       reader has to be able to see which. */
                    Text(
                        inScript("${pillar.n}/${pillar.of}", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
                Spacer(Modifier.height(Gap.s2))
                Groove(((pillar.score ?: 0.0) / 100.0).toFloat(), height = 5.dp)
                Spacer(Modifier.height(Gap.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        words.t(Keys.GIVES, lang, mapOf("v" to fmtNum(weight, lang, 0))),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                    Slider(
                        value = weight.toFloat(),
                        onValueChange = { v ->
                            onState(
                                state.copy(
                                    style = "custom",
                                    weights = state.weights + (p to v.toDouble()),
                                ),
                            )
                        },
                        valueRange = 0f..40f,
                        steps = 39,
                        colors = SliderDefaults.colors(
                            thumbColor = c.accent,
                            activeTrackColor = c.accent,
                            inactiveTrackColor = c.paperSunk,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Gap.s4)
                            .semantics { contentDescription = words.t("pillar.$p", lang) },
                    )
                    Text(
                        fmtNum(weight, lang, 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.ink,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/* ============================================================
   Fair value, and the Shariah screen
   ============================================================ */

@Composable
private fun FairValue(a: Analysis, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    val fair = a.fair
    Pane {
        SectionHeading(words.t(Keys.SEC_FAIR, lang), words.t(Keys.SEC_FAIR_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))
        if (fair.anchors.isEmpty() || !fair.mid.isFinite()) {
            Text(
                words.t(Keys.FV_NONE, lang),
                style = bodyStyle(words.t(Keys.FV_NONE, lang)),
                color = c.inkSoft,
            )
            return@Pane
        }
        FairRow(words.t(Keys.FV_LOWEST, lang), fmtTk(fair.low, lang))
        FairRow(words.t(Keys.FV_MEDIAN, lang), fmtTk(fair.mid, lang), strong = true)
        FairRow(words.t(Keys.FV_HIGHEST, lang), fmtTk(fair.high, lang))
        FairRow(words.t(Keys.FV_PRICE, lang), fmtTk(a.d.price, lang))

        Spacer(Modifier.height(Gap.s4))
        if (fair.marginOfSafety <= 0) {
            Text(
                words.t(Keys.FV_MOS_OVER, lang),
                style = bodyStyle(words.t(Keys.FV_MOS_OVER, lang)),
                color = c.gold,
            )
        } else {
            Row {
                Text(
                    words.t(Keys.FV_MOS, lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    fmtValue(fair.marginOfSafety, "%", lang),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.accent,
                )
            }
        }
        Spacer(Modifier.height(Gap.s4))
        for (anchor in fair.anchors) {
            Row(Modifier.padding(vertical = Gap.s2)) {
                Text(
                    words.t("fv.${anchor.id}", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    fmtTk(anchor.value, lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink,
                )
            }
        }
        /* The spread is the honest part: four anchors across a 4×
           range means this cannot be valued from these inputs, and
           saying so is worth more than a midpoint. */
        /* Anchors disagreeing by more than three times IS the
           answer, and the site says so in a sentence rather than
           printing a midpoint and hoping. */
        if (fair.spread > SPREAD_TOO_WIDE) {
            Spacer(Modifier.height(Gap.s3))
            Text(
                words.t(Keys.FV_WIDE, lang),
                style = bodyStyle(words.t(Keys.FV_WIDE, lang)),
                color = c.gold,
            )
        }
    }
}

@Composable
private fun ShariahBox(a: Analysis, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    Pane {
        SectionHeading(words.t(Keys.SEC_SHARIAH, lang), words.t(Keys.SEC_SHARIAH_NOTE, lang))
        Spacer(Modifier.height(Gap.s4))
        Text(
            words.t(if (a.shariah.pass) Keys.SH_PASS else Keys.SH_FAIL, lang),
            style = MaterialTheme.typography.titleSmall,
            color = if (a.shariah.pass) c.accent else c.gold,
        )
        Spacer(Modifier.height(Gap.s3))
        for (test in a.shariah.tests) {
            Row(Modifier.padding(vertical = Gap.s2)) {
                Text(
                    words.t("sh.${test.id}", lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${fmtValue(test.value, "%", lang)} / ${fmtValue(test.limit, "%", lang)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (test.pass) c.ink else c.gold,
                )
            }
        }
        Spacer(Modifier.height(Gap.s3))
        Text(
            words.t(Keys.SH_CAVEAT, lang),
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}

/* ============================================================
   Every metric, with its own number beside it
   ============================================================ */

@Composable
private fun MetricGroup(pillar: String, rows: List<Scored>, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    Pane {
        Text(
            words.t("pillar.$pillar", lang),
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s4))
        for (m in rows) MetricRow(m, words, lang)
    }
}

@Composable
private fun MetricRow(m: Scored, words: ToolWords, lang: String) {
    val c = LocalReiad.current
    var open by remember { mutableStateOf(false) }

    Column(Modifier.clickable { open = !open }) {
        Rung {
            Column(Modifier.weight(1f)) {
                Text(
                    words.t("m.${m.id}", lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (m.na) c.inkSoft else c.ink,
                )
                if (!m.na) {
                    Spacer(Modifier.height(Gap.s2))
                    Groove(((m.score ?: 0.0) / 100.0).toFloat(), height = 4.dp)
                }
            }
            Spacer(Modifier.width(Gap.s5))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmtValue(m.raw, m.fmt, lang),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (m.na) c.inkSoft else c.ink,
                )
                /* "Not applicable" is a different answer from
                   "scored zero" and has to read as one, or a bank
                   looks like a company failing seven tests. */
                Text(
                    if (m.na) words.t(Keys.NA_REASON, lang) else words.t("grade.${m.grade}", lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.na) c.inkSoft else gradeColour(m.grade, c),
                )
            }
        }
        AnimatedVisibility(open) {
            Text(
                words.t("m.${m.id}.why", lang),
                style = bodyStyle(words.t("m.${m.id}.why", lang)),
                color = c.inkSoft,
                modifier = Modifier.padding(
                    start = Gap.s5, end = Gap.s5, top = Gap.s3, bottom = Gap.s4,
                ),
            )
        }
    }
}

@Composable
private fun gradeColour(grade: String, c: ReiadColours): Color = when (grade) {
    "strong", "good" -> c.accent
    "fair" -> c.inkSoft
    "weak" -> c.gold
    "poor" -> c.danger
    else -> c.inkSoft
}

/* ============================================================
   The form
   ============================================================ */

@Composable
private fun FieldGroup(
    group: String,
    state: StockState,
    onState: (StockState) -> Unit,
    words: ToolWords,
    lang: String,
) {
    val c = LocalReiad.current
    val open = group in state.open
    Pane {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onState(
                        state.copy(
                            open = if (open) state.open - group else state.open + group,
                        ),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                words.t("g.$group", lang),
                style = MaterialTheme.typography.titleSmall,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (open) "−" else "+",
                style = MaterialTheme.typography.titleMedium,
                color = c.inkSoft,
            )
        }
        AnimatedVisibility(open) {
            Column {
                Spacer(Modifier.height(Gap.s4))
                for (field in FIELDS.filter { it.group == group }) {
                    FieldRow(field, state, onState, words, lang)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    field: Field,
    state: StockState,
    onState: (StockState) -> Unit,
    words: ToolWords,
    lang: String,
) {
    val c = LocalReiad.current
    val stored = valueOf(state.inputs, field.id)

    /* The box holds TEXT, not a number, and that is not laziness.
       A number field bound to a Double cannot hold "1." or "-" or
       an empty string, so a reader deleting the last digit finds a
       nought appear under the caret, and a reader typing "1.5"
       watches "1" become 1 before the point arrives.

       So the text is the reader's and the model is the model's,
       and they are resynced only when the model moved for a
       reason that was not this box: a reset, a preset, or the
       slider under it. The test for that is whether what is typed
       still WOULD produce what is stored, which leaves "1." and
       "" alone while catching a value that changed underneath. */
    var typed by remember(field.id) { mutableStateOf(stored) }
    LaunchedEffect(stored) {
        if (valueOf(withField(state.inputs, field.id, typed), field.id) != stored) {
            typed = stored
        }
    }

    Column(Modifier.padding(vertical = Gap.s3)) {
        Text(
            words.t("i.${field.id}", lang),
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s2))

        if (field.choices != null) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Gap.s3),
            ) {
                for (choice in field.choices!!) {
                    val chosen = stored == choice
                    Box(
                        Modifier.clickable {
                            onState(
                                state.copy(
                                    inputs = withField(state.inputs, field.id, choice),
                                ),
                            )
                        },
                    ) {
                        Chip(
                            field.choicePrefix?.let { words.t("$it$choice", lang) } ?: choice,
                            tone = if (chosen) c.accent else c.inkSoft,
                        )
                    }
                }
            }
            return@Column
        }

        Field(
            value = typed,
            onValue = { text ->
                typed = text
                onState(state.copy(inputs = withField(state.inputs, field.id, text)))
            },
            description = words.t("i.${field.id}", lang),
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        field.slider?.let { s ->
            val now = stored.toDoubleOrNull() ?: s.low
            Slider(
                value = now.toFloat().coerceIn(s.low.toFloat(), s.high.toFloat()),
                onValueChange = { v ->
                    /* Snapped to the field's own increment, or a
                       drag writes 12.700000000000001 into a box
                       somebody has to read. */
                    val snapped = Math.round(v / s.by) * s.by
                    val text = trimZeros(snapped)
                    typed = text
                    onState(state.copy(inputs = withField(state.inputs, field.id, text)))
                },
                valueRange = s.low.toFloat()..s.high.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = c.accent,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.paperSunk,
                ),
                modifier = Modifier.semantics { contentDescription = words.t("i.${field.id}", lang) },
            )
        }
    }
}

/** `12.7` rather than `12.700000000000001`, and `40` rather than
    `40.0`: what goes into the box is what goes into the link. */
private fun trimZeros(v: Double): String {
    val s = java.math.BigDecimal.valueOf(v)
        .setScale(4, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return s
}

/* ---------- small shared pieces ---------- */

/** A row of the fair-value table: what an anchor is called, and
    what it says. Four of them rather than a bar, because on a
    handset a bar with four ticks on it is four ticks nobody can
    tell apart. */
@Composable
private fun FairRow(label: String, value: String, strong: Boolean = false) {
    val c = LocalReiad.current
    Row(Modifier.padding(vertical = Gap.s2)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleSmall
            else MaterialTheme.typography.bodySmall,
            color = if (strong) c.accent else c.ink,
        )
    }
}

@Composable
private fun SectionHeading(title: String, why: String) {
    val c = LocalReiad.current
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (why.isNotBlank() && why != title) {
            Spacer(Modifier.height(Gap.s2))
            Text(why, style = bodyStyle(why), color = c.inkSoft)
        }
    }
}

/**
 * Save this check under a name.
 *
 * What is stored is the QUERY STRING, which the caller builds
 * from the same encoder every shared link has been proving
 * correct for a year. This composable never sees the numbers: it
 * takes a name and hands it back.
 */
@Composable
private fun SaveCheck(
    label: String,
    button: String,
    needsName: String,
    note: String?,
    onSave: (String) -> Unit,
) {
    val c = LocalReiad.current
    var name by remember { mutableStateOf("") }
    var complaint by remember { mutableStateOf<String?>(null) }
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s4))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Gap.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Field(
                    value = name,
                    onValue = { name = it; complaint = null },
                    description = label,
                    hint = "Beximco, August",
                    /* The column is `char_length(name) <= 80`, so
                       the cap is where the keystroke is rather
                       than where the save is: a box that accepts
                       ninety characters and rejects them on Save
                       is a box that lied while you were typing. */
                    filter = { it.take(80) },
                )
            }
            PillButton(
                label = button,
                onClick = {
                    /* Named before sent. The column is
                        `char_length(name) <= 80` and a blank one
                        is a row nobody can tell apart from the
                        next blank one. */
                    if (name.isBlank()) complaint = needsName else onSave(name.trim())
                },
            )
        }
        val said = complaint ?: note
        if (said != null) {
            Spacer(Modifier.height(Gap.s4))
            Text(said, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        }
    }
}
