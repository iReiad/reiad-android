package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.PACES
import uk.co.reiad.library.read.Reader
import uk.co.reiad.library.read.Speaking

/* ============================================================
   The voice's own controls, in the two places they belong.

   ---- one transport, two homes ----

   While a piece is open the controls ride above the bottom bar,
   because the reader is IN the thing being read. Leave the
   piece and they appear at the top of the board instead, which
   is where a phone puts anything still going on: it arrives when
   the voice starts, it goes when the voice stops, and it is not
   part of the arrangement, so it can never be dragged, resized
   or taken off. A reader who has walked away from a lesson can
   pause it from the front page rather than hunting for the
   article it came from.

   Both draw the same row, from the same file, because two
   transports would be two chances to disagree about which
   button pauses.

   ---- and it dies with the app ----

   `ReadAloudService` stops with the task, which is the other
   half of the same report: reading that outlives the app is a
   voice with nowhere to press stop.
   ============================================================ */

/** Back, hold, ahead, how far through, and stop.

    Every control here is a fact the synthesiser can honour.
    There is no scrubber because there is no timeline, and a
    control that lies is worse than one that is missing. */
@Composable
fun ReadAloudTransport(
    speaking: Speaking,
    modifier: Modifier = Modifier,
    /** The pace switch, which the sticky controller has room for
        and the board card does not. */
    pace: Boolean = true,
) {
    val c = LocalReiad.current
    val context = LocalContext.current
    val touch = rememberTouch()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tap(
                onClick = { touch.tap(); Reader.skip(context, -1) },
                label = "Back one line",
            ) { Icon("back", size = 18.dp, tint = c.ink) }
            Spacer(Modifier.width(Gap.s4))
            Tap(
                onClick = {
                    touch.tap()
                    if (speaking.paused) Reader.resume(context) else Reader.pause(context)
                },
                label = if (speaking.paused) "Play" else "Hold",
            ) {
                Box(
                    Modifier
                        .size(Gap.tap)
                        .clip(RoundedCornerShape(Corner.pill))
                        .background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (speaking.paused) "play" else "pause", size = 18.dp, tint = c.paper)
                }
            }
            Spacer(Modifier.width(Gap.s4))
            Tap(
                onClick = { touch.tap(); Reader.skip(context, 1) },
                label = "Ahead one line",
            ) { Icon("forward", size = 18.dp, tint = c.ink) }
            Spacer(Modifier.weight(1f))
            Text(
                "${(speaking.at + 1).coerceAtLeast(1)} / ${speaking.total.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelMedium,
                color = c.inkSoft,
            )
            Spacer(Modifier.width(Gap.s5))
            Tap(
                onClick = { touch.tap(); Reader.stop(context) },
                label = "Stop reading",
            ) { Icon("close", size = 18.dp, tint = c.inkSoft) }
        }
        Spacer(Modifier.height(Gap.s4))
        Groove(
            if (speaking.total <= 0) 0f else (speaking.at + 1f) / speaking.total,
            height = 3.dp,
        )
        if (pace) {
            Spacer(Modifier.height(Gap.s5))
            Segmented(
                options = PACES,
                chosen = PACES.firstOrNull { it.id == speaking.pace },
                onChoose = { Reader.setPace(context, it.id) },
                height = 34.dp,
                label = { it.label },
            ) { option, on ->
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.paper else c.inkSoft,
                )
            }
        }
    }
}

/**
 * What is being read, on the board, for as long as it is being
 * read.
 *
 * It is NOT one of the arrangeable widgets and does not appear
 * in the picker: a card that comes and goes on its own cannot be
 * part of an arrangement a reader made, and one they could take
 * off would strand the voice with no controls anywhere. So it
 * sits above the board rather than in it, and the board it sits
 * above is unchanged underneath.
 */
@Composable
fun ReadingWidget(
    speaking: Speaking,
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    Pane(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            /* The site's own mark for saying something out
               loud, which is the one the lessons already use
               over "মুখে বলো". */
            Icon("mouth", size = 15.dp, tint = c.accent)
            Spacer(Modifier.width(Gap.s4))
            Text(
                if (speaking.paused) "ধরে রাখা আছে" else "পড়ে শোনানো হচ্ছে",
                style = BanglaBody.copy(
                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                ),
                color = c.accent,
            )
        }
        if (title.isNotBlank()) {
            Spacer(Modifier.height(Gap.s3))
            Text(
                title,
                style = bodyStyle(title),
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                /* Tapping the title goes BACK to the piece, which
                   is the one thing this card can offer that the
                   notification cannot. */
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen),
            )
        }
        Spacer(Modifier.height(Gap.s5))
        ReadAloudTransport(speaking, pace = false)
    }
}
