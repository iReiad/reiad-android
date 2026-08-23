package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import uk.co.reiad.library.core.Kind

/* ============================================================
   Two kinds of card, and a reader can tell them apart.

   The site's own note is the argument, and it is worth restating
   because it is the whole reason these are two components rather
   than one with a flag. `.cell` was one card doing five jobs: a
   link to an article, a statistic, a paragraph about a service, a
   calculator, and a heading with bullets under it. All five
   looked the same, so the only way to find out which of them
   would take you somewhere was to move the mouse.

   On a handset there is no mouse to move, which makes it worse
   rather than better: the only way to find out is to press and
   see whether anything happens.

   | | |
   | --- | --- |
   | `GoCard` | takes you somewhere. Accent rail, arrow, the action written out, and the light |
   | `InfoCard` | tells you something and is the end of the road. Dashed edge, quieter ground, no arrow, no light |
   | `SoonCard` | promised and not written |

   Neither can be the other by accident, and the light is the
   fourth mark rather than the only one: a card that answers you
   has one and a card that does not has none, which is the same
   distinction the rail and the arrow already draw, drawn once
   more by the one thing a finger can test.
   ============================================================ */

/** The disc: the rounded square an icon sits in.

    `.gt-disc`, and the BORDER is not decoration. A tinted square
    with no edge on a tinted card is a smudge; the accent at 24
    per cent is what makes it a tile sitting on the card. */
@Composable
fun Disc(size: Dp = 34.dp, corner: Dp = Corner.field, art: @Composable () -> Unit) {
    val c = LocalReiad.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(c.accent.copy(alpha = 0.12f))
            .border(1.dp, c.accent.copy(alpha = 0.24f), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
        content = { art() },
    )
}

/** The head of a card: the icon tile and the little mono chip. */
@Composable
private fun CardTop(chip: String?, art: (@Composable () -> Unit)?) {
    if (chip == null && art == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (art != null) {
            Disc { art() }
            Spacer(Modifier.width(Gap.s5))
        }
        if (chip != null) Chip(chip)
    }
    Spacer(Modifier.height(Gap.s5))
}

@Composable
private fun CardBody(title: String, dek: String?, feature: Boolean = false) {
    val c = LocalReiad.current
    val base = if (isBangla(title)) BanglaTitle else MaterialTheme.typography.titleMedium
    Text(
        title,
        /* The title decides its own face. A ladder of lessons has
           Bangla titles and English stage labels in the same
           column, so asking the page's language would set half of
           them in the wrong one. */
        style = if (feature) {
            base.copy(fontSize = 26.sp, lineHeight = if (isBangla(title)) 38.sp else 32.sp)
        } else {
            base
        },
        color = c.ink,
    )
    if (dek != null) {
        Spacer(Modifier.height(Gap.s4))
        Text(
            dek,
            style = if (isBangla(dek)) BanglaBody.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
            ) else MaterialTheme.typography.bodyMedium,
            color = c.inkSoft,
        )
    }
}

/** A card that takes you somewhere, or does something.

    `go` is the action written out, and it is not decoration: it
    is the sentence that says what pressing this does, which is
    the difference between a card and a paragraph in a box. */
@Composable
fun GoCard(
    title: String,
    go: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    dek: String? = null,
    chip: String? = null,
    done: Boolean = false,
    sway: Sway? = null,
    art: (@Composable () -> Unit)? = null,
    /** The featured form: taller, a bigger title, and one
        oversized drawing in the bottom corner at a watermark's
        opacity. `.gate-feat` and `.gt-bg`. One card on a screen
        gets this; two of them is a poster rather than a page. */
    feature: Boolean = false,
    /** The name of the drawing behind a featured card, which is
        usually the same icon the disc carries at eight times the
        size and eight per cent of the ink. */
    watermark: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    val touch = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressGives(touch)
            .then(if (sway != null) Modifier.leaning(sway) else Modifier)
            .clip(RoundedCornerShape(Corner.card))
            .material(
                kind = Kind.CARD,
                colours = c,
                corner = Corner.card,
                /* The wash that arrives under a finger, out of the
                   corner the arrow is in. It is the card's OWN
                   gradient, which is exactly what `--surface-image`
                   exists for: the material paints the background,
                   so a card that painted its own would lose it. */
                /* TWO washes, and the resting one is what the app
                   was missing.

                   `.gate-tile` carries `linear-gradient(150deg,
                   accent-soft, panel 55%)` AT REST, which is what
                   makes a card on this site look like a card of
                   its section rather than a grey box with a
                   coloured line beside it. The radial out of the
                   arrow's corner is `::after`, and it only
                   arrives under a finger. */
                wash = { now ->
                    val rest = Brush.linearGradient(
                        0f to c.accentSoft,
                        0.55f to c.panel,
                        start = Offset.Zero,
                        end = Offset(0f, Float.POSITIVE_INFINITY),
                    )
                    if (now.a <= 0f) rest
                    else Brush.radialGradient(
                        0f to c.accent.copy(alpha = 0.14f * now.a),
                        0.6f to Color.Transparent,
                    )
                },
                lit = { glow.lit },
            )
            .follows(glow)
            .clickable(
                interactionSource = touch,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onOpen,
            ),
    ) {
        /* The rail: three pixels of the card's own accent down the
           left edge, and the one mark every interactive card here
           carries and no informational one does. */
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(c.accent),
            )
        }
        /* The watermark, UNDER the words and over the wash. It is
           allowed off the bottom-right corner on purpose: a
           drawing that fits inside the card is a picture, and one
           that runs off the edge is a texture. */
        if (feature && watermark != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clipToBounds(),
            ) {
                Icon(
                    watermark,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 22.dp, y = 30.dp)
                        .rotate(-8f)
                        .alpha(0.09f),
                    size = 150.dp,
                    tint = c.accent,
                )
            }
        }
        Column(
            if (feature) {
                Modifier.padding(start = 26.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
            } else {
                Modifier.padding(start = 22.dp, top = 18.dp, end = 20.dp, bottom = 18.dp)
            },
        ) {
            CardTop(chip, art)
            CardBody(title, dek, feature)
            content()
            Spacer(Modifier.height(Gap.s6))
            Row(verticalAlignment = Alignment.CenterVertically) {
                /* Mono, uppercase and letterspaced: `.gt-go`. The
                   site says what pressing this does in the one
                   face it uses for machine words, which is what
                   stops the line reading as another sentence of
                   the paragraph above it. */
                Text(
                    go.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.08.em),
                    color = c.accent,
                )
                Spacer(Modifier.width(Gap.s4))
                Icon("arrow", size = 15.dp, tint = c.accent)
            }
        }
        if (done) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(Corner.pill))
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", style = MaterialTheme.typography.labelMedium, color = c.panel)
            }
        }
    }
}

