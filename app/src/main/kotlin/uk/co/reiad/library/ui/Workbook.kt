package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.reiad.library.core.Book
import uk.co.reiad.library.core.BookDay
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.bengaliNumber
import uk.co.reiad.library.core.dayId
import uk.co.reiad.library.core.writeSlot

/* ============================================================
   A practice book: one page, returned to thirty times.

   A book is not a ladder. The shape of the page never changes,
   only what is poured into it: a pattern, lines to read aloud,
   prompts to translate, and one piece of free writing. So this is
   one screen with a day walker on it rather than thirty screens.

   ---- what a learner types is theirs ----

   Every box saves to `deutsch-schrift` or `english-write` and
   NEITHER key ever leaves the device. `ProgressKeys.write` says
   why and `WritingStaysHereTest` asserts it: a tick is one bit
   saying a lesson was read, and this is somebody writing about
   their own life in a language they are learning badly. The whole
   point of a practice book is that being bad at it is safe.

   ---- and the answers are not here until asked for ----

   The book arrives with every answer stripped, because the
   endpoint takes them out. Show fetches one day's key. That is
   the same guarantee the web page has, and it is why the reveal
   is a network call rather than a boolean.

   ---- the tick, and whose shape it is ----

   `stufe-1/tag-3` in German and `term-1/day-3` in English. Both
   are in real browsers, and the site's shared engine built the
   German shape for both once: an English day could be ticked and
   came back unticked, because `toggleDay` wrote it correctly and
   the tracker looked under a name nothing had ever used. `dayId`
   takes the school for exactly that reason.
   ============================================================ */

@Composable
fun WorkbookScreen(
    stage: String,
    stageName: String,
    school: School,
    book: Book?,
    /** The book could not be read at all, as opposed to not
        having arrived yet. */
    failed: Boolean,
    onOpenOnSite: () -> Unit,
    days: Set<String>,
    written: Map<String, String>,
    answers: Map<Int, List<String>>,
    bottomPadding: Dp,
    onWrite: (String, String) -> Unit,
    onTickDay: (String) -> Unit,
    onReveal: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalReiad.current
    /* Where the reader was left, which is the furthest day they
       have ticked plus one, capped at the book's length. Not day
       one every time: a book opened on the first page after three
       weeks is a book that has forgotten them. */
    var at by remember(book) {
        mutableStateOf(
            book?.let { b ->
                val reached = b.days.count { dayId(school, stage, it.n) in days }
                (reached + 1).coerceIn(1, b.days.size)
            } ?: 1,
        )
    }
    val day = book?.days?.firstOrNull { it.n == at }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
    ) {
        item("head") {
            Crumb(stageName, onBack)
            Spacer(Modifier.height(Gap.s7))
            if (book == null) {
                if (failed) {
                    /* Said out loud, with a way through. A book
                       that will not open is not a spinner: this
                       is the one screen in the app whose data
                       lives at an address the site added for it,
                       so an older deployment is a real reason for
                       it to be missing and the reader should not
                       have to guess. */
                    InfoCard(
                        title = "The book would not open",
                        dek = "Either there is no connection and no saved copy, or the " +
                            "site has not been updated for this yet. It opens on the " +
                            "site either way.",
                    )
                    Spacer(Modifier.height(Gap.s7))
                    PillButton("Open on the site", onOpenOnSite, kind = ButtonKind.SOFT)
                } else {
                    Skeleton(lines = 4, label = "Opening the book")
                }
                return@item
            }
            PageHead(
                title = book.lede.bn,
                eyebrow = stageName,
                lede = book.lede.target,
            )
            Spacer(Modifier.height(Gap.s7))

            DayWalker(book, school, stage, days, at) { at = it }
            Spacer(Modifier.height(Gap.s8))
        }

        if (day != null) {
            item("day-${day.n}") {
                DayPage(
                    day = day,
                    school = school,
                    stage = stage,
                    foot = book!!.foot,
                    ticked = dayId(school, stage, day.n) in days,
                    written = written,
                    answers = answers[day.n],
                    onWrite = onWrite,
                    onTick = { onTickDay(dayId(school, stage, day.n)) },
                    onReveal = { onReveal(day.n) },
                )
            }
        }
    }
}

/** The thirty days, as a row of marks.

    Small enough that the whole book fits on one line of a phone,
    which is the point: a learner should be able to see the shape
    of what they have done without scrolling a list of thirty
    rows. A ticked day is filled, the day being read is ringed,
    and everything else is a channel waiting. */
@Composable
private fun DayWalker(
    book: Book,
    school: School,
    stage: String,
    days: Set<String>,
    at: Int,
    onGo: (Int) -> Unit,
) {
    val c = LocalReiad.current
    Column {
        Row(
            rememberScrollState().let { slide ->
                Modifier.fadesAtTheEnd(slide, LocalReiad.current.paper).horizontalScroll(slide)
            },
            horizontalArrangement = Arrangement.spacedBy(Gap.s3),
        ) {
            for (day in book.days) {
                val done = dayId(school, stage, day.n) in days
                val here = day.n == at
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Corner.pill))
                        .material(
                            kind = Kind.CHIP,
                            colours = c,
                            corner = Corner.pill,
                            ground = when {
                                done -> c.accent
                                here -> c.accent.copy(alpha = 0.18f)
                                else -> c.paperSunk
                            },
                        )
                        .clickable(role = Role.Button) { onGo(day.n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        bengaliNumber(day.n),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = Faces.bengali,
                        ),
                        color = if (done) c.paper else c.ink,
                    )
                }
            }
        }
        Spacer(Modifier.height(Gap.s4))
        val done = book.days.count { dayId(school, stage, it.n) in days }
        Text(
            "${bengaliNumber(done)} / ${bengaliNumber(book.days.size)}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = Faces.bengali),
            color = c.accent,
        )
    }
}

