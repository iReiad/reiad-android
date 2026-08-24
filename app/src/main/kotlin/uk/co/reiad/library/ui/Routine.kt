package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.routine.Mood
import uk.co.reiad.library.core.routine.Plant
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.routine.Cell
import uk.co.reiad.library.core.routine.Echo
import uk.co.reiad.library.core.routine.Entry
import uk.co.reiad.library.core.routine.MOODS
import uk.co.reiad.library.core.routine.Momentum
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.Runs
import uk.co.reiad.library.core.routine.Season
import uk.co.reiad.library.core.routine.Share
import uk.co.reiad.library.core.routine.Task
import uk.co.reiad.library.core.routine.TaskTally
import uk.co.reiad.library.core.routine.Weekday
import uk.co.reiad.library.core.routine.balance
import uk.co.reiad.library.core.routine.bandTasks
import uk.co.reiad.library.core.routine.done
import uk.co.reiad.library.core.routine.hoursDone
import uk.co.reiad.library.core.routine.moodRibbon
import uk.co.reiad.library.core.routine.weekdays
import uk.co.reiad.library.core.routine.written
import kotlin.math.roundToInt

/* ============================================================
   The routine, on a phone.

   ---- ROUTINE.md §0, which this screen exists to honour ----

   **Nothing here can punish anybody.** No streak that breaks, no
   number that resets at midnight, no red, no target, no "best
   day" (which would name a worst one), and no percentage on a day
   with nothing on it.

   That last is the one a port gets wrong. `done()` answers NULL
   for an empty day and the temptation everywhere is to render
   that as 0%. A person opening yesterday must be told the day is
   still empty, not that they scored nothing: the number describes
   what happened, and on a day where nothing happened there is
   nothing to describe.

   ---- and unticking deletes ----

   A tick removes the key rather than writing a zero. Every piece
   of arithmetic in `core/routine` reads that distinction, and a
   screen that wrote zeroes would poison it from the outside.
   ============================================================ */

/** Everything the routine screens draw. */
data class RoutineState(
    val routineId: String? = null,
    val shape: RoutineShape = RoutineShape(),
    val today: String = "",
    val entry: Entry? = null,
    val entries: List<Entry> = emptyList(),
    val heat: List<Cell> = emptyList(),
    val consistency: List<TaskTally> = emptyList(),
    val neverMarked: List<Task> = emptyList(),
    val momentum: Momentum? = null,
    val runs: Runs? = null,
    val echo: Echo? = null,
    val season: Season? = null,
    val greeting: String = "",
    val flock: Int = 0,
    /** The four moods as the site last sent them, so a fifth
        one reaches a phone with no release. */
    val moods: List<Mood> = MOODS,
    /** What has been planted, ever. Never shrinks, for the
        same reason the flock does not. */
    val garden: List<Plant> = emptyList(),
    val loading: Boolean = true,
    /** Signed out, which is not an error: a routine belongs to an
        account and there is nothing here without one. */
    val signedOut: Boolean = false,
    val saving: Boolean = false,
)

