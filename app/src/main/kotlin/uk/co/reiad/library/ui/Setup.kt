package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Choice
import uk.co.reiad.library.core.stock.inScript
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Profile
import uk.co.reiad.library.core.Target

/* ============================================================
   The three questions an account asks, and the form that is
   also the settings page.

   ---- setup asks, settings tells ----

   ONE form, two framings, decided by whether the profile carries
   a `setup_at`. Two forms would be two save handlers and two
   places for a label to drift, which is the site's own argument
   for this shape and it holds here.

   "Not now" is a real answer and is recorded as one: without it
   the screen would ask again on every visit, which is how a
   polite question becomes nagging.

   ---- and the vocabularies are NOT spelled here ----

   `paces` arrives in the manifest out of `shared/profile.ts`,
   which is also what the CHECK constraint on `profiles.pace` is
   held to. A fourth pace added on the site reaches this screen
   with no app release, and one spelled here would be a 400 on
   the whole patch the day the two disagreed.
   ============================================================ */

/** A question: what is being asked, why it is being asked, and
    the answers.

    The "why" is not decoration. Every one of these is optional
    and none of them is obvious, so a reader who is not told what
    a box does is a reader deciding whether to trust it. */
@Composable
fun Question(ask: String, why: String, content: @Composable () -> Unit) {
    val c = LocalReiad.current
    Column(Modifier.fillMaxWidth()) {
        Text(ask, style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s3))
        Text(why, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
        Spacer(Modifier.height(Gap.s5))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s4)) {
            content()
        }
    }
}

/** One answer, with its mark and its note.

    `Role.RadioButton` or `Role.Checkbox` rather than Button: a
    screen reader announces "selected" or "not selected" for
    those and nothing at all for a button, and this row is the
    only thing saying which answer is on. */
@Composable
fun ChoiceRow(
    label: String,
    note: String,
    on: Boolean,
    role: Role,
    onPick: () -> Unit,
) {
    val c = LocalReiad.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Gap.tap)
            .clip(RoundedCornerShape(Corner.card))
            .material(
                kind = Kind.CONTROL,
                colours = c,
                corner = Corner.card,
                ground = if (on) c.accent.copy(alpha = 0.14f) else null,
            )
            .clickable(role = role, onClick = onPick)
            .padding(horizontal = Gap.s6, vertical = Gap.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /* A MARK, not a surface: `NOT_GLASS` again. A bevel on a
           nine pixel dot is detail nobody can resolve. */
        Box(
            Modifier
                .width(18.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(if (role == Role.RadioButton) Corner.pill else 5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon("check", size = 16.dp, tint = c.accent)
        }
        Spacer(Modifier.width(Gap.s6))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) c.ink else c.inkSoft,
            )
            if (note.isNotBlank()) {
                Text(note, style = MaterialTheme.typography.bodySmall, color = c.inkSoft)
            }
        }
    }
}

/** What the form is holding while somebody fills it in.

    Held by the caller rather than inside this composable, because
    a save is the caller's to make and a form that owned its own
    answers would have to hand them back through a callback that
    is the same thing with more steps. */
data class SetupState(
    val name: String = "",
    val following: Set<String> = emptySet(),
    val pace: String = "",
    /** Whether they have answered, EVER. Held apart from
        `Profile.setupAt` so that pressing Save or Not now
        reframes the form at once rather than waiting for a
        re-read. */
    val asked: Boolean = false,
    val busy: Boolean = false,
    /** What the last save said, and whether it went well. */
    val note: String? = null,
    val wrong: Boolean = false,
)

/** Seed the form from the account's answers, and never over
    anything already typed.

    A reader who starts filling this in while the profile is still
    in flight must not have it taken away underneath them, which
    is why every field here is a null-or-blank test rather than an
    assignment. */
fun SetupState.seeded(profile: Profile?, fallbackName: String, started: Set<String>): SetupState {
    if (profile == null) {
        return copy(name = name.ifBlank { fallbackName })
    }
    return copy(
        name = name.ifBlank { profile.displayName.orEmpty().ifBlank { fallbackName } },
        /* Ticked BY DEFAULT rather than ticked always. A union
           computed at render time is a checkbox that refuses to
           be a checkbox: the box comes straight back. */
        following = following.ifEmpty { profile.following.orEmpty().toSet() + started },
        pace = pace.ifBlank { profile.pace.orEmpty() },
        asked = asked || profile.setupAt != null,
    )
}

/**
 * The form, whichever of its two framings it is in.
 *
 * @param schools the ladders, out of the manifest, so a sixth
 *   school appears here with no release.
 * @param paces the pace vocabulary, out of the manifest, for the
 *   same reason and one stronger: it is a CHECK constraint.
 */
