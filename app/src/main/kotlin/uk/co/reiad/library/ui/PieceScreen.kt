package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Block
import uk.co.reiad.library.core.BodyParser
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.PACES
import uk.co.reiad.library.core.Pace
import androidx.compose.foundation.layout.size
import uk.co.reiad.library.core.Piece
import uk.co.reiad.library.core.Utterance
import uk.co.reiad.library.core.speakable
import uk.co.reiad.library.read.Reader
import uk.co.reiad.library.read.Speaking

/* ============================================================
   One piece, read.

   The body arrives as sanitised HTML, goes through the parser
   that `BodyParserTest` proves against real lessons, and is drawn
   block by block. What this screen adds around it is the
   furniture: the byline, the reading line, the read-aloud control
   and the prev/next pair.

   ---- the reading line is not progress ----

   It says how far down the page a reader has scrolled and it
   marks NOTHING. Scrolling is not reading, and this site has a
   rule about that one level up: opening is not finishing, and a
   tick is a button somebody presses. A bar that quietly ticked a
   piece off for being scrolled past would be the same mistake in
   a smaller place.
   ============================================================ */

@Composable
fun PieceScreen(
    piece: Piece,
    previous: Piece?,
    next: Piece?,
    stale: Boolean,
    bottomPadding: Dp,
    /** Null while the account has not answered, which is not the
        same as "no row": drawing an unsaved Save in that window
        makes a reader who saved this last week watch it flip. */
    kept: Kept?,
    signedIn: Boolean,
    onKeep: (Boolean?, String?) -> Unit,
    onOpen: (Piece) -> Unit,
    onBack: () -> Unit,
    /** The thread under it, or null on a screen that does not
        take comments. Null draws nothing at all rather than an
        empty heading. */
    thread: ThreadState? = null,
    onLeaveComment: (String, Int?) -> Unit = { _, _ -> },
    onRetryThread: () -> Unit = {},
) {
    val c = LocalReiad.current
    val context = LocalContext.current
    val scroll = rememberLazyListState()
    val speaking by Reader.state.collectAsStateWithLifecycle()

    /**
     * The body, parsed OFF the main thread.
     *
     * `remember { parse(body) }` runs inside composition, which
     * is the main thread, and a long piece is a hitch at the one
     * moment a reader is watching: the frame where the article
     * opens. `produceState` with a `Default` hop parses on a
     * worker and the page fills in behind, which is what the
     * skeleton below is for.
     *
     * Keyed on the SLUG as well as the body, because a body that
     * arrives empty and then fills is one piece and not two: the
     * list answer carries no body and the full one does.
     */
    val parsed by produceState(
        initialValue = emptyList<Block>(),
        piece.slug,
        piece.body,
    ) {
        value = if (piece.body.isBlank()) {
            emptyList()
        } else {
            withContext(Dispatchers.Default) { BodyParser.parse(piece.body).blocks }
        }
    }
    val blocks = parsed
    val lines = remember(blocks) { speakable(blocks) }

    /* Derived, so scrolling invalidates the bar and nothing else.
       Read as state in composition it would recompose the whole
       piece on every frame of a fling. */
    val read by remember {
        derivedStateOf {
            val info = scroll.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f
            else (info.visibleItemsInfo.lastOrNull()?.index ?: 0).toFloat() / (total - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(horizontal = Gap.s8),
            contentPadding = PaddingValues(top = topClearance(), bottom = bottomPadding),
        ) {
            item("head") {
                Crumb("Back", onBack)
                Spacer(Modifier.height(Gap.s7))
                PageHead(
                    title = piece.title,
                    eyebrow = piece.section.takeIf { it.isNotBlank() },
                    lede = piece.dek.takeIf { it.isNotBlank() },
                )
                Byline(piece)
                if (stale) {
                    Spacer(Modifier.height(Gap.s5))
                    Chip("SAVED COPY")
                }
                Spacer(Modifier.height(Gap.s6))
                Keep(
                    state = kept,
                    signedIn = signedIn,
                    onSave = { onKeep(it, null) },
                    onNote = { onKeep(null, it) },
                )
                Spacer(Modifier.height(Gap.s6))
                ReadAloudBar(piece, lines, speaking)
                Spacer(Modifier.height(Gap.s8))
            }

            if (blocks.isEmpty()) {
                item("empty") {
                    Text(
                        if (piece.body.isBlank()) "Opening" else "This one has no words yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.inkSoft,
                    )
                }
            }

            itemsIndexedKeyed(blocks) { index, block ->
                /* The block being spoken is marked, so somebody
                   listening with the screen on can follow. The
                   mark is a ground rather than a highlight
                   colour: a yellow bar over prose is a
                   highlighter pen, and this is the material's own
                   accent at a whisper. */
                val on = speaking.on && speaking.block == index
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (on) {
                                Modifier
                                    .clip(RoundedCornerShape(Corner.field))
                                    .background(c.accent.copy(alpha = 0.10f))
                                    .padding(horizontal = Gap.s5, vertical = Gap.s4)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    BodyView(listOf(block))
                }
                Spacer(Modifier.height(Gap.s6))
            }

            /* The thread, BETWEEN the piece and where to go
               next. It belongs after the words and before the
               next piece, which is where the site puts it and
               where a reader who has just finished reading
               looks. */
            thread?.let { state ->
                item("thread") {
                    Spacer(Modifier.height(Gap.s10))
                    Thread(
                        state = state,
                        signedIn = signedIn,
                        onLeave = onLeaveComment,
                        onRetry = onRetryThread,
                    )
                }
            }

            item("foot") {
                Spacer(Modifier.height(Gap.s9))
                PrevNext(previous, next, onOpen)
            }
        }

        /* A hairline at the very top saying how far down the page
           this is. Above the content and outside the scroll, so
           it does not move with what it measures. */
        ReadingLine(read, Modifier.align(Alignment.TopCenter))

        /* The voice's own controls, riding above the bar while
           it reads. OUTSIDE the scroll on purpose: the reader
           this serves has the phone at arm's length listening,
           and a control that has scrolled away is a control that
           does not exist. */
        androidx.compose.animation.AnimatedVisibility(
            visible = speaking.on,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
            enter = androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.ENTER_MS),
            ) + androidx.compose.animation.slideInVertically(
                androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.ENTER_MS),
            ) { it / 2 },
            exit = androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.QUICK_MS),
            ),
        ) {
            ReadAloudController(speaking)
        }
    }
}