@Composable
fun RoutineScreen(
    state: RoutineState,
    onMark: (task: String, value: Double) -> Unit,
    onMood: (String?) -> Unit,
    onNote: (String) -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val c = LocalReiad.current
    val fraction = done(state.shape, state.entry)

    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Gap.s7),
    ) {
        item {
            Column(Modifier.padding(top = Gap.s6)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.greeting,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.accent,
                        modifier = Modifier.weight(1f),
                    )
                    /* The season, because Bangladesh has six and
                       almost no software knows it. Its colour is
                       DATA and travels with it. */
                    state.season?.let { s ->
                        Text(
                            s.bn,
                            style = MaterialTheme.typography.labelSmall,
                            color = hex(s.colour) ?: c.inkSoft,
                        )
                    }
                }
                Spacer(Modifier.height(Gap.s5))
                Text(
                    todayText(state.today),
                    style = headlineStyle(todayText(state.today)),
                    color = c.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        if (state.signedOut) {
            item { NeedsAccount(onOpenSite) }
            return@LazyColumn
        }
        if (state.loading) {
            item { Skeleton(lines = 3, label = "Reading your routine") }
            return@LazyColumn
        }
        if (state.routineId == null) {
            item { NoRoutine(onOpenSite) }
            return@LazyColumn
        }

        /* ---------- how the day stands ---------- */
        item { Standing(state, fraction) }

        /* ---------- a year ago today ---------- */
        state.echo?.let { item { EchoCard(it) } }

        /* ---------- the day itself ---------- */
        for (band in state.shape.bands.sortedBy { it.order }) {
            val tasks = bandTasks(state.shape, band.id)
            if (tasks.isEmpty()) continue
            item(key = "band-${band.id}") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(Corner.pill))
                                .background(hex(band.colour) ?: c.accent),
                        )
                        Spacer(Modifier.width(Gap.s4))
                        Text(
                            band.bn,
                            style = if (isBangla(band.bn)) BanglaTitle
                            else MaterialTheme.typography.titleSmall,
                            color = c.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.height(Gap.s4))
                    Pane {
                        for (task in tasks) {
                            TaskRow(task, state.entry?.marks?.get(task.id) ?: 0.0, onMark)
                        }
                    }
                }
            }
        }

        /* ---------- how it felt, and what was worth keeping ---------- */
        item { MoodRow(state.moods, state.entry?.mood, onMood, state.entries, state.today) }
        item { NoteBox(state.today, state.entry?.note.orEmpty(), onNote) }

        /* ---------- the jar ---------- */
        /* Everything ever written, minus today's line, which is
           still in the box above it. */
        val jar = written(state.entries).filter { it.date != state.today }
        if (jar.isNotEmpty()) item { JarCard(jar, state.moods) }

        /* ---------- the year ---------- */
        if (state.heat.isNotEmpty()) item { Year(state.heat) }
        val week = weekdays(state.shape, state.entries, state.today)
        if (week.any { it.marked > 0 }) item { WeekShape(week) }

        /* ---------- the things that only ever grow ---------- */
        if (state.flock > 0 || state.garden.isNotEmpty()) {
            item { Grown(state.flock, state.garden) }
        }
        if (state.momentum != null || state.runs != null) {
            item { Carrying(state.momentum, state.runs) }
        }

        /* ---------- what is real, and what was aspirational ---------- */
        val shares = balance(state.shape, state.entries)
        if (shares.isNotEmpty()) item { BalanceCard(shares) }
        if (state.consistency.isNotEmpty()) item { Consistency(state.consistency) }
        if (state.neverMarked.isNotEmpty()) item { NeverMarked(state.neverMarked, onOpenSite) }
    }
}

/* ---------- the birds and the garden ---------- */

/**
 * What a routine has grown, and NOTHING HERE CAN SHRINK.
 *
 * `ROUTINE.md` §0 and the whole argument for this tool existing
 * rather than another habit tracker: the flock is how many times
 * the birds have been fed, ever, and the garden is what has been
 * planted, ever. A person who stops for a fortnight and comes
 * back finds both exactly as they left them.
 *
 * That is why there is no percentage on this card and no "this
 * week". A streak that can break is a thing that punishes an
 * illness, and this tool refuses to.
 */
