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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Accent
import uk.co.reiad.library.core.Accents
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.LadderSchool
import uk.co.reiad.library.core.Standing
import kotlin.math.roundToInt

/* ============================================================
   Where you are, one row per school.

   The site's `account/paths.tsx`. How far through, the
   checkpoints ticked inside those lessons, and the one link that
   matters, which is the next thing to read.

   ---- nothing is drawn from nothing ----

   Every number here is one reader's, and a row for a school
   whose ladder has not arrived draws NOTHING rather than an
   empty bar. "You have finished nothing" and "this has not
   loaded" must not look the same, which is the same rule
   `Waiting.kt` opens with.

   ---- and each row wears its own school's colour ----

   Which the rail taught the reader: the German book is blue and
   the Qur'anic scroll is teal. One `ReiadTheme` per row does it,
   so the bar, the row and its arrow all follow.
   ============================================================ */

/** One school's standing, with the school it belongs to. */
data class Path(
    val school: LadderSchool,
    val at: Standing,
)

/**
 * A meter.
 *
 * A GROOVE with a fill in it, which is the site's own words for
 * this: a channel cut in, and what sits in the channel is a FILL
 * and gets no cut edge of its own, because a bevel inside a bevel
 * is a channel drawn inside a channel.
 */
@Composable
fun Meter(pct: Double, label: String, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    /* The bar pours to its number rather than appearing at it,
       in the groove's own slow step. */
    val share by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (pct / 100).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = if (rememberReducedMotion()) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.SLOW_MS)
        },
        label = "meter",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk)
            .semantics { contentDescription = label },
    ) {
        /* Laid out rather than given a fraction, because
           `fillMaxWidth(0f)` is a zero-width box that Compose is
           entitled to skip and a 1px sliver at nought per cent
           reads as progress nobody has made. */
        Layout(
            content = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(Corner.pill))
                        .background(c.accent),
                )
            },
            modifier = Modifier.fillMaxWidth().height(10.dp),
        ) { measurables, constraints ->
            val width = (constraints.maxWidth * share).roundToInt()
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (width > 0) {
                    measurables.first()
                        .measure(
                            constraints.copy(
                                minWidth = width,
                                maxWidth = width,
                                minHeight = constraints.maxHeight,
                            ),
                        )
                        .place(0, 0)
                }
            }
        }
    }
}

private fun plural(n: Int, one: String, many: String): String =
    "$n ${if (n == 1) one else many}"

@Composable
private fun PathRow(path: Path, onOpen: (LadderSchool) -> Unit) {
    val at = path.at
    ReiadTheme(accent = accentOfSchool(path.school), dark = LocalReiad.current.isDark) {
        val c = LocalReiad.current
        val pct = at.pct.roundToInt()
        val name = "${path.school.bn} · ${path.school.en}"
        Pane {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = Faces.mono,
                    color = c.accent,
                )
            }
            Spacer(Modifier.height(Gap.s5))
            Meter(at.pct, "$name: ${at.done} of ${at.total} chapters finished")
            Spacer(Modifier.height(Gap.s5))
            Text(
                /* The checkpoints get a clause only when there
                   are some. "0 checkpoints ticked in 0 lessons"
                   is a sentence about nothing. */
                if (at.checksDone > 0) {
                    "${at.done} of ${at.total} chapters · " +
                        plural(at.checksDone, "checkpoint", "checkpoints") + " ticked in " +
                        plural(at.checksLessons, "lesson", "lessons")
                } else {
                    "${at.done} of ${at.total} chapters"
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
            val next = at.next
            if (next != null) {
                Spacer(Modifier.height(Gap.s6))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Gap.tap)
                        .clip(RoundedCornerShape(Corner.field))
                        .material(Kind.CONTROL, c, Corner.field)
                        .clickable(role = Role.Button) { onOpen(path.school) }
                        .padding(horizontal = Gap.s6, vertical = Gap.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (at.touched) "Carry on" else "Start",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = Faces.mono,
                            color = c.accent,
                        )
                        Text(
                            next.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                        )
                    }
                    Icon("arrow", size = 18.dp, tint = c.accent)
                }
            } else {
                Spacer(Modifier.height(Gap.s4))
                Text(
                    if (at.total > 0) {
                        "Every written chapter is finished."
                    } else {
                        "Nothing written here yet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSoft,
                )
            }
        }
    }
}

/**
 * @param paths one per school whose ladder has arrived. A school
 *   missing from this list is a school this phone has not read
 *   yet, and draws nothing.
 */
@Composable
fun Paths(paths: List<Path>, onOpen: (LadderSchool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s6)) {
        /* Started first, which is the site's order and is the
           right one: a reader opening this wants the thing they
           are in the middle of, not the alphabet. */
        for (path in paths.sortedByDescending { it.at.touched }) {
            PathRow(path, onOpen)
        }
    }
}

/** A school's own colour, from the manifest's token where it has
    one and from the built-in table where it does not.

    Here rather than beside the screen that first needed it,
    because three files draw a school in its colour now and the
    third would otherwise have written the lookup out again. */
fun accentOfSchool(school: LadderSchool): Accent =
    Accents.byToken(school.accent) ?: Accents.BY_KEY[school.key] ?: Accents.GREEN