/** The control, and what it says when it cannot work.

    Three states rather than two. A device with no synthesiser
    gets a sentence rather than a button that does nothing, which
    is the difference between a feature that is absent and a
    feature that is broken. */
@Composable
/** The bar takes the already-parsed LINES rather than the
    piece's raw body: it used to parse the whole article a
    second time, on the main thread, on a button press, with
    the same list sitting in the caller. */
private fun ReadAloudBar(piece: Piece, lines: List<Utterance>, speaking: Speaking) {
    if (lines.isEmpty()) return
    val c = LocalReiad.current
    val context = LocalContext.current

    if (speaking.unavailable) {
        Text(
            "This device has no speech voice installed, so there is nothing to read with.",
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            if (speaking.on) "Stop" else "Read aloud",
            {
                if (speaking.on) {
                    Reader.stop(context)
                } else {
                    /* `lines` is the same list, already parsed
                       and already speakable. Parsing the body a
                       second time here was the whole article
                       through the parser again, on the main
                       thread, on a button press. */
                    Reader.start(context, piece.title, lines, Pace.NORMAL)
                }
            },
            kind = ButtonKind.SOFT,
            pressed = speaking.on,
            icon = if (speaking.on) "close" else "play",
        )
        if (speaking.on) {
            Spacer(Modifier.width(Gap.s6))
            Text(
                "Keeps going with the screen off.",
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
            )
        }
    }
}

/**
 * The voice, held in the hand.
 *
 * The site's read-aloud is one button because a browser tab
 * cannot promise more. This app's voice runs with the screen
 * off, so it earns a real controller: hold and carry on, a line
 * back for the sentence that went past, a line ahead for the
 * list being skimmed, the pace the reader is offered everywhere
 * else, and how far through it is. Every control is a fact the
 * synthesiser can honour; there is no scrubber because there is
 * no timeline, and a control that lies is worse than one that is
 * missing.
 */
@Composable
private fun ReadAloudController(speaking: Speaking, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    val context = LocalContext.current
    val touch = rememberTouch()
    Pane(modifier.fillMaxWidth().padding(horizontal = Gap.s5)) {
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
            if (speaking.total <= 0) 0f
            else (speaking.at + 1f) / speaking.total,
            height = 3.dp,
        )
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

/* ---------- two small helpers ---------- */

/** `items` with an index, keyed by position.

    Keyed by position and not by content because a body can hold
    two identical paragraphs and a duplicate key crashes a
    LazyColumn. Position is the one thing about a block that is
    guaranteed unique. */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedKeyed(
    blocks: List<Block>,
    row: @Composable (Int, Block) -> Unit,
) {
    for ((index, block) in blocks.withIndex()) {
        item(key = "block-$index") { row(index, block) }
    }
}
