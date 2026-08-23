package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.diet.Body
import uk.co.reiad.library.core.diet.DietDay
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.DietProfile
import uk.co.reiad.library.core.diet.FloorHit
import uk.co.reiad.library.core.diet.Target
import uk.co.reiad.library.core.diet.bmi
import uk.co.reiad.library.core.diet.bmiBand
import uk.co.reiad.library.core.diet.totalOf
import uk.co.reiad.library.core.diet.whtr
import uk.co.reiad.library.core.diet.whtrBand

/* ============================================================
   Today, which is the page of the diet tool a reader opens.

   `DIET.md` §29 lists fourteen pages and this is the first of
   them: what was eaten, what the day comes to, and how that sits
   against the target. The other thirteen are reference and open
   on the site until they are ported.

   ---- what this screen will not do ----

   Print a nought. A day nobody has logged reads "nothing logged
   yet" rather than "0 kcal", for the reason the routine tool's
   own §0 gives one directory along: a zero is a judgement wearing
   a number's clothes, and a reader opening the app at eight in
   the morning has not failed at anything.

   And it will not print a target without the FLOORS beside it. A
   figure that was clamped and does not say so is the site's own
   phrase for it: a lie of omission.
   ============================================================ */

data class DietState(
    val loading: Boolean = true,
    val signedOut: Boolean = false,
    val today: String = "",
    val profile: DietProfile? = null,
    val day: DietDay? = null,
    val entries: List<DietEntry> = emptyList(),
    val body: Body? = null,
    val target: Target? = null,
    val maintenance: Double? = null,
    val saving: Boolean = false,
)

@Composable
fun DietScreen(
    state: DietState,
    onWeight: (Double) -> Unit,
    onRemove: (String) -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** The tool's own words, out of `/api/site`, so nothing about
        a body is written in Kotlin. */
    words: uk.co.reiad.library.core.DietWords = uk.co.reiad.library.core.DietWords(),
    lang: String = "bn",
) {
    val c = LocalReiad.current
    /* Two of the tool's fourteen pages, and the switch says which
       rather than a tab bar for two: today's log, and what the
       measurements say. The other twelve are the site's until
       they are ported. */
    var page by rememberSaveable { mutableStateOf("today") }
    val eaten = remember(state.entries) { totalOf(state.entries) }

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
    ) {
        item("head") {
            PageHead(
                title = "আজ কী খেলেন",
                eyebrow = "Tools · diet",
                lede = "What went in, what it comes to, and how that sits against "
                    + "what you set.",
                modifier = Modifier.semantics { heading() },
            )
        }

        if (state.signedOut) {
            item("out") { NeedsSignIn(onOpenSite) }
            return@LazyColumn
        }
        if (state.loading) {
            item("wait") { Skeleton(lines = 4, label = "Reading your log") }
            return@LazyColumn
        }
        if (state.profile?.heightCm == null) {
            item("setup") { NeedsSetup(onOpenSite) }
            return@LazyColumn
        }

        item("pages") {
            val pages = listOf(
                "today" to (if (lang == "bn") "আজ" else "Today"),
                "you" to (if (lang == "bn") "শরীর" else "You"),
            )
            Segmented(
                options = pages,
                chosen = pages.firstOrNull { it.first == page },
                onChoose = { page = it.first },
                height = Gap.tap,
                label = { it.second },
            ) { option, on ->
                Text(
                    option.second,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) c.paper else c.inkSoft,
                )
            }
            Spacer(Modifier.height(Gap.s6))
        }

        if (page == "you") {
            item("body") {
                DietBodyPanel(
                    body = state.body,
                    words = words,
                    lang = lang,
                    onOpenSite = onOpenSite,
                )
            }
            return@LazyColumn
        }

        /* ---------- where the day stands ---------- */
        item("standing") { Standing(state, eaten) }

        /* ---------- the scale ---------- */
        item("weight") { Weighing(state.day?.weightKg, onWeight) }

        /* ---------- what was eaten ---------- */
        /* The heading only where there is a list under it. With
           none, the card above already says the day is empty and
           a second heading saying so is the same sentence
           twice. */
        if (state.entries.isNotEmpty()) {
            item("eaten-head") {
                Spacer(Modifier.height(Gap.s5))
                Text(
                    "What you logged",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        if (state.entries.isEmpty()) {
            item("empty") {
                /* An InfoCard rather than an empty list: the end
                   of the road, and it says where the rest of the
                   tool is rather than pretending this screen is
                   all of it. */
                InfoCard(
                    title = "The food search is on the site",
                    dek = "Barcodes, the portion library and your own items are all "
                        + "there. This screen shows the day and takes a weight; the "
                        + "rest of the log is one tap away.",
                ) {
                    Spacer(Modifier.height(Gap.s6))
                    PillButton("Open the log", onOpenSite, icon = "arrow")
                }
            }
        }

        items(state.entries, key = { it.id ?: it.label }) { entry ->
            Eaten(entry) { entry.id?.let(onRemove) }
        }
    }
}

/* ---------- the day, in one card ---------- */

@Composable
private fun Standing(state: DietState, eaten: Double) {
    val c = LocalReiad.current
    val target = state.target
    Pane {
        if (state.entries.isEmpty()) {
            /* The sentence, never a nought. A reader opening this
               at eight in the morning has not failed at
               anything. */
            Text("আজ এখনো কিছু লেখা হয়নি", style = BanglaTitle, color = c.ink)
            Spacer(Modifier.height(Gap.s2))
            Text(
                "Nothing logged today yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    whole(eaten),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.accent,
                )
                Spacer(Modifier.width(Gap.s4))
                Text(
                    if (target == null) "kcal" else "of ${target.kcal} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (target != null && target.kcal > 0) {
                Spacer(Modifier.height(Gap.s5))
                Groove((eaten / target.kcal).toFloat().coerceIn(0f, 1f), height = 10.dp)
            }
        }

        if (target != null && target.floors.isNotEmpty()) {
            Spacer(Modifier.height(Gap.s6))
            /* Every bound that bound, said out loud. A figure
               that was clamped and does not say so is the site's
               own phrase for it: a lie of omission. */
            Text(
                floorWords(target),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }

        val body = state.body
        if (body != null) {
            Spacer(Modifier.height(Gap.s7))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s7)) {
                val value = bmi(body.weightKg, body.heightCm)
                Reading("BMI", one(value), bmiBand(value, body.ancestry).id)
                body.waistCm?.let { waist ->
                    val ratio = whtr(waist, body.heightCm)
                    Reading("WAIST ÷ HEIGHT", two(ratio), whtrBand(ratio).id)
                }
            }
        }
    }
}