@Composable
private fun DayPage(
    day: BookDay,
    school: School,
    stage: String,
    foot: String,
    ticked: Boolean,
    written: Map<String, String>,
    answers: List<String>?,
    onWrite: (String, String) -> Unit,
    onTick: () -> Unit,
    onReveal: () -> Unit,
) {
    val c = LocalReiad.current
    Column {
        Text(day.bn, style = BanglaHeading, color = c.ink)
        Text(day.target, style = MaterialTheme.typography.bodyMedium, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s8))

        /* The pattern: one shape, why it is that shape, examples,
           and the one thing that catches people out. */
        Pane {
            Text(day.pattern.shape, style = MaterialTheme.typography.titleMedium, color = c.accent)
            Spacer(Modifier.height(Gap.s4))
            Text(day.pattern.why, style = BanglaBody, color = c.ink)
            if (day.pattern.examples.isNotBlank()) {
                Spacer(Modifier.height(Gap.s5))
                Text(
                    day.pattern.examples,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                )
            }
            if (day.pattern.tip.isNotBlank()) {
                Spacer(Modifier.height(Gap.s5))
                Text(day.pattern.tip, style = BanglaBody, color = c.accent)
            }
        }
        Spacer(Modifier.height(Gap.s8))

        /* Lines to read aloud. Nothing to type: this part of the
           page is the learner's mouth, not their hands. */
        Text("জোরে পড়ুন", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s4))
        for (line in day.watch) {
            Plate(Modifier.fillMaxWidth().padding(bottom = Gap.s4)) {
                Text(line.target, style = MaterialTheme.typography.bodyLarge, color = c.ink)
                Text(line.bn, style = BanglaBody, color = c.inkSoft)
            }
        }
        Spacer(Modifier.height(Gap.s8))

        /* Prompts. Speak first, write second, and the answer is
           not on the device until Show is pressed. */
        Text("বলুন, তারপর লিখুন", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s4))
        day.say.forEachIndexed { index, prompt ->
            val slot = writeSlot(day.n, "say-$index")
            Column(Modifier.padding(bottom = Gap.s6)) {
                Text(prompt.q, style = BanglaBody, color = c.ink)
                Spacer(Modifier.height(Gap.s3))
                WritingBox(
                    value = written[slot].orEmpty(),
                    onChange = { onWrite(slot, it) },
                    lines = 1,
                )
                answers?.getOrNull(index)?.let { answer ->
                    Spacer(Modifier.height(Gap.s3))
                    Text(
                        answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.accent,
                    )
                }
            }
        }

        if (answers == null) {
            PillButton("উত্তর দেখুন", onReveal, kind = ButtonKind.SOFT)
        }
        Spacer(Modifier.height(Gap.s8))

        /* The free writing. One box, several lines, and the one
           part of the page that is nobody's business but the
           learner's. */
        Text(day.heart.bn, style = BanglaBody, color = c.ink)
        Text(day.heart.target, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s4))
        WritingBox(
            value = written[writeSlot(day.n, "heart")].orEmpty(),
            onChange = { onWrite(writeSlot(day.n, "heart"), it) },
            lines = 5,
        )
        Spacer(Modifier.height(Gap.s9))

        /* The day's tick, and the sentence under it that grows
           with the level: Stufe 1 asks whether yesterday's page
           was read first, Stufe 3 asks for a whole story. */
        PillButton(
            if (ticked) "আজকের পাতা হয়েছে ✓" else "আজকের পাতা হয়েছে",
            onTick,
            kind = ButtonKind.SOFT,
            wide = true,
            pressed = ticked,
        )
        Spacer(Modifier.height(Gap.s4))
        Text(foot, style = BanglaBody.copy(
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
        ), color = c.inkSoft)
    }
}

/** A box a learner types into.

    Saved on a debounce rather than on every keystroke. Eight
    sentences is a hundred input events, and a write per event is
    a hundred disk writes on a phone that may be cheap; the site's
    own engine debounces for the same reason. Half a second is
    long enough to coalesce a burst of typing and short enough
    that closing the app mid-sentence loses nothing a reader would
    notice. */
@Composable
private fun WritingBox(value: String, onChange: (String) -> Unit, lines: Int) {
    val c = LocalReiad.current
    var text by remember(value.hashCode()) { mutableStateOf(value) }

    LaunchedEffect(text) {
        if (text == value) return@LaunchedEffect
        delay(500)
        onChange(text)
    }

    Field(
        value = text,
        onValue = { text = it },
        description = "What you write for this exercise",
        /* A practice book asks for anything from one word to a
           paragraph, and the exercise says which: `lines` is the
           school's own number and this is the only box here whose
           height is not one of the two. */
        size = if (lines > 1) FieldSize.AREA else FieldSize.LINE,
        modifier = Modifier.heightIn(min = (lines * 26 + 20).dp),
        textStyle = BanglaBody,
    )
}