@Composable
fun SetupPanel(
    state: SetupState,
    schools: List<LadderSchool>,
    paces: List<Choice>,
    started: Set<String>,
    onChange: (SetupState) -> Unit,
    onSave: () -> Unit,
    onNotNow: () -> Unit,
) {
    val c = LocalReiad.current
    val setup = !state.asked
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (setup) "Set up your account" else "Your settings",
            style = MaterialTheme.typography.headlineSmall,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s3))
        Text(
            if (setup) {
                "Three things, and none of them required. Some of it is filled in " +
                    "already from what this account knows. Change what is wrong, tick " +
                    "what you are about to start, and this becomes your settings page."
            } else {
                "Three things, none of them required. You can change any of them " +
                    "whenever you like."
            },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s8))

        Question(
            ask = "Your name",
            why = "What appears beside anything you write. Nothing else about you " +
                "is shown to anyone.",
        ) {
            Field(
                value = state.name,
                onValue = { onChange(state.copy(name = it)) },
                description = "Your name",
                /* No hint. `Question` above it already asks for a
                   name, and a placeholder repeating the label is
                   the same sentence twice in two type sizes. */
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            )
        }
        Spacer(Modifier.height(Gap.s8))

        Question(
            ask = "What are you here to learn?",
            why = "The front page offers these first when you come back, and a course " +
                "you pick here shows up even before you have opened it.",
        ) {
            for (school in schools) {
                ChoiceRow(
                    label = "${school.bn} · ${school.en}",
                    /* Said out loud, because a box already ticked
                       without explanation reads as a default
                       somebody chose for you. */
                    note = if (school.key in started) {
                        "you have already started this"
                    } else {
                        school.blurb
                    },
                    on = school.key in state.following,
                    role = Role.Checkbox,
                    onPick = {
                        val next = state.following.toMutableSet()
                        if (!next.add(school.key)) next.remove(school.key)
                        onChange(state.copy(following = next))
                    },
                )
            }
        }
        Spacer(Modifier.height(Gap.s8))

        Question(
            ask = "How often do you want to practise?",
            why = "Only so this screen can tell you how the last week went.",
        ) {
            for (option in paces) {
                ChoiceRow(
                    label = option.label,
                    note = option.note,
                    on = state.pace == option.id,
                    role = Role.RadioButton,
                    onPick = { onChange(state.copy(pace = option.id)) },
                )
            }
        }
        Spacer(Modifier.height(Gap.s8))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Gap.s5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillButton(
                label = if (state.busy) "Saving…" else "Save",
                filled = true,
                onClick = { if (!state.busy) onSave() },
            )
            if (setup) {
                PillButton(
                    label = "Not now",
                    onClick = { if (!state.busy) onNotNow() },
                )
            }
        }
        state.note?.let { said ->
            Spacer(Modifier.height(Gap.s5))
            Text(
                said,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.wrong) c.danger else c.inkSoft,
            )
        }
    }
}

/* ============================================================
   Setting a target.

   The three kinds are three sources of progress this site
   ALREADY HOLDS, and that is the test a fourth has to pass: a
   `course` counts the reader's own ticks, a `habit` counts the
   days they were here, and a `metric` is a number the site
   cannot see, so they type it in. If the site cannot measure it
   out of something it already has, the bar would be a
   decoration.

   Which is why the form CHANGES SHAPE with the kind rather than
   showing every field greyed out: a course picks a school and
   needs no unit, a habit is days a week, and only a metric asks
   for a unit and a starting figure.
   ============================================================ */

/** What the target form is holding. The caller's, for the reason
    `SetupState` is. */
data class TargetDraft(
    val kind: String = "course",
    val subject: String = "",
    val label: String = "",
    val target: String = "",
    val unit: String = "",
)

/** Whether this draft is a row Postgres would accept.

    `label` is `between 1 and 80` and `target >= 0` in the
    constraint, and a course names a school. Checked here as well
    as there because a 400 arriving after the button is pressed
    is a form that looked finished. */
fun TargetDraft.ready(): Boolean {
    if (label.isBlank() || label.length > 80) return false
    val n = target.trim().toDoubleOrNull() ?: return false
    if (n <= 0) return false
    if (kind == "course" && subject.isBlank()) return false
    return true
}

fun TargetDraft.asTarget(): Target = Target(
    kind = kind,
    subject = subject,
    label = label.trim(),
    target = target.trim().toDoubleOrNull() ?: 0.0,
    unit = unit.trim(),
)