/** A card that tells you something and is the end of the road.

    A dashed edge, a quieter ground, no arrow, no rail and no
    light. It is not pressable, so it has no `clickable` to
    forget to leave off: the type is the guarantee. */
@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    dek: String? = null,
    chip: String? = null,
    art: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalReiad.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.card))
            .material(Kind.PLATE, c, Corner.card, ground = c.panel.copy(alpha = 0.55f))
            .dashedEdge(c.hairline, Corner.card),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            CardTop(chip, art)
            CardBody(title, dek)
            content()
        }
    }
}

/** A card for something promised and not written.

    Not a `GoCard` that happens to do nothing, for the same reason
    a chip that goes nowhere is not a link: a thing that looks
    pressable and is not is a worse answer than a thing that says
    it is not ready. */
@Composable
fun SoonCard(
    title: String,
    modifier: Modifier = Modifier,
    dek: String? = null,
    soon: String = "আসছে",
) {
    val c = LocalReiad.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.card))
            .dashedEdge(c.hairline, Corner.card),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Chip(soon)
            Spacer(Modifier.height(Gap.s5))
            Text(
                title,
                style = if (isBangla(title)) BanglaTitle else MaterialTheme.typography.titleMedium,
                color = c.ink.copy(alpha = 0.72f),
            )
            if (dek != null) {
                Spacer(Modifier.height(Gap.s4))
                Text(
                    dek,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.inkSoft.copy(alpha = 0.72f),
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

/** A deck: cards in a column, at the site's own gap. */
@Composable
fun Deck(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Gap.s7), content = content)
}

/**
 * The slim form: one line tall, a handle rather than a card.
 *
 * `.gate-slim`. The site's side column is these, and a handset
 * wants them more than a desktop does: a list of eight places to
 * go should not be eight cards of prose, because then the page is
 * a scroll of paragraphs and none of them is the one you wanted.
 *
 * It is still a `GoCard` by every rule that matters: the rail,
 * the disc, the chip, the arrow and the light. What it drops is
 * the dek, which is the part a handle has no room for.
 */
@Composable
fun RowCard(
    title: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    chip: String? = null,
    live: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = LocalReiad.current
    val glow = rememberGlow()
    val touch = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .pressGives(touch)
            .clip(RoundedCornerShape(Corner.pill))
            .material(
                kind = Kind.CARD,
                colours = c,
                corner = Corner.pill,
                wash = { now ->
                    val rest = Brush.linearGradient(
                        0f to c.accentSoft,
                        0.55f to c.panel,
                        start = Offset.Zero,
                        end = Offset(0f, Float.POSITIVE_INFINITY),
                    )
                    if (now.a <= 0f) rest
                    else Brush.radialGradient(
                        0f to c.accent.copy(alpha = 0.14f * now.a),
                        0.6f to Color.Transparent,
                    )
                },
                lit = { glow.lit },
            )
            .follows(glow)
            .clickable(
                interactionSource = touch,
                indication = androidx.compose.foundation.LocalIndication.current,
                role = Role.Button,
                onClick = onOpen,
            ),
    ) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(c.accent))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Disc(30.dp) { Icon(icon, size = 16.dp) }
                Spacer(Modifier.width(Gap.s5))
            }
            if (chip != null) {
                Chip(chip, live = live)
                Spacer(Modifier.width(Gap.s5))
            }
            Text(
                title,
                Modifier.weight(1f),
                style = if (isBangla(title)) {
                    BanglaTitle.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize)
                } else {
                    MaterialTheme.typography.titleSmall
                },
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (trailing != null) {
                Spacer(Modifier.width(Gap.s5))
                trailing()
            } else {
                Spacer(Modifier.width(Gap.s5))
                Icon("arrow", size = 16.dp, tint = c.accent)
            }
        }
    }
}
