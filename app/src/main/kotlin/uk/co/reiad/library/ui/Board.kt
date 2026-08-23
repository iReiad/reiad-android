package uk.co.reiad.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Placed
import uk.co.reiad.library.core.WidgetKind
import uk.co.reiad.library.core.WidgetSize

/* ============================================================
   The board, and the controls that arrange it.

   `core/Board.kt` is the arithmetic and the contract; this is
   the chrome around one widget and the picker that adds one.
   Nothing here knows what any particular widget DRAWS, which is
   the point: `Home` passes a renderer in, and a kind with no
   renderer never reaches this file.

   ---- why arranging is a MODE ----

   Because the alternative is a remove button on every card of a
   page a reader is trying to read. A dashboard covered in ✕ is
   a dashboard that looks unfinished, and the one press nobody
   wants to make by accident is the one that takes something
   away.

   So the board is a board until somebody says সাজান, and then
   for as long as that lasts every widget grows a strip of
   controls and the page says what it is doing.

   ---- arrows rather than a drag, for now ----

   A long-press drag is the nicer gesture and it is the one that
   cannot be asserted without a finger, cannot be reached by a
   switch or a screen reader, and cannot be undone. Two arrows
   are a worse gesture and a better control: they are 44dp, they
   have names a screen reader can say, and `BoardTest` already
   holds the arithmetic they call. The drag can arrive on top of
   them later; it cannot replace them.
   ============================================================ */

/** One widget on the board, with its arranging controls when the
    board is being arranged.

    The controls are a strip ABOVE the widget rather than an
    overlay on it, because an overlay on glass is a second
    surface on a surface and the site has one rule about that:
    every kind having the same rest state is what turned the rail
    into twenty boxes. */
@Composable
fun WidgetFrame(
    kind: WidgetKind,
    placed: Placed,
    arranging: Boolean,
    first: Boolean,
    last: Boolean,
    lang: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    body: @Composable () -> Unit,
) {
    if (!arranging) {
        body()
        return
    }

    val c = LocalReiad.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Corner.pill))
                .material(Kind.GROOVE, c, Corner.pill)
                .padding(horizontal = Gap.s4, vertical = Gap.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                kind.name(lang),
                style = MaterialTheme.typography.labelMedium,
                color = c.inkSoft,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Gap.s4),
            )
            /* Absent rather than present and inert at the ends of
               the list. A control that cannot do anything is a
               control a reader presses twice before deciding the
               page is broken. */
            if (!first) {
                Handle("chevron-up", "${kind.name(lang)}: উপরে নিন", onUp)
            }
            if (!last) {
                Handle("chevron-down", "${kind.name(lang)}: নিচে নামান", onDown)
            }
            kind.other(placed.size)?.let { other ->
                Handle(
                    if (other == WidgetSize.HALF) "shrink" else "grow",
                    "${kind.name(lang)}: " + if (other == WidgetSize.HALF) "ছোট করুন" else "বড় করুন",
                    onResize,
                )
            }
            Handle("close", "${kind.name(lang)}: সরিয়ে দিন", onRemove)
        }
        Spacer(Modifier.height(Gap.s3))
        /* Dimmed, so the strip above it is what the eye goes to
           and nobody tries to press a card that is being moved
           rather than read. */
        Box(Modifier.alpha(0.72f)) { body() }
    }
}

/** One 44dp control in the strip. */
@Composable
private fun Handle(icon: String, label: String, onClick: () -> Unit) {
    val c = LocalReiad.current
    Box(
        Modifier
            .size(Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, size = 17.dp, tint = c.ink)
    }
}

/** What is not on the board yet, offered.

    A kind whose `needs` this reader does not meet is still
    OFFERED, with the reason said under it, rather than hidden.
    Hiding it means a reader who signs in later never finds out
    the widget exists, which is the same failure the site's
    `unlisted` flag exists to avoid one level up. */
@Composable
fun WidgetPicker(
    offered: List<WidgetKind>,
    lang: String,
    signedIn: Boolean,
    onAdd: (WidgetKind) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gap.s5)) {
        Text(
            if (lang == "bn") "আরও যোগ করুন" else "Add to your board",
            style = MaterialTheme.typography.titleSmall,
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )

        if (offered.isEmpty()) {
            InfoCard(
                title = if (lang == "bn") "সবগুলোই বোর্ডে আছে" else "Everything is on your board",
                dek = if (lang == "bn") {
                    "যেটা লাগবে না সেটা সরিয়ে দিলে এখানে আবার দেখা যাবে।"
                } else {
                    "Take one off and it comes back here."
                },
            )
        }

        for (kind in offered) {
            val locked = kind.needs == "account" && !signedIn
            Pane(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(kind.icon, size = 18.dp, tint = c.accent)
                    Spacer(Modifier.width(Gap.s5))
                    Column(Modifier.weight(1f)) {
                        Text(
                            kind.name(lang),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.ink,
                        )
                        if (kind.note.isNotBlank()) {
                            Spacer(Modifier.height(Gap.s2))
                            Text(
                                kind.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.inkSoft,
                            )
                        }
                        if (locked) {
                            Spacer(Modifier.height(Gap.s2))
                            /* Said, not hidden. A widget nobody
                               can see is a widget nobody signs in
                               for. */
                            Text(
                                if (lang == "bn") {
                                    "অ্যাকাউন্টে ঢুকলে এটা কাজ করবে।"
                                } else {
                                    "This one needs an account."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = c.accent,
                            )
                        }
                    }
                    Spacer(Modifier.width(Gap.s5))
                    PillButton(
                        label = if (lang == "bn") "যোগ করুন" else "Add",
                        onClick = { onAdd(kind) },
                        icon = "plus",
                        description = "${kind.name(lang)}: " +
                            if (lang == "bn") "বোর্ডে যোগ করুন" else "add to your board",
                    )
                }
            }
        }

        Spacer(Modifier.height(Gap.s4))
        PillButton(
            label = if (lang == "bn") "আগের মতো করে দিন" else "Back to the default",
            onClick = onReset,
            icon = "arrow",
        )
    }
}
