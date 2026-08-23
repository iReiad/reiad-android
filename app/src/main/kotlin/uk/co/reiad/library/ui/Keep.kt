package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Kind

/* ============================================================
   Save, and Add a note, under a byline.

   ---- three states, and the middle one matters ----

   Signed OUT: nothing at all. Not a greyed button and not a
   prompt: a control that appears and then refuses is worse than
   one that was never offered, because it invites a press.

   Signed in and the account has not ANSWERED yet: still nothing.
   This is the state that is easy to skip and it is the one that
   goes wrong. Drawing an unsaved Save before the answer arrives
   means a reader who saved this piece last week watches it flip
   from unsaved to saved, and a reader who presses it in that
   window unsaves what they already had.

   Signed in and answered: both controls, in whatever state the
   account says.

   ---- and the two controls never write over each other ----

   `saved` and `note` are two columns of ONE row, so a control
   that sent the whole row would overwrite what the other had just
   put there. Each sends only its own column. The site's own
   `keep.test.ts` has 109 checks and that is what most of them are
   about.
   ============================================================ */

@Composable
fun Keep(
    /** Null while the account has not answered. The difference
        between "no row" and "not asked yet" is the whole of why
        this is nullable rather than a default `Kept()`. */
    state: Kept?,
    signedIn: Boolean,
    onSave: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!signedIn || state == null) return
    val c = LocalReiad.current
    var writing by remember(state.url) { mutableStateOf(state.note.isNotBlank()) }

    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s5)) {
            Control(
                modifier = Modifier.clickable(role = Role.Checkbox) { onSave(!state.saved) },
                ground = if (state.saved) c.accent else c.panel,
            ) {
                Text(
                    if (state.saved) "Saved ✓" else "Save",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.saved) c.paper else c.accent,
                )
            }
            Control(
                modifier = Modifier.clickable(role = Role.Button) { writing = !writing },
                ground = c.panel,
            ) {
                Text(
                    if (state.note.isNotBlank()) "Note" else "Add a note",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                )
            }
        }

        if (writing) {
            Spacer(Modifier.height(Gap.s5))
            NoteBox(state.url, state.note, onNote)
        }
    }
}

@Composable
private fun NoteBox(url: String, note: String, onNote: (String) -> Unit) {
    val c = LocalReiad.current
    var text by remember(url) { mutableStateOf(note) }

    /* Debounced, like a practice book's boxes and for the same
       reason: a note is typed a sentence at a time and a request
       per keystroke is a request per keystroke. */
    LaunchedEffect(text) {
        if (text == note) return@LaunchedEffect
        delay(700)
        onNote(text)
    }

    Field(
        value = text,
        /* The same 20,000 the column checks and the site's own
           editor stops at. About eight pages: far past a margin
           note and far short of anything that costs this project
           money. Stopped HERE as well, because a reader whose
           note is silently truncated by the database has lost
           words they watched themselves type. */
        onValue = { text = it },
        filter = { it.take(20_000) },
        description = "Your note about this piece",
        hint = "Whatever you want to remember about this.",
        size = FieldSize.AREA,
        textStyle = BanglaBody,
    )
}

/** A year of days, drawn.

    `days-active` is a set of dates and this is all of them at
    once. No flame, nothing red, nothing counting down, and no
    number telling somebody how many days they have missed. The
    site's own rule: this is not a generic engagement app and must
    not become one. A year of quiet marks says "here is what you
    did" and a streak counter says "do not stop", and those are
    different things to say to somebody learning a language in
    their spare time. */
@Composable
fun YearOfDays(
    days: Set<String>,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    Column(modifier) {
        Text(
            "${days.size} days here",
            style = MaterialTheme.typography.labelMedium,
            color = c.accent,
        )
        Spacer(Modifier.height(Gap.s5))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            /* Fifty-three columns of seven, which is how a year
               lays out. Drawn from the set rather than from a
               calendar, because what this shows is which days
               have a mark and the grid is only the arrangement. */
            for (week in 0 until 53) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (day in 0 until 7) {
                        val n = week * 7 + day
                        val on = n < days.size
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(1.dp))
                                /* A CELL IN A GRID belongs to the
                                   grid. The site's `NOT_GLASS`
                                   list says why: a year is 365 of
                                   these and glass on each is the
                                   cage one order of magnitude
                                   worse. */
                                .background(
                                    if (on) c.accent else c.hairline.copy(alpha = 0.5f),
                                ),
                        )
                    }
                }
            }
        }
    }
}