@Composable
private fun Grown(flock: Int, garden: List<Plant>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "এতদিনে যা জমেছে",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "What has grown. None of this can go down.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )

        if (flock > 0) {
            Spacer(Modifier.height(Gap.s6))
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* One bird per threshold reached, not one per
                   feeding: the flock grows in visible steps and
                   then stops, rather than becoming a crowd. */
                repeat(flock) { i ->
                    Icon(
                        "bird",
                        Modifier.padding(end = Gap.s3),
                        size = (17 + (i % 3) * 2).dp,
                        tint = c.accent,
                    )
                }
            }
        }

        if (garden.isNotEmpty()) {
            Spacer(Modifier.height(Gap.s6))
            Row(
                rememberScrollState().let { slide ->
                    Modifier.fadesAtTheEnd(slide, LocalReiad.current.paper)
                        .horizontalScroll(slide)
                },
                horizontalArrangement = Arrangement.spacedBy(Gap.s5),
            ) {
                for (plant in garden) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(Corner.pill))
                            .material(Kind.CHIP, c, Corner.pill, ground = c.accent.copy(alpha = 0.10f))
                            .padding(horizontal = Gap.s6, vertical = Gap.s4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon("seed", size = 14.dp, tint = c.accent)
                        Spacer(Modifier.width(Gap.s4))
                        Text(
                            plant.bn,
                            style = BanglaBody.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            ),
                            color = c.accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/* ---------- what is being carried ---------- */

/**
 * The mean over the days that were MARKED, and the longest run.
 *
 * Marked days rather than calendar days, which is the one line
 * that separates this from a chart of a life, scored: a quiet
 * fortnight makes a smaller sample rather than a falling line.
 * Said out loud on the card, because a mean that silently
 * excluded days would be worse than one that fell.
 *
 * `best` is shown beside `now` and never as a target: the site's
 * rule is that nothing here names a next threshold.
 */
@Composable
private fun Carrying(momentum: Momentum?, runs: Runs?) {
    val c = LocalReiad.current
    Pane {
        Text(
            "কেমন চলছে",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s5))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s7)) {
            if (momentum != null && momentum.marked > 0) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${(momentum.now * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.accent,
                    )
                    Text(
                        "over ${momentum.marked} day${if (momentum.marked == 1) "" else "s"} you marked",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                    )
                }
            }
            if (runs != null && runs.best > 0) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${runs.now} / ${runs.best}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.accent,
                    )
                    Text(
                        "days running, and the longest",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/* ---------- how the day stands ---------- */

@Composable
private fun Standing(state: RoutineState, fraction: Double?) {
    val c = LocalReiad.current
    Pane {
        if (fraction == null) {
            /* The sentence, never a nought. This is the line the
               whole tool turns on. */
            Text(
                "আজকের দিনটা এখনো খালি",
                style = BanglaTitle,
                color = c.ink,
            )
            Spacer(Modifier.height(Gap.s2))
            Text(
                "Today is still empty. Nothing is behind.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSoft,
            )
        } else {
            val filled by animateFloatAsState(
                fraction.toFloat().coerceIn(0f, 1f),
                tween(Motion.SLOW_MS),
                label = "day",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = c.accent,
                )
                Spacer(Modifier.width(Gap.s5))
                val (did, planned) = hoursDone(state.shape, state.entry)
                Text(
                    "${trim(did)} of ${trim(planned)} hours",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
            Spacer(Modifier.height(Gap.s4))
            Groove(filled, height = 8.dp)
        }

        /* Two facts, and neither is a streak. `best` standing
           above `now` is the ordinary case rather than a fall
           from grace, and nothing is lost by breaking either. */
        state.runs?.let { r ->
            if (r.best > 0) {
                Spacer(Modifier.height(Gap.s5))
                Text(
                    "Marked ${r.now} day${if (r.now == 1) "" else "s"} in a row, "
                        + "and ${r.best} at the most.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
        }
        state.momentum?.let { m ->
            if (m.marked > 0) {
                Spacer(Modifier.height(Gap.s2))
                Text(
                    "Over ${m.days} days you marked ${m.marked} of them, averaging "
                        + "${(m.now * 100).roundToInt()}%. "
                        + "The ${m.days} before that: ${(m.before * 100).roundToInt()}%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
        }
    }
}

/* ---------- one task ---------- */

/** A tick, a half and nothing, cycled by pressing.

    Three states rather than two because a half is a real answer:
    somebody who read for twenty minutes of an hour did not do
    nothing and did not do it. Pressing goes none, whole, half,
    none, so the common answer is one press. */
@Composable
private fun TaskRow(task: Task, mark: Double, onMark: (String, Double) -> Unit) {
    val c = LocalReiad.current
    val next = when {
        mark <= 0 -> 1.0
        mark >= 1 -> 0.5
        else -> 0.0
    }
    val label = if (isBangla(task.bn)) task.bn else task.en

    Rung(
        Modifier
            .clickable(role = Role.Checkbox) { onMark(task.id, next) }
            .semantics {
                stateDescription = when {
                    mark >= 1 -> "done"
                    mark > 0 -> "half done"
                    else -> "not marked"
                }
            },
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(Corner.xs))
                .background(
                    when {
                        mark >= 1 -> c.accent
                        /* A half is the accent at half strength
                           rather than a different colour: it is
                           the same answer, less of it. */
                        mark > 0 -> c.accent.copy(alpha = 0.45f)
                        else -> c.paperSunk
                    },
                ),
        )
        Spacer(Modifier.width(Gap.s5))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (isBangla(label)) BanglaBody else MaterialTheme.typography.bodyMedium,
                color = c.ink,
            )
            /* Leisure says so, quietly. A reader should be able to
               see which parts of their day cannot fail. */
            if (!task.counts) {
                Text(
                    "just for you",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
            }
        }
        task.hours?.let {
            Text(
                "${trim(it)}h",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
    }
}

/* ---------- how it felt ---------- */

/** The four moods, and there are deliberately no more.

    NONE OF THEM IS BAD. Heavy is the honest bottom of the scale
    and it is a description rather than a failure, which is the
    difference between this and every tracker that offers a
    frowning face. */
@Composable
private fun MoodRow(
    moods: List<Mood>,
    chosen: String?,
    onMood: (String?) -> Unit,
    entries: List<Entry>,
    today: String,
) {
    val c = LocalReiad.current
    Pane {
        Text(
            "আজ কেমন লাগল",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s4)) {
            for (mood in moods) {
                val on = chosen == mood.id
                Box(
                    /* Pressing the chosen one again clears it. A
                       mood nobody chose is a real answer and has
                       to stay reachable. */
                    Modifier.clickable { onMood(if (on) null else mood.id) },
                ) {
                    Chip(mood.bn, tone = if (on) hex(mood.colour) ?: c.accent else c.inkSoft)
                }
            }
        }

        /* The last four weeks of answers, oldest to the left, in
           the moods' own colours. A day nobody answered is paper:
           it is not a hole and none of the four is a verdict, so
           this can only ever read as weather. */
        val ribbon = remember(entries, today) { moodRibbon(entries, today, 28) }
        if (ribbon.any { it.mood != null }) {
            Spacer(Modifier.height(Gap.s6))
            Text(
                "How the last four weeks felt.",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
            Spacer(Modifier.height(Gap.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (day in ribbon) {
                    val tone = moods.firstOrNull { it.id == day.mood }?.let { hex(it.colour) }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tone?.copy(alpha = 0.85f) ?: c.paperSunk),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteBox(today: String, note: String, onNote: (String) -> Unit) {
    val c = LocalReiad.current
    /* Keyed by the DAY, so moving between days replaces the box's
       contents rather than carrying yesterday's words forward. */
    var typed by remember(today) { mutableStateOf(note) }
    LaunchedEffect(today, note) { if (note != typed && typed.isEmpty()) typed = note }

    Pane {
        Text(
            "আজকের একটা কথা",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s2))
        Text(
            "One line. A year from now this is the part worth having.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s4))
        Field(
            value = typed,
            onValue = { typed = it; onNote(it) },
            description = "Today's one line",
            hint = "One line about today.",
            size = FieldSize.AREA,
        )
    }
}

/* ---------- a year ago today ---------- */

@Composable
private fun EchoCard(e: Echo) {
    val c = LocalReiad.current
    Plate {
        Text(
            e.bn,
            style = MaterialTheme.typography.labelSmall,
            color = c.accent,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            e.entry.note.orEmpty(),
            style = bodyStyle(e.entry.note.orEmpty()),
            color = c.ink,
        )
    }
}

/* ---------- the jar ---------- */

/**
 * Every line ever written, newest first: the jar.
 *
 * `ROADMAP.md` names it beside the garden and for the same
 * reason: it only fills. Three slips are shown and the rest are
 * counted rather than listed, because the point of a jar is
 * knowing it is there, not reading it end to end on a Tuesday.
 */
@Composable
private fun JarCard(jar: List<Entry>, moods: List<Mood>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "লেখার বয়াম",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Every line you kept. The jar only fills.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        for (slip in jar.take(3)) {
            Spacer(Modifier.height(Gap.s5))
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* The mood beside the date where one was chosen,
                   as its own colour: the two answers were given
                   together and read back together. */
                moods.firstOrNull { it.id == slip.mood }?.let { m ->
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(Corner.pill))
                            .background(hex(m.colour) ?: c.inkSoft),
                    )
                    Spacer(Modifier.width(Gap.s3))
                }
                Text(
                    slipDate(slip.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
            }
            Spacer(Modifier.height(Gap.s2))
            Text(
                slip.note.orEmpty(),
                style = bodyStyle(slip.note.orEmpty()),
                color = c.ink,
            )
        }
        if (jar.size > 3) {
            Spacer(Modifier.height(Gap.s5))
            Text(
                "And ${jar.size - 3} more, further back.",
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
            )
        }
    }
}

/* ---------- the shape of a week ---------- */

/**
 * Seven bars, Sunday first, and NOTHING HERE NAMES A BEST DAY.
 *
 * All seven are the same colour on purpose: `weekdays()` says a
 * best day names a worst one, and a worst one is a thing to feel
 * behind on every time it comes round. So this is a shape to
 * recognise, never a ranking to fix.
 */
@Composable
private fun WeekShape(week: List<Weekday>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "সপ্তাহের সাত দিন",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "The shape of a week, over twelve of them.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s5))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s4)) {
            for (day in week) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(Corner.xs))
                            .background(c.paperSunk),
                    ) {
                        val rate = day.rate.toFloat().coerceIn(0f, 1f)
                        if (rate > 0f) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height((44 * rate).dp)
                                    .background(c.accent.copy(alpha = 0.8f)),
                            )
                        }
                    }
                    Spacer(Modifier.height(Gap.s2))
                    Text(
                        day.en,
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/* ---------- how a day divides ---------- */

/**
 * One bar, split by band, in the bands' own colours.
 *
 * `balance()` reads the whole history, so this is what a typical
 * day has actually been made of rather than what this week went
 * like: the slow answer to "where does the time go". No share is
 * called too big, because no band is the right size.
 */
@Composable
private fun BalanceCard(shares: List<Share>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "দিন যেভাবে ভাগ হয়",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s5))
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(Corner.pill)),
        ) {
            for (share in shares) {
                Box(
                    Modifier
                        .weight(share.share.toFloat().coerceAtLeast(0.01f))
                        .height(10.dp)
                        .background(hex(share.band.colour) ?: c.accent),
                )
            }
        }
        Spacer(Modifier.height(Gap.s5))
        for (share in shares) {
            Row(
                Modifier.padding(vertical = Gap.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(Corner.pill))
                        .background(hex(share.band.colour) ?: c.accent),
                )
                Spacer(Modifier.width(Gap.s4))
                Text(
                    share.band.bn,
                    style = if (isBangla(share.band.bn)) BanglaBody
                    else MaterialTheme.typography.bodySmall,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(share.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
            }
        }
    }
}

/* ---------- the year ---------- */

/** Twelve weeks, one column per week.

    An unmarked day is PAPER rather than an empty slot: it is not
    a hole and must not read as one. */
@Composable
private fun Year(cells: List<Cell>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "গত বারো সপ্তাহ",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s5))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (week in cells.chunked(7)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (cell in week) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when (val f = cell.fraction) {
                                        null -> c.paperSunk
                                        else -> c.accent.copy(
                                            alpha = (0.18f + f.toFloat() * 0.82f),
                                        )
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/* ---------- what is real ---------- */

@Composable
private fun Consistency(rows: List<TaskTally>) {
    val c = LocalReiad.current
    Pane {
        Text(
            "গত চার সপ্তাহে",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s4))
        for (row in rows) {
            Row(Modifier.padding(vertical = Gap.s3), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val label = if (isBangla(row.task.bn)) row.task.bn else row.task.en
                    Text(
                        label,
                        style = if (isBangla(label)) BanglaBody
                        else MaterialTheme.typography.bodySmall,
                        color = c.ink,
                    )
                    Spacer(Modifier.height(Gap.s2))
                    Groove(row.marked.toFloat() / row.of, height = 4.dp)
                }
                Spacer(Modifier.width(Gap.s5))
                Text(
                    "${row.marked}/${row.of}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
            }
        }
    }
}

/**
 * The tasks never marked, ever.
 *
 * THE MOST IMPORTANT PANEL IN THE TOOL. A routine full of
 * aspirational tasks is what makes a tracker feel bad, and the
 * fix is taking them out rather than trying harder. So it lists
 * them and says nothing else: no nagging, no count, no suggestion
 * that they ought to have been done.
 */
@Composable
private fun NeverMarked(tasks: List<Task>, onOpenSite: () -> Unit) {
    val c = LocalReiad.current
    Pane {
        Text(
            "এগুলো একবারও হয়নি",
            style = BanglaTitle,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Gap.s2))
        Text(
            "Taking one off the list is a fine answer.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s4))
        for (task in tasks) {
            val label = if (isBangla(task.bn)) task.bn else task.en
            Text(
                label,
                style = if (isBangla(label)) BanglaBody else MaterialTheme.typography.bodyMedium,
                color = c.ink,
                modifier = Modifier.padding(vertical = Gap.s2),
            )
        }
        Spacer(Modifier.height(Gap.s4))
        PillButton("Edit the routine on the site", onOpenSite, kind = ButtonKind.SOFT)
    }
}

/* ---------- the two ways there is nothing to draw ---------- */

@Composable
private fun NeedsAccount(onOpenSite: () -> Unit) {
    val c = LocalReiad.current
    Pane {
        Text(
            "A routine belongs to an account",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            "Sign in on the Account tab and your days follow you between devices. "
                + "Nothing here is stored on this phone alone.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
    }
}

@Composable
private fun NoRoutine(onOpenSite: () -> Unit) {
    val c = LocalReiad.current
    Pane {
        Text(
            "No routine yet",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            "Build one on the site: pick a template or start from nothing, and it will "
                + "be here the moment it exists. A day is six or seven things, not thirty.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s5))
        PillButton("Open on the site", onOpenSite, kind = ButtonKind.SOFT)
    }
}

/* ---------- printing ---------- */

/** `#6E52A8` as a colour, or null for anything that is not one.

    A band's colour is DATA a reader can change, so this is
    reading a value from a database rather than a token from the
    stylesheet, and it has to survive a typo. */
internal fun hex(value: String): Color? {
    val body = value.removePrefix("#")
    if (body.length != 6) return null
    val n = body.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or n)
}

/** `31 March 2025`: a jar slip can be years old, so unlike the
    heading above today, it keeps its year. */
private fun slipDate(iso: String): String {
    val day = todayText(iso)
    return if (day.isEmpty() || iso.length < 4) day else "$day ${iso.take(4)}"
}

/** `31 March`, from an ISO day, with no locale involved. */
private fun todayText(iso: String): String {
    if (iso.length < 10) return ""
    val months = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    val m = iso.substring(5, 7).toIntOrNull() ?: return ""
    if (m !in 1..12) return ""
    return "${iso.substring(8, 10).trimStart('0')} ${months[m - 1]}"
}

/** `1.5` and `7`, never `7.0`. */
private fun trim(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
