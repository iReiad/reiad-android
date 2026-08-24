package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.FoodLibrary
import uk.co.reiad.library.core.diet.Nutrient
import uk.co.reiad.library.core.diet.DayTotal
import uk.co.reiad.library.core.diet.readingFor
import uk.co.reiad.library.core.diet.tooSparse
import uk.co.reiad.library.core.diet.totalFor
import uk.co.reiad.library.core.stock.inScript
import kotlin.math.roundToInt

/* ============================================================
   What the day actually held, and out of how much of it.

   ---- the second number is the whole point ----

   "Iron: about 9 mg, from 62% of today's food". A total with no
   coverage beside it is a confident figure that may be missing a
   third of the day, and `DIET.md` §15 refuses to print one. The
   denominator is every logged item and the numerator is the ones
   whose row carries the key, so a restaurant plate typed in free
   counts AGAINST coverage: it is food that was eaten and not
   measured, and saying so is the honest thing.

   ---- and nothing here judges ----

   Not one line says a figure is good or bad. A nutrient with a
   range shows where the day sits in it and stops; a nutrient with
   no range (carbohydrate, fat) shows the figure and no bar at
   all, because those are the split the reader CHOSE and a bar
   would tell somebody on keto they were failing at something they
   decided to do.

   ---- and none of the nineteen is named in this file ----

   The nutrients, their ranges, their units, the sentence under
   each and whose reference intake it is all arrive from
   `/api/foods`. A nutrient added on the site is drawn here with
   nothing published.
   ============================================================ */