@Composable
fun AddTarget(
    kinds: List<Choice>,
    schools: List<LadderSchool>,
    onAdd: (Target) -> Unit,
) {
    val c = LocalReiad.current
    var draft by remember { mutableStateOf(TargetDraft()) }
    var open by remember { mutableStateOf(false) }

    if (!open) {
        PillButton(
            label = "Set a target",
            icon = "plus",
            onClick = { open = true },
        )
        return
    }

    Pane {
        Text("Set a target", style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s6))

        Question(
            ask = "What kind?",
            why = "Each of these is something this site can already measure. " +
                "Anything it cannot see, you keep yourself.",
        ) {
            for (kind in kinds) {
                ChoiceRow(
                    label = kind.label,
                    note = kind.note,
                    on = draft.kind == kind.id,
                    role = Role.RadioButton,
                    /* The subject is cleared with the kind. It
                       means a school for one kind and free text
                       naming a number for another, so carrying it
                       across would send a school id as the name
                       of a metric. */
                    onPick = { draft = draft.copy(kind = kind.id, subject = "", unit = "") },
                )
            }
        }
        Spacer(Modifier.height(Gap.s7))

        if (draft.kind == "course") {
            Question(ask = "Which one?", why = "Counted from your own ticks.") {
                for (school in schools) {
                    ChoiceRow(
                        label = "${school.bn} · ${school.en}",
                        note = "",
                        on = draft.subject == school.key,
                        role = Role.RadioButton,
                        onPick = { draft = draft.copy(subject = school.key) },
                    )
                }
            }
            Spacer(Modifier.height(Gap.s7))
        }

        Question(ask = "Call it what?", why = "How it will read on this screen.") {
            Field(
                value = draft.label,
                onValue = { draft = draft.copy(label = it.take(80)) },
                description = "What to call this target",
                hint = "Finish the money school",
            )
        }
        Spacer(Modifier.height(Gap.s7))

        Question(
            ask = when (draft.kind) {
                "habit" -> "How many days a week?"
                "metric" -> "What number are you aiming at?"
                else -> "How many lessons?"
            },
            why = "The end of the bar.",
        ) {
            Field(
                value = draft.target,
                onValue = { typed ->
                    /* Digits and one point. A number field on
                       Android still admits a minus sign and a
                       second separator on some keyboards, and
                       `target >= 0` is a constraint rather than a
                       preference. */
                    draft = draft.copy(target = typed.filter { it.isDigit() || it == '.' })
                },
                description = "The number to aim at",
                hint = "60",
                keyboard = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )
            if (draft.kind == "metric") {
                Field(
                    value = draft.unit,
                    onValue = { draft = draft.copy(unit = it.take(20)) },
                    description = "The unit, if it has one",
                    hint = "kg, hours, pages",
                )
            }
        }
        Spacer(Modifier.height(Gap.s8))

        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            PillButton(
                label = "Add it",
                filled = true,
                onClick = {
                    if (draft.ready()) {
                        onAdd(draft.asTarget())
                        draft = TargetDraft()
                        open = false
                    }
                },
            )
            PillButton(label = "Cancel", onClick = { open = false; draft = TargetDraft() })
        }
    }
}

/* ============================================================
   The routine, in one line.
   ============================================================ */

/** What the account says about a reader's routine.

    Its NAME, how many things are in it, and how many days have
    been written: the site's own three, and none of them needs
    the tool's year of entries or six charts. */
data class RoutineLine(
    val built: Boolean = false,
    val name: String = "",
    val tasks: Int = 0,
    val written: Int = 0,
)

@Composable
fun RoutinePanel(line: RoutineLine, onOpen: () -> Unit) {
    val c = LocalReiad.current
    Pane {
        if (!line.built) {
            Text(
                "You have not built one yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink,
            )
            Spacer(Modifier.height(Gap.s3))
            Text(
                /* Sent to the SITE for this, and the sentence says
                   so rather than offering a button that opens a
                   browser without warning. The app can read a
                   routine and cannot build one: `TEMPLATES` is
                   held back from the manifest for exactly that
                   reason, and a screen that pretended otherwise
                   would be the promise this app cannot keep. */
                "Building one is on the site for now: start from a template or " +
                    "from nothing, and it will be here next time you open this.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
            return@Pane
        }
        Text(line.name, style = MaterialTheme.typography.titleSmall, color = c.ink)
        Spacer(Modifier.height(Gap.s3))
        Text(
            "${inScript(line.tasks.toString(), "bn")} things a day, " +
                "${inScript(line.written.toString(), "bn")} days written.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s6))
        PillButton(label = "Open today", filled = true, onClick = onOpen)
        Spacer(Modifier.height(Gap.s5))
        Text(
            "Your routine is on your account, so it is the same on every device " +
                "you sign in on.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}
