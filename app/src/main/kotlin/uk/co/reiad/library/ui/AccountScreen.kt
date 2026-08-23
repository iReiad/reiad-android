package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import androidx.compose.foundation.lazy.items
import uk.co.reiad.library.core.Choice
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Reader
import uk.co.reiad.library.core.Target
import uk.co.reiad.library.core.isDone
import uk.co.reiad.library.core.reachedFor

/* ============================================================
   The account, and what being signed in actually means.

   Four sentences, and the screen says all four out loud rather
   than leaving a reader to work out what a Sign in button will
   do to the ticks already on their phone:

   | | |
   | --- | --- |
   | signed out | nothing. No request, no listener, no storage touched. Progress is this phone's and everything still works. |
   | signing in | the account's rows are written on to this phone, and any synced key the account does not have is REMOVED. What was here first is not merged and not uploaded. |
   | signed in | the phone is a mirror. A tick here goes up; a tick on the laptop comes down. |
   | signing out | the mirror comes off, so the next person at this phone does not inherit somebody's reading. |

   The second is the one that surprises people, and it is the one
   that has to be said before the button is pressed rather than
   after. A phone is not a copy of an account: it may have been
   handed to somebody for five minutes, and nothing here can tell.
   ============================================================ */

@Composable
fun AccountScreen(
    reader: Reader?,
    kept: List<Kept>,
    targets: List<Target>,
    daysActive: Set<String>,
    ticksOf: (String) -> Int,
    onOpenKept: (Kept) -> Unit,
    onRemoveTarget: (String) -> Unit,
    onExport: () -> Unit,
    exported: String?,
    onErase: () -> Unit,
    erasing: String?,
    /** What went wrong last time, if anything. Silently doing
        nothing is the one response to a failed sign-in that
        leaves somebody pressing the same button again. */
    problem: String?,
    linkSent: Boolean,
    bottomPadding: Dp,
    onGoogle: () -> Unit,
    onLink: (String) -> Unit,
    onSignOut: () -> Unit,
    /* ---- the three questions, and the target form ----

       All of it is the caller's state, because all of it is a
       WRITE: a screen that owned its own answers would have to
       hand them back through a callback, which is the same thing
       with more steps and one more place for a label to drift.

       Defaulted so the render tests can draw this screen without
       a profile, which is also what a reader who has never
       answered sees. */
    setup: SetupState = SetupState(),
    schools: List<LadderSchool> = emptyList(),
    paces: List<Choice> = emptyList(),
    targetKinds: List<Choice> = emptyList(),
    started: Set<String> = emptySet(),
    onSetupChange: (SetupState) -> Unit = {},
    onSaveProfile: () -> Unit = {},
    onNotNow: () -> Unit = {},
    onAddTarget: (Target) -> Unit = {},
    /** Where the reader stands in each school whose ladder has
        arrived. Empty draws the section away entirely rather than
        four bars at nought. */
    paths: List<Path> = emptyList(),
    onOpenSchool: (LadderSchool) -> Unit = {},
) {
    val c = LocalReiad.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = TOP_CLEARANCE, bottom = bottomPadding),
    ) {
        item("head") {
            PageHead(
                title = "আপনার অ্যাকাউন্ট",
                eyebrow = "You · আপনার",
                lede = if (reader == null) {
                    "আপনি কতদূর পড়েছেন, কী সাভ করেছেন আর কী লক্ষ্য ঠিক করেছেন: সব এক জায়গায়, সব ডিভাইসে এক."
                } else {
                    null
                },
            )
            Spacer(Modifier.height(Gap.s7))
        }

        if (reader != null) {
            item("who") {
                Pane {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reader.picture.isNotBlank()) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(Corner.pill)),
                            ) {
                                Photo(reader.picture, reader.name)
                            }
                            Spacer(Modifier.width(Gap.s6))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                reader.name.ifBlank { reader.email },
                                style = MaterialTheme.typography.titleMedium,
                                color = c.ink,
                            )
                            if (reader.name.isNotBlank() && reader.email.isNotBlank()) {
                                Text(
                                    reader.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.inkSoft,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Gap.s7))
                InfoCard(
                    title = "This phone is a mirror",
                    dek = "A tick here goes up; a tick on your laptop comes down. " +
                        "What you type into a practice book stays on this phone and " +
                        "is never sent anywhere.",
                )
                Spacer(Modifier.height(Gap.s9))
            }

            /* ---- the three questions ----

               ABOVE the year and the targets, because a reader
               who has never answered them is being ASKED, and a
               question below three panels of results is a
               question nobody scrolls to. Once answered it is
               the settings section and its position stops
               mattering. */
            item("setup") {
                Pane {
                    SetupPanel(
                        state = setup,
                        schools = schools,
                        paces = paces,
                        started = started,
                        onChange = onSetupChange,
                        onSave = onSaveProfile,
                        onNotNow = onNotNow,
                    )
                }
                Spacer(Modifier.height(Gap.s9))
            }

            /* ---- where you are ----

               Above the year, because "how far through am I" is
               the question this screen exists for and a calendar
               is the answer to a different one. */
            if (paths.isNotEmpty()) {
                item("paths") {
                    Text(
                        "WHERE YOU ARE",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                    Spacer(Modifier.height(Gap.s5))
                    Paths(paths, onOpenSchool)
                    Spacer(Modifier.height(Gap.s9))
                }
            }

            /* ---- a year of days ---- */
            item("year") {
                Text("DAYS HERE", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                Spacer(Modifier.height(Gap.s5))
                Pane { YearOfDays(daysActive) }
                Spacer(Modifier.height(Gap.s9))
            }

            /* ---- what is being aimed at ---- */
            if (targets.isNotEmpty()) {
                item("targets-head") {
                    Text("AIMING AT", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                    Spacer(Modifier.height(Gap.s5))
                }
                items(targets, key = { "t-" + it.id }) { target ->
                    TargetRow(target, ticksOf, daysActive.size) { onRemoveTarget(target.id) }
                    Spacer(Modifier.height(Gap.s5))
                }
                item("targets-foot") { Spacer(Modifier.height(Gap.s5)) }
            }

            /* ---- and setting a new one ----

               Outside the `isNotEmpty` above, deliberately: a
               reader with no targets is exactly the reader who
               needs the form, and it sat inside that branch for
               one draft, so the only way to get a first target
               was to already have one. */
            item("targets-add") {
                if (targets.isEmpty()) {
                    Text(
                        "AIMING AT",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                    Spacer(Modifier.height(Gap.s5))
                }
                AddTarget(
                    kinds = targetKinds,
                    schools = schools,
                    onAdd = onAddTarget,
                )
                Spacer(Modifier.height(Gap.s9))
            }

            /* ---- the reading list ---- */
            item("kept-head") {
                Text(
                    "SAVED AND NOTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSoft,
                )
                Spacer(Modifier.height(Gap.s5))
                if (kept.isEmpty()) {
                    InfoCard(
                        title = "Nothing saved yet",
                        dek = "Save is under the byline of every piece and every lesson, " +
                            "with a note beside it.",
                    )
                    Spacer(Modifier.height(Gap.s7))
                }
            }
            items(kept, key = { "k-" + it.id }) { row ->
                KeptRow(row) { onOpenKept(row) }
                Spacer(Modifier.height(Gap.s4))
            }

            /* ---- leaving ---- */
            item("out") {
                Spacer(Modifier.height(Gap.s9))
                Text("LEAVING", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                Spacer(Modifier.height(Gap.s5))
                Text(
                    "Leaving should be as easy as arriving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
                Spacer(Modifier.height(Gap.s5))
                Control(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onExport),
                    ground = c.panel,
                ) {
                    Text(
                        "Take a copy of everything",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.accent,
                    )
                }
                exported?.let {
                    Spacer(Modifier.height(Gap.s5))
                    Plate(Modifier.fillMaxWidth()) {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = c.ink)
                    }
                }

                Spacer(Modifier.height(Gap.s7))
                Erase(onErase, erasing)

                Spacer(Modifier.height(Gap.s7))
                Control(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClick = onSignOut),
                    ground = c.panel,
                ) {
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.accent,
                    )
                }
                Spacer(Modifier.height(Gap.s4))
                Text(
                    "Signing out takes the mirror off this phone, so the next person " +
                        "using it does not inherit your reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
                Spacer(Modifier.height(Gap.s9))
            }
            return@LazyColumn
        }

        item("in") {
            problem?.let {
                InfoCard(title = "That sign-in did not go through", dek = it)
                Spacer(Modifier.height(Gap.s7))
            }

            /* Said BEFORE the button, not after. This is the one
               thing about signing in that surprises people, and a
               reader with forty lessons ticked on this phone
               deserves to know what happens to them. */
            InfoCard(
                title = "What signing in does",
                dek = "Your account's reading is written on to this phone, and anything " +
                    "ticked here that the account does not have is removed. Nothing on " +
                    "this phone is uploaded. If you have been reading here without an " +
                    "account, tick what matters on the site first.",
            )
            Spacer(Modifier.height(Gap.s8))

            Control(
                modifier = Modifier
                    /* The whole groove, not just its width. The
                       field's own node is what carries the click,
                       so a text field drawn one line tall inside
                       a 44dp box is a 17dp target however tall
                       the box is. */
                    .fillMaxSize()
                    .clickable(role = Role.Button, onClick = onGoogle),
                ground = c.accent,
            ) {
                Text(
                    "Continue with Google",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.paper,
                )
            }
            Spacer(Modifier.height(Gap.s8))

            Text("Or by email", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
            Spacer(Modifier.height(Gap.s4))
            EmailBox(linkSent, onLink)

            Spacer(Modifier.height(Gap.s8))
            Text(
                "Signed out, nothing here talks to a server about you. Your reading is " +
                    "this phone's and every page still works.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }
    }
}

@Composable
private fun EmailBox(sent: Boolean, onLink: (String) -> Unit) {
    val c = LocalReiad.current
    var email by remember { mutableStateOf("") }

    if (sent) {
        Plate(Modifier.fillMaxWidth()) {
            Text(
                "Check your email. The link opens straight back here.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink,
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Gap.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(Gap.tap)
                /* A GROOVE, for the reason the site's `NOT_GLASS`
                   note gives: a text field's affordance is the
                   caret and the focus ring, and a lit resting rim
                   on a box you type into is a box that looks like
                   a button. */
                .clip(RoundedCornerShape(Corner.pill))
                .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
                .padding(horizontal = Gap.s7),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (email.isEmpty()) {
                Text(
                    "you@example.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft,
                )
            }
            BasicTextField(
                value = email,
                onValueChange = { email = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Send,
                ),
                modifier = Modifier
                    /* The whole groove, not just its width. The
                       field's own node is what carries the click,
                       so a text field drawn one line tall inside
                       a 44dp box is a 17dp target however tall
                       the box is. */
                    .fillMaxSize()
                    /* The placeholder is drawn as a sibling Text
                       so the field itself carries no label at
                       all, and a screen reader reaches an empty
                       edit box: "edit box", and nothing about
                       what goes in it. The placeholder is a
                       drawing; this is the name. */
                    .semantics { contentDescription = "Your email address" },
            )
        }
        Control(
            modifier = Modifier
                /* The same minimum `PillButton` carries: a short
                   label is a target too narrow in one direction.
                   This is a bare `Control` rather than a
                   `PillButton` because it sits inside the email
                   row and shares its height, so it needs saying
                   here too. */
                .widthIn(min = Gap.tap)
                .clickable(
                    role = Role.Button,
                    enabled = '@' in email,
                ) { onLink(email.trim()) },
            ground = if ('@' in email) c.accent else c.paperSunk,
        ) {
            Text(
                "Send",
                style = MaterialTheme.typography.labelLarge,
                color = if ('@' in email) c.paper else c.inkSoft,
            )
        }
    }
}


/** One thing being aimed at.

    The bar is COMPUTED for a course and a habit, from what this
    device already holds, so it moves the moment a lesson is
    ticked rather than after an exchange. Only a metric shows a
    stored number, because only a metric has one the site could
    not work out.

    And finished is what the reader SAID, not where the bar is:
    somebody may call a goal done at eighty per cent, and somebody
    else may pass a number and want to keep going. */
@Composable
private fun TargetRow(
    target: Target,
    ticksOf: (String) -> Int,
    daysActive: Int,
    onRemove: () -> Unit,
) {
    val c = LocalReiad.current
    val reached = reachedFor(target, ticksOf, daysActive)
    val done = isDone(target)
    Pane {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    target.label,
                    style = if (isBangla(target.label)) BanglaTitle
                    else MaterialTheme.typography.titleMedium,
                    color = c.ink,
                )
                Text(
                    buildString {
                        append(reached.trim())
                        append(" of ")
                        append(target.target.trim())
                        if (target.unit.isNotBlank()) append(" ").append(target.unit)
                        if (done) append(" · finished")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (done) c.accent else c.inkSoft,
                )
            }
            Box(Modifier.clickable(role = Role.Button, onClick = onRemove).padding(Gap.s5)) {
                Icon("close", size = 16.dp, tint = c.inkSoft)
            }
        }
        Spacer(Modifier.height(Gap.s5))
        Groove(
            if (target.target <= 0.0) 0f else (reached / target.target).toFloat(),
        )
    }
}

/** A number without a trailing nought nobody asked for. */
private fun Double.trim(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

@Composable
private fun KeptRow(row: Kept, onOpen: () -> Unit) {
    val c = LocalReiad.current
    Rung(Modifier.clickable(role = Role.Button, onClick = onOpen)) {
        Icon(if (row.kind == "lesson") "book" else "pen", size = 18.dp, tint = c.accent)
        Spacer(Modifier.width(Gap.s6))
        Column(Modifier.weight(1f)) {
            Text(
                row.title.ifBlank { row.url },
                style = if (isBangla(row.title)) BanglaBody
                else MaterialTheme.typography.bodyLarge,
                color = c.ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (row.note.isNotBlank()) {
                Text(
                    row.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (row.saved) {
            Spacer(Modifier.width(Gap.s5))
            Text("✓", style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
    }
}


/** Erasing everything, behind one question.

    A confirmation rather than a straight button, because this is
    the one control on the screen that cannot be undone and a
    reader who meant to press Sign out is one row above it.

    What it says is what it DOES: the rows go, and the login does
    not. Deleting an auth user needs a service-role key, which
    this project does not have and has no reason to start having,
    and a button promising to delete an account that then leaves
    the account able to sign in would be a lie a reader only finds
    out about afterwards. */
@Composable
private fun Erase(onErase: () -> Unit, erasing: String?) {
    val c = LocalReiad.current
    var asking by remember { mutableStateOf(false) }

    if (erasing != null) {
        Plate(Modifier.fillMaxWidth()) {
            Text(erasing, style = MaterialTheme.typography.bodyMedium, color = c.ink)
        }
        return
    }

    if (!asking) {
        Control(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { asking = true },
            ground = c.panel,
        ) {
            Text(
                "Erase everything",
                style = MaterialTheme.typography.labelLarge,
                color = c.danger,
            )
        }
        return
    }

    Pane {
        Text(
            "Erase everything this account has saved?",
            style = MaterialTheme.typography.titleMedium,
            color = c.ink,
        )
        Spacer(Modifier.height(Gap.s4))
        Text(
            "Your position, your checkpoints, your reading list, your notes and your " +
                "targets. This cannot be undone. Your login stays, so you can start " +
                "again from nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        Spacer(Modifier.height(Gap.s6))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            Control(
                modifier = Modifier.clickable(role = Role.Button) { asking = false },
                ground = c.panel,
            ) {
                Text("Keep it", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
            Control(
                modifier = Modifier.clickable(role = Role.Button) {
                    asking = false
                    onErase()
                },
                ground = c.danger,
            ) {
                Text(
                    "Erase everything",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.paper,
                )
            }
        }
    }
}
