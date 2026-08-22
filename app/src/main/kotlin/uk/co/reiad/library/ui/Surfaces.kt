package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
) {
    val c = LocalReiad.current
    Box(
        modifier
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.CHIP, c, Corner.pill, ground = c.accent.copy(alpha = 0.12f))
            .padding(horizontal = Gap.s6, vertical = Gap.s3),
    ) {
        Text(
            text.uppercase(),
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
