package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Kind

/* ============================================================
   The six kinds, as things a screen can use.

   `Material.kt` draws glass. This names the six pieces of it a
   layout actually reaches for, and the naming is the part that
   matters: the test for which one a thing is, is not what it
   looks like. It is what happens when you press it.

   | | |
   | --- | --- |
   | a chip | latches |
   | a control | acts |
   | a card | takes you in |
   | a pane | holds other things |
   | a plate | is read |
   | a groove | is filled |

   `Rung` is the one that is not a kind: a row in a column of
   twenty, which is a `card`'s glass at `standing` 0. That is not
   a seventh material, it is the same material in a situation
   where nothing needs to announce itself, and it is the whole
   argument for `--standing` being an axis at all. A lone button
   has to look pressable because nothing else says it is; a row in
   a list does not, because the LIST is the affordance. Give every
   kind the same rest state and a rail becomes twenty boxes.
   ============================================================ */

/** A pane: it holds other things. A sheet, a bar, a panel. */
@Composable
fun Pane(
    modifier: Modifier = Modifier,
    corner: Dp = Corner.card,
    ground: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalReiad.current
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .material(Kind.PANE, c, corner, ground)
            .padding(horizontal = Gap.s8, vertical = Gap.s7),
        content = content,
    )
}

/** A card: it takes you in. Pressable, so it carries the light. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    corner: Dp = Corner.card,
    ground: Color? = null,
    lit: Glow? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalReiad.current
    val glow = lit ?: rememberGlow()
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .material(Kind.CARD, c, corner, ground, lit = { glow.lit })
            .follows(glow)
            .padding(horizontal = Gap.s8, vertical = Gap.s7),
        content = content,
    )
}

/** A control: it acts. One press, one thing happens. */
@Composable
fun Control(
    modifier: Modifier = Modifier,
    corner: Dp = Corner.pill,
    ground: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    Row(
        modifier
            .height(Gap.tap)
            .clip(RoundedCornerShape(corner))
            .material(Kind.CONTROL, c, corner, ground, lit = { glow.lit })
            .follows(glow)
            .padding(horizontal = Gap.s7),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The press ANSWERED in the glass itself: the surface gives a
    little under the finger and springs back when it lifts, the
    way the bar's thumb swells. Feedback rather than decoration,
    so it stays under reduced motion; what reduced motion turns
    off is the sway and the jiggle, things that move by
    themselves. */
@Composable
fun Modifier.pressGives(interaction: androidx.compose.foundation.interaction.InteractionSource): Modifier {
    val held by interaction.collectIsPressedAsState()
    val give by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (held) 0.965f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.55f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
        ),
        label = "give",
    )
    return this.graphicsLayer {
        scaleX = give
        scaleY = give
    }
}

/** The site's own four kinds, named for what a button IS rather
    than for how it looks. The ladder between them is LOUDNESS
    rather than importance: a solid is the one action a screen is
    for, a soft sits on a panel, a ghost acts without claiming
    ground, and a quiet is a word that happens to be pressable. */
enum class ButtonKind { SOLID, SOFT, GHOST, QUIET }