@Composable
fun DietNutrientPanel(
    entries: List<DietEntry>,
    library: FoodLibrary?,
    lang: String,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current

    if (library == null || library.nutrients.isEmpty()) {
        InfoCard(
            title = if (lang == "bn") "পুষ্টির তালিকা আসেনি" else "The nutrient panel has not arrived",
            dek = if (lang == "bn") {
                "একবার নেট পেলে এটা এই ফোনে থেকে যাবে।"
            } else {
                "It arrives once and then stays on this phone."
            },
            modifier = modifier,
        ) {
            Spacer(Modifier.height(Gap.s6))
            PillButton(
                if (lang == "bn") "সাইটে দেখুন" else "See it on the site",
                onOpenSite,
                icon = "arrow",
            )
        }
        return
    }

    if (entries.isEmpty()) {
        /* A day nobody has logged is not a day of noughts. Nineteen
           zeroes with nineteen empty bars under them is a screen
           telling a reader at eight in the morning that they have
           failed at nineteen things. */
        InfoCard(
            title = if (lang == "bn") "আজ এখনো কিছু লেখা হয়নি" else "Nothing logged yet today",
            dek = if (lang == "bn") {
                "কিছু যোগ করলে এখানে দেখা যাবে কতটা কী পেলেন, আর দিনের কতটুকু খাবার " +
                    "থেকে সেটা হিসাব হলো।"
            } else {
                "Add something and this says how much of each you got, and how much " +
                    "of the day's food that figure was worked out from."
            },
            modifier = modifier,
        )
        return
    }

    val day = remember(entries, library) { totalFor(entries, library.macros) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s6)) {
        /* COVERAGE FIRST. It is the most important sentence on
           the page, and it goes above the figures rather than
           under each one because a reader who scrolls past it has
           already read the numbers. */
        Text(
            if (lang == "bn") {
                "আজকের খাবারের ${inScript(share(day.coverage), "bn")}% থেকে হিসাব করা।"
            } else {
                "Computed from ${share(day.coverage)}% of today's food."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )

        if (tooSparse(day)) {
            /* UNDER HALF, NOTHING IS DRAWN. The site's own rule
               and the one it calls the most important on the
               page: a confident number missing a third of the day
               is more dangerous than no number, so a phone that
               drew the figures anyway would be the more dangerous
               of the two. */
            InfoCard(
                title = if (lang == "bn") "পড়ার মতো যথেষ্ট নয়" else "Too sparse to read",
                dek = if (lang == "bn") {
                    "দিনের এক তৃতীয়াংশ বাদ দিয়ে দেওয়া আত্মবিশ্বাসী সংখ্যা কোনো সংখ্যা " +
                        "না থাকার চেয়ে বিপজ্জনক, তাই দিনের বেশি অংশে গঠন যুক্ত না হওয়া " +
                        "পর্যন্ত কিছুই আঁকা হয় না। নিজে লিখলে ফাঁকটা থেকে যায়: তালিকা " +
                        "থেকে বেছে নিলে সঙ্গে সংখ্যা আসে।"
                } else {
                    "A confident number that is missing a third of the day is more " +
                        "dangerous than no number, so nothing is drawn until more of " +
                        "the day has composition attached. A row picked from the " +
                        "library carries its numbers; a free estimate leaves the gap."
                },
            )
            return@Column
        }

        for (group in library.groups) {
            val rows = library.inGroup(group.id)
            if (rows.isEmpty()) continue
            SectionLabelRow(if (lang == "bn") group.bn else group.en)
            for (n in rows) {
                NutrientRow(n, day, lang)
            }
        }

        Spacer(Modifier.height(Gap.s3))
        Text(
            /* Section 31, in both languages, on every page that
               prints a figure about a body. */
            if (lang == "bn") {
                "এটা সাধারণ শিক্ষা, চিকিৎসা পরামর্শ নয়।"
            } else {
                "This is general education and not medical advice."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}

@Composable
private fun SectionLabelRow(text: String) {
    val c = LocalReiad.current
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = c.accent,
        modifier = Modifier.semantics { heading() },
    )
}

/** One nutrient: the figure, where it sits, and why it is here.

    The sentence and the reference are behind a press rather than
    always on, because nineteen paragraphs is a wall and the
    figure is what a reader came for. A `plate`, because it is
    read: nothing here is pressed to make something happen, and
    the disclosure is the row's own. */
@Composable
private fun NutrientRow(of: Nutrient, day: DayTotal, lang: String) {
    val c = LocalReiad.current
    var open by remember(of.key) { mutableStateOf(false) }
    /* The glasses are not in this app yet, so the water in the
       food is all this can honestly add up. Passing nought rather
       than leaving the argument out is the same statement, said
       where somebody will read it. */
    val reading = remember(of.key, day) { readingFor(of, day, drunkMl = 0.0) }
    val amount = reading.amount

    /* Where the figure sits in its own range, for the fill. A
       nutrient with no range gets no bar: carbohydrate and fat
       are chosen rather than prescribed, and a bar would tell a
       reader on keto they were failing at something they
       decided to do. */
    val ceiling = of.high ?: of.low
    val fraction = if (amount == null || ceiling == null || ceiling <= 0) null else {
        (amount / ceiling).toFloat().coerceIn(0f, 1f)
    }

    Plate(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(of.say(lang), style = MaterialTheme.typography.bodyMedium, color = c.ink)
                if (amount != null) {
                    Spacer(Modifier.height(Gap.s2))
                    Text(
                        /* THIS nutrient's own coverage, not the
                           day's. A row may carry sodium and
                           nothing else, so the day reads well
                           while four figures out of five are
                           drawn from a third of it. */
                        if (lang == "bn") {
                            "আজকের খাবারের ${inScript(share(reading.seen), "bn")}% থেকে"
                        } else {
                            "from ${share(reading.seen)}% of today's food"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
            Spacer(Modifier.width(Gap.s5))
            Text(
                figure(amount, of, reading.thin, lang),
                style = MaterialTheme.typography.titleSmall,
                color = if (amount == null) c.inkSoft else c.ink,
            )
            Spacer(Modifier.width(Gap.s4))
            Tap(
                onClick = { open = !open },
                label = of.say(lang) + (if (lang == "bn") ": কেন" else ": why"),
            ) {
                Icon(if (open) "chevron-up" else "chevron-down", size = 14.dp, tint = c.inkSoft)
            }
        }

        if (fraction != null) {
            Spacer(Modifier.height(Gap.s4))
            Groove(fraction)
        }
        val target = range(of, lang)
        if (target.isNotEmpty()) {
            Spacer(Modifier.height(Gap.s3))
            Text(target, style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
        }

        if (open) {
            Spacer(Modifier.height(Gap.s4))
            Text(of.why(lang), style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            Spacer(Modifier.height(Gap.s3))
            /* Whose figure the range is. A range with no
               population on it is a range a reader cannot argue
               with, and several of these differ between the UK,
               the WHO and the US by more than a rounding. */
            Text(of.reference(lang), style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
        }
    }
}

/* ---------- the words ---------- */

/** Rounded for reading rather than for arithmetic. A whole number
    above ten, one decimal below it: rounding alone turns 1.4 µg
    of B12 into 1 and a fifth of the range into nothing. */
private fun show(v: Double): String {
    if (v >= 10) return v.roundToInt().toString()
    val tenths = (v * 10).roundToInt()
    /* And no decimal place it has not earned. Kotlin prints a
       whole Double as "6.0" where the site prints 6, and a panel
       reading "প্রায় ৬.০ মিলিগ্রাম" is the same figure said
       worse. */
    return if (tenths % 10 == 0) (tenths / 10).toString() else (tenths / 10.0).toString()
}

private fun share(fraction: Double): String = (fraction * 100).roundToInt().toString()

/** "about 9 mg", "at least 9 mg", or "not known", which are three
    different sentences and only one of them is about the food.

    "AT LEAST" IS NOT A HEDGE. A figure drawn from a third of the
    day is a floor: the rest of the day can only have added to it,
    so saying "about" would be the one wrong word available. */
private fun figure(amount: Double?, of: Nutrient, thin: Boolean, lang: String): String {
    if (amount == null) return if (lang == "bn") "জানা নেই" else "not known"
    val n = inScript(show(amount), lang)
    val unit = of.unitSay(lang)
    return when {
        thin && lang == "bn" -> "কমপক্ষে $n $unit"
        thin -> "at least $n $unit"
        lang == "bn" -> "প্রায় $n $unit"
        else -> "about $n $unit"
    }
}

/** What to aim for, said once. Four shapes, because a nutrient
    can have a floor, a ceiling, both, or neither, and none of the
    four is the same sentence. The fourth draws nothing: a reader
    told to aim at nothing in particular has been told nothing. */
private fun range(of: Nutrient, lang: String): String {
    val low = of.low
    val high = of.high
    val unit = of.unitSay(lang)
    val n = { v: Double -> inScript(show(v), lang) }
    return when {
        low != null && high != null ->
            if (lang == "bn") "লক্ষ্য ${n(low)} থেকে ${n(high)} $unit"
            else "aim for ${n(low)} to ${n(high)} $unit"
        low != null ->
            if (lang == "bn") "দিনে অন্তত ${n(low)} $unit" else "at least ${n(low)} $unit a day"
        high != null ->
            if (lang == "bn") "${n(high)} $unit এর নিচে রাখুন" else "keep it under ${n(high)} $unit"
        else -> ""
    }
}
