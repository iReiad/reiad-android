package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/* ============================================================
   The material, as far as a first screen needs it.

   The site has six kinds of glass and each is four numbers:
   depth, polish, clarity and standing. What a reader can tell
   apart is not the numbers, it is what happens when they press:
   a chip latches, a control acts, a card takes you in, a pane
   holds other things, a plate is read, a groove is filled.

   So these are the kinds by name, with the two properties a flat
   renderer can honestly carry, the ground and the edge. The lit
   edge, the pointer glow and the dispersion in the rim are the
   next change, not this one, and calling them done while they are
   absent is the failure the site's own notes keep returning to.

   `standing` is why a row in a list draws no box: a lone button
   has to look pressable because nothing else says it is, and a
   row in a column of twenty does not, because the LIST is the
   affordance.
   ============================================================ */

/** A card: it takes you somewhere, or it holds a reading. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    accented: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalReiad.current
    Column(
        modifier
            .clip(RoundedCornerShape(Corner.card))
            .background(c.panel)
            .border(
                width = 1.dp,
                color = if (accented) c.accentLine else c.hairline,
                shape = RoundedCornerShape(Corner.card),
            )
            .padding(horizontal = Gap.s8, vertical = Gap.s7),
        content = content,
    )
}

/** A row in a column of rows. Standing 0: no box of its own. */
@Composable
fun Rung(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Gap.s5),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A plate: read, never pressed. The statistic, the key figure,
    the quiet aside. */
@Composable
fun Plate(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalReiad.current
    Column(
        modifier
            .clip(RoundedCornerShape(Corner.field))
            .background(c.paperSunk)
            .padding(horizontal = Gap.s7, vertical = Gap.s6),
        content = content,
    )
}

/** A groove: a channel cut in, waiting to be filled. The site's
    meters and progress tracks are all this one shape. */
@Composable
fun Groove(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 6.dp,
) {
    val c = LocalReiad.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Corner.pill))
            .background(c.hairline.copy(alpha = 0.45f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(Corner.pill))
                .background(c.accent)
        )
    }
}

/** A chip: a small latching label. The TAG is what it does, not
    the class, so this draws one and the caller decides whether it
    is pressable. */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
) {
    val c = LocalReiad.current
    val ink = tone ?: c.accent
    Box(
        modifier
            .clip(RoundedCornerShape(Corner.pill))
            .background(c.accentSoft)
            .padding(horizontal = Gap.s6, vertical = Gap.s3)
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = ink,
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
            .background(c.accent)
    )
}