/**
 * A control with a label on it, which is what a button is.
 *
 * ONE BUTTON, the way `ui/button.tsx` is one on the site and
 * `ui/Field.kt` is one box here. There were three: this, with
 * two states; nineteen bare `Control(Modifier.clickable(...))`
 * sites, each a hand-made soft button with no rim, no minimum
 * width and its own idea of case and colour; and three latches
 * built the same way again. The clickable on those sat OUTSIDE
 * the pill clip, so the press ripple bled square corners on
 * every one.
 *
 * `Control` is the surface and this is the thing you press. The
 * difference matters because the material's lit edge is not
 * enough on its own at this size: a control on a pane of the same
 * glass reads as a label until it has a rim, and the site draws
 * one.
 *
 * `pressed` is the latch, and it is a third axis on purpose: a
 * thing that is ON is the accent whatever its kind, which is the
 * site's `aria-pressed` rule, and the semantics say selected so a
 * screen reader hears the state the eye sees.
 *
 * A Bangla label is detected rather than declared: it takes the
 * Bangla face and skips the uppercase, which is a no-op on Bangla
 * anyway. A flag would be forgotten on exactly the labels that
 * need it.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.GHOST,
    icon: String? = null,
    /** The whole row, for the account-page shape: one action on
        its own line. */
    wide: Boolean = false,
    /** Non-null makes this a latch: on is the accent, off is the
        kind, and the semantics carry the state. */
    pressed: Boolean? = null,
    /** The ink, where it is not the accent: a danger action. The
        KIND stays the loudness; this is only the colour. */
    tint: Color? = null,
    /** A control that cannot act YET, beside the box that will
        make it able: a Send next to an empty email. Anything
        else that cannot act should be absent rather than
        disabled, which is the board strip's own rule. */
    enabled: Boolean = true,
    description: String? = null,
) {
    val c = LocalReiad.current
    val on = enabled && (pressed == true || (pressed == null && kind == ButtonKind.SOLID))
    val ink = when {
        !enabled -> c.inkSoft
        on -> c.paper
        else -> tint ?: c.accent
    }
    /* TRANSPARENT is said out loud for the two quiet kinds,
       because `null` means "the material's own glass": left null,
       ghost and quiet drew the same ground as soft and the three
       were one kind in four names. The button sheet is what
       showed it, on its first render. */
    val ground = when {
        !enabled -> c.paperSunk
        on -> tint ?: c.accent
        kind == ButtonKind.SOFT -> c.panel
        else -> Color.Transparent
    }
    val bangla = label.any { it in 'ঀ'..'৿' }
    val touch = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Control(
        modifier
            .pressGives(touch)
            /* A MINIMUM WIDTH, not only a height: a short label
               is a target too narrow in one direction. SAVE came
               to 34dp and NOT NOW to 36, both right in a
               screenshot and wrong under a thumb, which is why
               `ReachTest` walks the semantics tree rather than
               looking. */
            .widthIn(min = Gap.tap)
            .then(if (wide) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(Corner.pill))
            .then(
                if (on || !enabled || kind != ButtonKind.GHOST) Modifier
                else Modifier.border(
                    /* The accent at half strength rather than the
                       hairline: a ghost's whole claim to being a
                       button is its rim, and the hairline
                       disappears into a dark ground. */
                    1.dp,
                    (tint ?: c.accent).copy(alpha = 0.45f),
                    RoundedCornerShape(Corner.pill),
                ),
            )
            .clickable(
                interactionSource = touch,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = if (pressed != null) Role.Checkbox else Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics {
                if (description != null) contentDescription = description
                if (pressed != null) selected = pressed
            },
        ground = ground,
    ) {
        if (wide) Spacer(Modifier.weight(1f))
        if (icon != null) {
            Icon(icon, size = 15.dp, tint = ink)
            Spacer(Modifier.width(Gap.s4))
        }
        Text(
            if (bangla) label else label.uppercase(),
            style = if (bangla) {
                MaterialTheme.typography.labelLarge.copy(fontFamily = Faces.bengali)
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = ink,
            maxLines = 1,
        )
        if (wide) Spacer(Modifier.weight(1f))
    }
}

/**
 * A tap target, never smaller than the site's own `--tap`.
 *
 * A chip is 29dp tall, which is right: it is a small mark that
 * says what something is. When one is also PRESSABLE the mark and
 * the target stop being the same thing, and the target is the one
 * with a minimum. The language switch on the stock check and the
 * seven calculator chips were all 28 by 29, well under both the
 * site's 44 and Android's 48, and nothing could see it: they are
 * the right size in a screenshot and the wrong size under a
 * thumb.
 *
 * The child keeps its own size and is centred, so this changes
 * what a finger can hit and not what a reader sees.
 */
@Composable
fun Tap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    label: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .sizeIn(minWidth = Gap.tap, minHeight = Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .clickable(role = role, onClick = onClick)
            .then(
                if (label == null) Modifier
                else Modifier.semantics { contentDescription = label },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A row in a column of rows.

    The same glass a card is, standing on nothing, which is what
    stops a ladder of forty lessons reading as forty boxes. It
    still LIGHTS under a finger: how a thing answers you is a
    different question from how much it announces itself, and a
    row that lies flat until you reach it is the whole idea. */
@Composable
fun Rung(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    Row(
        modifier
            .fillMaxWidth()
            /* A rung is usually the target itself: the menu's
               rows, a ladder's lessons. At `Gap.s5` of padding
               around one line it came to 40dp, which is under
               both the site's 44 and Android's 48 and is
               invisible in a screenshot. */
            .heightIn(min = Gap.tap)
            .clip(RoundedCornerShape(Corner.field))
            .material(
                kind = Kind.CARD,
                colours = c,
                corner = Corner.field,
                /* Flush in a column, so no ground of its own
                   either: the page shows through and only the
                   light arrives. */
                ground = Color.Transparent,
                lit = { glow.lit },
            )
            .follows(glow)
            .padding(horizontal = Gap.s6, vertical = Gap.s5),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A plate: it is read, never pressed. A statistic, a key figure,
    a quiet aside. The same weave and the same lit edge as
    everything else, and a STILL light: `--glow-w: 0` is how the
    site says that, and it is also what stops the glow module
    tracking it. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    corner: Dp = Corner.field,
    ground: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalReiad.current
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            /* The PANEL, never `paperSunk`: sunk is the GROOVE's
               ground, a channel cut in, and a plate wearing it
               reads as a hole in whatever holds it. The glass
               sheet is what showed it, worst as a plate inside a
               pane, where the statistic sat in a dark slot like
               something had been removed. A plate is a slab
               RESTING on the surface, so it takes the quietest
               raised ground there is and lets the material's own
               edge say the rest. */
            .material(Kind.PLATE, c, corner, ground = ground ?: c.panel)
            .padding(horizontal = Gap.s7, vertical = Gap.s6),
        content = content,
    )
}

/** A groove: a channel cut in, waiting to be filled.

    Every meter and every progress track on the site is this one
    shape, and it is the inverse of the other five: the light in
    it runs the other way up, the near wall in shadow at the top
    and the far wall catching the light at the bottom.

    The fill is NOT given an edge of its own. It is what is IN the
    channel, and a cut edge on it would draw a channel inside a
    channel, which is one of the four arguments in the site's
    `NOT_GLASS` list. */
@Composable
fun Groove(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val c = LocalReiad.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.GROOVE, c, Corner.pill, ground = c.paperSunk),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(Corner.pill))
                .background(c.accent),
        )
    }
}

