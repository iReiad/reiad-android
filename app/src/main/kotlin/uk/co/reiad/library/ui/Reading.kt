package uk.co.reiad.library.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Piece

/* ============================================================
   A reading hub, and a piece.

   Three sections share one endpoint and one screen: Insights,
   Cooking and Travel are the same table with a different value in
   one column, so they are the same list with a different filter
   and a different accent rather than three screens.

   ---- the chips COUNT ----

   A topic chip carries how many pieces carry that topic, and the
   number is counted from the pieces on screen rather than
   remembered. That is this site's oldest rule and the one it
   states first: a page that says how many of something there are
   must count them. A chip reading "ভিসা 3" beside two pieces is
   the failure the whole `COUNTS` arrangement exists to prevent,
   one level down.

   ---- and a chip that hides nothing is not offered ----

   A single topic across every piece filters nothing, so it is not
   a chip. The site learned that on its own Insights hub: a row of
   chips where pressing any of them changes nothing is a control
   that has to be tried before it can be understood to be useless.
   ============================================================ */

@Composable
fun ReadingHub(
    title: String,
    pieces: List<Piece>,
    stale: Boolean,
    bottomPadding: Dp,
    onOpen: (Piece) -> Unit,
) {
    val c = LocalReiad.current
    var topic by remember(pieces) { mutableStateOf<String?>(null) }

    /* Counted, never remembered. */
    val counts = remember(pieces) {
        pieces.flatMap { it.topics }.groupingBy { it }.eachCount()
    }
    /* A topic on every piece filters nothing, so it is not
       offered. Ordered by how many pieces carry it, then
       alphabetically, so the list is stable between launches. */
    val topics = remember(counts, pieces) {
        counts.filter { it.value < pieces.size }
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }
    val shown = remember(pieces, topic) {
        if (topic == null) pieces else pieces.filter { topic in it.topics }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = Gap.s8),
        contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
    ) {
        item {
            PageHead(
                title = title,
                /* Counted from what is on screen, in the site's
                   own manner: the number is the data's, not a
                   sentence's. */
                eyebrow = if (pieces.size == 1) "1 piece" else "${pieces.size} pieces",
            )
            if (stale) {
                Spacer(Modifier.height(Gap.s5))
                Chip("SAVED COPY")
            }
            Spacer(Modifier.height(Gap.s8))
        }

        if (topics.isNotEmpty()) {
            item {
                Row(
                    rememberScrollState().let { slide ->
                        Modifier.fadesAtTheEnd(slide, LocalReiad.current.paper)
                            .horizontalScroll(slide)
                    },
                    horizontalArrangement = Arrangement.spacedBy(Gap.s4),
                ) {
                    TopicChip("All ${pieces.size}", topic == null) { topic = null }
                    for ((name, count) in topics) {
                        TopicChip("$name $count", topic == name) {
                            /* Pressing the one already on clears
                               it, so a chip is a latch rather
                               than a trap: there is always a way
                               back to everything without hunting
                               for an All. */
                            topic = if (topic == name) null else name
                        }
                    }
                }
                Spacer(Modifier.height(Gap.s8))
            }
        }

        if (pieces.isEmpty()) {
            item {
                InfoCard(
                    title = "Nothing here yet",
                    dek = "Either this section has no live pieces, or the app has not " +
                        "read the site yet. It works offline once it has.",
                )
            }
        }

        items(shown, key = { it.slug }) { piece ->
            PieceCard(piece, onOpen)
            Spacer(Modifier.height(Gap.s7))
        }
    }
}

@Composable
private fun TopicChip(label: String, on: Boolean, onPress: () -> Unit) {
    val c = LocalReiad.current
    Box(Modifier.clickable(role = Role.Checkbox, onClick = onPress)) {
        Chip(label, tone = if (on) c.paper else c.accent)
    }
}

/** A piece as a card that takes you somewhere.

    A `GoCard`, because it does. Its cover is the share card the
    site draws for it, which is a 1200 by 630 JPEG made from the
    lead photo rather than the photo itself, so what a reader sees
    here and what WhatsApp shows for the same link agree. */
@Composable
private fun PieceCard(piece: Piece, onOpen: (Piece) -> Unit) {
    GoCard(
        title = piece.title,
        dek = piece.dek.takeIf { it.isNotBlank() },
        chip = piece.tag.takeIf { it.isNotBlank() },
        go = if (piece.minutes > 0) "${piece.minutes} min read" else "Read",
        onOpen = { onOpen(piece) },
        art = piece.cover.takeIf { it.isNotBlank() }?.let {
            { Photo(uk.co.reiad.library.core.mediaUrl(it), piece.title) }
        },
    )
}

/* ---------- one piece ---------- */

/** The row under a title: how long, when, and in what. */
@Composable
fun Byline(piece: Piece, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (piece.minutes > 0) {
            Text(
                "${piece.minutes} MIN",
                style = MaterialTheme.typography.labelSmall,
                color = c.accent,
            )
            Spacer(Modifier.width(Gap.s5))
        }
        if (piece.publishedAt.isNotBlank()) {
            Text(
                piece.publishedAt,
                style = MaterialTheme.typography.labelSmall,
                color = c.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How far down a piece a reader is.

    A groove, which is the kind that means "a channel waiting to
    be filled", and the one place on this screen where a number
    about the reader rather than about the piece is shown. It is
    not progress in the ladder's sense: scrolling is not reading
    and this marks nothing. */
@Composable
fun ReadingLine(fraction: Float, modifier: Modifier = Modifier) {
    Groove(fraction, modifier, height = 3.dp)
}

@Composable
fun PrevNext(
    previous: Piece?,
    next: Piece?,
    onOpen: (Piece) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (previous == null && next == null) return
    val c = LocalReiad.current
    Column(modifier.fillMaxWidth()) {
        previous?.let {
            Rung(onClick = { onOpen(it) }) {
                Text("←", style = MaterialTheme.typography.labelLarge, color = c.accent)
                Spacer(Modifier.width(Gap.s6))
                Column(Modifier.weight(1f)) {
                    Text(
                        "PREVIOUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.inkSoft,
                    )
                    Text(
                        it.title,
                        style = if (it.isBangla) BanglaTitle else MaterialTheme.typography.titleSmall,
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        next?.let {
            Rung(onClick = { onOpen(it) }) {
                Column(Modifier.weight(1f)) {
                    Text("NEXT", style = MaterialTheme.typography.labelSmall, color = c.inkSoft)
                    Text(
                        it.title,
                        style = if (it.isBangla) BanglaTitle else MaterialTheme.typography.titleSmall,
                        color = c.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Gap.s6))
                Text("→", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
    }
}
