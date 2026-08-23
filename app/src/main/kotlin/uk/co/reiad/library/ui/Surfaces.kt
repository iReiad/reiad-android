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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * A control with a label on it, which is what a button is.
 *
 * `Control` is the surface and this is the thing you press. The
 * difference matters because the material's lit edge is not
 * enough on its own at this size: a control on a pane of the same
 * glass reads as a label until it has a rim, and the site draws
 * one. It shipped without, five times over, and every one of them
 * looked like text somebody had coloured green.
 *
 * `filled` is a latch rather than an emphasis: a thing that is ON
 * is the accent, a thing that ACTS is the rim.
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    filled: Boolean = false,
    description: String? = null,
) {
    val c = LocalReiad.current
    val ink = if (filled) c.paper else c.accent
    Control(
        modifier
            /* A MINIMUM WIDTH, not only a height.

               `Control` gives this its 44dp height and nothing
               gave it a width, so a short label is a target too
               narrow in one direction: SAVE came to 34dp and NOT
               NOW to 36. Both are the right size in a screenshot
               and the wrong size under a thumb, which is why
               `ReachTest` walks the semantics tree rather than
               looking. It was fixed once on ONE button, in the
               account's own file, with a comment saying exactly
               this; here is where it belongs. */
            .widthIn(min = Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .then(
                if (filled) Modifier
                else Modifier.border(1.dp, c.hairline, RoundedCornerShape(Corner.pill)),
            )
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (description == null) Modifier
                else Modifier.semantics { contentDescription = description },
            ),
        ground = if (filled) c.accent else null,
    ) {
        if (icon != null) {
            Icon(icon, size = 15.dp, tint = ink)
            Spacer(Modifier.width(Gap.s4))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
        )
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
            .material(Kind.PLATE, c, corner, ground = ground ?: c.paperSunk)
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
