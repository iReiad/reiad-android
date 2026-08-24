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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** One widget on the board, wearing its arranging dress when the
    board is being arranged.

    ---- the shape is a phone's own ----

    It was a strip of four controls above every widget, and the
    report on it was one word: congested. A home screen already
    taught everyone the other shape, so this is that: the widget
    itself leans and sways a little (the jiggle, which is what
    says "these are loose now"), the whole surface is the drag
    handle, and the only controls ON it are the two that cannot
    be a gesture: remove, and the size step. Move-up and
    move-down still exist for a switch or a screen reader, as
    custom accessibility actions on the frame rather than as
    44dp of drawn chrome per widget.

    A tap while arranging goes NOWHERE: the eater under the drag
    consumes it, because a board in jiggle mode is for moving
    things, and opening a card mid-arrange is the misfire a
    reader cannot undo. */
@Composable
fun WidgetFrame(
    modifier: Modifier = Modifier,
    kind: WidgetKind,
    placed: Placed,
    arranging: Boolean,
    first: Boolean,
    last: Boolean,
    lang: String,
    /** Motion is real for this reader. The jiggle is decoration
        and decoration is the first thing reduced motion means. */
    moving: Boolean = true,
    /** Out of the board's plane: under a finger, or still
        gliding into the slot it was dropped on. It is drawn
        proud and above its neighbours for both. */
    lifted: Boolean = false,
    /** How far off its slot to draw it, read at LAYER time
        rather than in composition, because it changes every
        frame a finger is moving and a composition that read it
        would recompose the whole widget every one of them. */
    shift: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset.Zero },
    onUp: () -> Unit,
    onDown: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    /** Pick it up and move it. Installed on the CELL, over the
        widget and under the badges, and deliberately NOT inside
        the layer that moves: `BoardDrag` derives where the card
        should be from where the finger is inside a cell that
        holds still, and a handle that travelled with the card
        would be measuring itself. */
    handle: Modifier = Modifier,
    body: @Composable () -> Unit,
) {
    if (!arranging) {
        Box(modifier) { body() }
        return
    }

    val name = kind.name(lang)
    val resize = kind.other(placed.size)

    /* The sway. A slow lean, out of phase with its neighbours by
       the widget's own name so the board never marches in step,
       held STILL on the carried one: the thing in the hand is
       already answering the finger, and a wobble on top of a
       drag reads as the drag slipping. */
    val sway = rememberInfiniteTransition(label = "jiggle")
    val lean by sway.animateFloat(
        initialValue = -0.4f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 340 + (placed.id.hashCode().mod(5)) * 30,
                easing = androidx.compose.animation.core.EaseInOutSine,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lean",
    )

    /* One number for the whole lift, so the rise, the shadow and
       the fade of the jiggle all arrive together and leave
       together rather than as three separate opinions. */
    val rise = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (lifted) 1f else 0f,
        animationSpec = tween(uk.co.reiad.library.core.Motion.QUICK_MS),
        label = "rise",
    )

    Box(
        modifier
            /* What a screen reader can do here, without any of it
               being drawn: the frame is one node whose actions
               are the moves. The drawn controls are only the two
               that cannot be a gesture. */
            .semantics {
                contentDescription = "$name: ধরে সরান"
                customActions = buildList {
                    if (!first) add(CustomAccessibilityAction("উপরে নিন") { onUp(); true })
                    if (!last) add(CustomAccessibilityAction("নিচে নামান") { onDown(); true })
                }
            },
    ) {
        /* THE PICTURE, which is the only thing that moves. */
        Box(
            Modifier
                .graphicsLayer {
                    val by = shift()
                    translationX = by.x
                    translationY = by.y
                    /* A card in the hand is off the board:
                       lifted, and slightly proud of the rest so
                       it is obvious which one is moving. */
                    val lift = rise.value
                    val grow = 1f + 0.03f * lift
                    scaleX = grow
                    scaleY = grow
                    shadowElevation = 14f * lift
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Corner.card)
                    clip = false
                    alpha = if (lift > 0f) 1f else 0.9f
                    /* The jiggle fades out as the card rises, so
                       a widget being carried is steady in the
                       hand while the loose ones keep wobbling. */
                    if (moving) rotationZ = lean * (1f - lift)
                },
        ) { body() }

        /* The handle, over the widget and under the badges: while
           the board is loose a tap must not open a card, and the
           same surface carries the drag, which is how "hold
           anywhere and move it" is literally true. */
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(placed.id) { detectTapGestures { } }
                .then(handle),
        )

        /* Two glass badges on the top edge, the pair a phone
           taught: take it off, and step its size. They ride the
           CELL rather than the picture, so a card being carried
           does not drag its own controls across the board. */
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(Gap.s2)
                /* In the layer, so a card being picked up does
                   not recompose its own controls sixty times on
                   the way up. */
                .graphicsLayer { alpha = 1f - rise.value },
            horizontalArrangement = Arrangement.spacedBy(Gap.s2),
        ) {
            if (resize != null) {
                Badge(
                    icon = if (resize == WidgetSize.SMALL) "shrink" else "grow",
                    label = "$name: " + when (resize) {
                        WidgetSize.SMALL -> "ছোট করুন"
                        WidgetSize.TALL -> "লম্বা করুন"
                        WidgetSize.WIDE -> "চওড়া করুন"
                    },
                    onClick = onResize,
                )
            }
            Badge(icon = "close", label = "$name: সরিয়ে দিন", onClick = onRemove)
        }
    }
}

/** One round control riding a widget's corner while the board is
    loose. 32dp of glass drawn, 44dp of target underneath. */
@Composable
private fun Badge(icon: String, label: String, onClick: () -> Unit) {
    val c = LocalReiad.current
    Box(
        Modifier
            .size(Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Corner.pill))
                .material(Kind.CONTROL, c, Corner.pill, ground = c.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size = 15.dp, tint = c.ink)
        }
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