/** A chip: a small latching label.

    What makes it a chip is that it LATCHES, not that it is small,
    so this draws one and the caller decides whether pressing it
    does anything. A chip that goes nowhere is not a link. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    /** A filled dot before the word, which is the site's `.gt-live`:
        the one mark that says a number on the other side of it is
        arriving now rather than being remembered. */
    live: Boolean = false,
) {
    val c = LocalReiad.current
    Row(
        modifier
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.CHIP, c, Corner.pill, ground = c.accent.copy(alpha = 0.12f))
            .padding(horizontal = Gap.s6, vertical = Gap.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(Corner.pill))
                    .background(tone ?: c.accent),
            )
            Spacer(Modifier.width(Gap.s4))
        }
        Text(
            /* Bangla has no case, and `uppercase()` on a Bengali
               string is a no-op that costs an allocation. Said
               here rather than at the call sites, because the
               chip is the one place on this site that upper-cases
               a word a reader wrote. */
            if (isBangla(text)) text else text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tone ?: c.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The accent rail down a card's left edge, which is how the site
    says "this one goes somewhere". */
@Composable
fun AccentRail(modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Box(
        modifier
            .width(3.dp)
            .clip(RoundedCornerShape(Corner.pill))
            .background(c.accent),
    )
}

/** Anything at all, as any of the six. For the places a layout
    needs glass without one of the shapes above. */
@Composable
fun Glass(
    kind: Kind,
    modifier: Modifier = Modifier,
    corner: Dp = Corner.card,
    ground: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .material(kind, c, corner, ground, lit = { glow.lit })
            .then(if (kind.follows) Modifier.follows(glow) else Modifier),
        content = content,
    )
}