/** One figure with its band under it. A `plate`: it is read and
    never pressed. */
@Composable
private fun Reading(name: String, value: String, band: String) {
    val c = LocalReiad.current
    Plate(Modifier.width(150.dp)) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = c.accent)
        Spacer(Modifier.height(Gap.s3))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = c.ink)
        Text(band, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
    }
}

/* ---------- the scale ---------- */

/**
 * Today's weight, typed in.
 *
 * The one thing this screen WRITES, and it is a partial upsert:
 * the day's other columns are absent from the body, so typing a
 * weight does not erase the waist somebody measured this morning.
 */
@Composable
private fun Weighing(weightKg: Double?, onWeight: (Double) -> Unit) {
    val c = LocalReiad.current
    var typed by remember(weightKg) { mutableStateOf(weightKg?.let { two(it) } ?: "") }

    Pane {
        Text("Weight today", style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s3))
        Text(
            /* The site's own line, because it is the thing a
               reader most needs to hear on the day the number
               goes up: one reading is noise and the trend is the
               signal. */
            "One reading is noise. It is the trend that means anything.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s6))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(140.dp)
                    .height(Gap.tap)
                    .material(uk.co.reiad.library.core.Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
                    .padding(horizontal = Gap.s7),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (typed.isEmpty()) {
                    Text("kg", style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
                }
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Gap.tap)
                        .semantics { contentDescription = "Today's weight in kilograms" },
                )
            }
            Spacer(Modifier.width(Gap.s6))
            PillButton(
                label = "Save",
                filled = typed.toDoubleOrNull() != null,
                onClick = { typed.toDoubleOrNull()?.let(onWeight) },
            )
        }
    }
}

/* ---------- one thing eaten ---------- */

@Composable
private fun Eaten(entry: DietEntry, onRemove: () -> Unit) {
    val c = LocalReiad.current
    Rung {
        Column(Modifier.weight(1f)) {
            Text(
                entry.labelBn?.takeIf { it.isNotBlank() } ?: entry.label,
                style = if (isBangla(entry.labelBn ?: entry.label)) {
                    BanglaBody.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = c.ink,
            )
            val amount = listOfNotNull(
                entry.qty?.let { two(it) },
                entry.unit?.takeIf { it.isNotBlank() },
            ).joinToString(" ")
            if (amount.isNotBlank()) {
                Text(amount, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            }
        }
        Text(
            entry.kcal?.let { whole(it) } ?: NOTHING,
            style = MaterialTheme.typography.labelMedium,
            color = c.inkSoft,
        )
        if (entry.id != null) {
            Spacer(Modifier.width(Gap.s5))
            Tap(onClick = onRemove, label = "Remove ${entry.label}") {
                Icon("close", size = 15.dp, tint = c.inkSoft)
            }
        }
    }
}

/* ---------- the two ways in ---------- */

@Composable
private fun NeedsSignIn(onOpenSite: () -> Unit) {
    InfoCard(
        title = "A log belongs to an account",
        dek = "What you eat is yours, so it lives behind a sign-in rather than "
            + "on this phone. Sign in on the account page and it is here.",
    )
}

@Composable
private fun NeedsSetup(onOpenSite: () -> Unit) {
    InfoCard(
        title = "The tool needs to know a few things first",
        dek = "A height, a year of birth and what you are aiming at. Three questions, "
            + "answered once, on the site: without them there is no arithmetic to do "
            + "and a tool that guessed would be describing somebody else's body.",
    ) {
        Spacer(Modifier.height(Gap.s6))
        PillButton("Answer them on the site", onOpenSite, icon = "arrow")
    }
}

/* ---------- words ---------- */

/** Every bound that bound, in the order it bound, as a sentence.

    All of them and not the last: a small person on a fast rate
    hits the rate cap, the resting burn and the absolute floor,
    and the last of the three is the least informative. */
private fun floorWords(t: Target): String {
    val said = t.floors.map {
        when (it) {
            FloorHit.RATE -> "the rate was capped"
            FloorHit.RESTING -> "it would have gone under your resting burn"
            FloorHit.ABSOLUTE -> "it would have gone under the hard floor"
            FloorHit.UNDERWEIGHT -> "there is no loss goal at this weight"
        }
    }
    return "This is not the figure you asked for: " + said.joinToString(", and ") + "."
}

private fun whole(v: Double): String = v.toLong().toString()

private fun one(v: Double): String = trimmed((v * 10).toLong() / 10.0)

private fun two(v: Double): String = trimmed((v * 100).toLong() / 100.0)

/** A number without a trailing nought nobody asked for.

    "2 eggs" rather than "2.0 eggs", which is what a quantity with
    no unit came out as: the decimal is there to carry 1.5, and on
    a whole number it reads as a measurement somebody took to one
    place. */
private fun trimmed(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private const val NOTHING = "–"
