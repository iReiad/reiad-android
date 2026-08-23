package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.Block
import uk.co.reiad.library.core.CalloutKind
import uk.co.reiad.library.core.Inline
import uk.co.reiad.library.core.checkpointId
import androidx.compose.foundation.layout.widthIn

/* ============================================================
   A parsed body, drawn.

   `BodyParser` in core turned the sanitised HTML into blocks and
   is tested against real lessons. This only has to draw them, and
   every branch is one of the classes the site's own article layer
   styles.

   The one rule worth restating: a block the parser did not know
   still arrives, carrying its words, and is drawn as plain text.
   Dropping it would lose prose silently, which is exactly what a
   strict renderer would have done to the `<b>` in
   /money/basics-1/share.
   ============================================================ */

/** How a checklist in this body behaves.

    Absent, and a checklist is a list. Present, and every item of
    every checklist in the body is a CHECKPOINT: a thing the
    reader can tick, filed `<lesson id>#<n>`.

    The site invents no markup for this and neither does the app.
    `.checklist` is an article block that has been in the
    stylesheet and in both sanitisers since the Studio was
    written, so every checklist in a school lesson becomes
    checkpoints and a checklist anywhere else stays a list. That
    is why this is a parameter of the renderer rather than a new
    kind of block. */
data class Checkpoints(
    val lessonId: String,
    val done: Set<String>,
    /** Which number each checklist's first item takes, by path.

        Computed once in core by `checkpointBases`, because the
        numbering runs across the whole lesson in document order
        and a renderer counting as it draws would depend on the
        order Compose happens to compose in. */
    val bases: Map<String, Int>,
    val onToggle: (String) -> Unit,
)

@Composable
fun BodyView(
    blocks: List<Block>,
    modifier: Modifier = Modifier,
    checkpoints: Checkpoints? = null,
    /** This run's position in the whole body, dotted for nesting.

        A slice of one block drawn on its own still has to know
        where it sits, because a checkpoint's number is its
        position in the LESSON. A page that renumbered from zero
        per slice would forget somebody's ticks. */
    path: String = "",
) {
    /* THE READER'S MEASURE, and it is a maximum rather than a
       width: a column narrower than the setting stays as it is.
       The site says the same thing with `max-width: var(--measure)`
       and it matters for the same reason: a line of prose running
       the whole width of an unfolded foldable is a line the eye
       loses its place in. */
    val ch = measureCh(LocalMeasure.current.ch, MaterialTheme.typography.bodyLarge)
    Column(modifier.widthIn(max = ch)) {
        for ((index, block) in blocks.withIndex()) {
            BlockView(block, checkpoints, if (path.isEmpty()) "$index" else "$path.$index")
            /* A heading opens a section, so the air belongs ABOVE
               it rather than under the paragraph it follows.

               One gap between every pair of blocks put a
               subheading the same distance from the prose it
               ends and the prose it introduces, which reads as
               another line of the paragraph above rather than as
               a new section. The site says the same with
               `margin-block: 28px 10px` on an article's h2. */
            val nextIsHeading = blocks.getOrNull(index + 1) is Block.Heading
            Spacer(Modifier.height(if (nextIsHeading) Gap.s10 else Gap.s7))
        }
    }
}

@Composable
private fun BlockView(
    block: Block,
    checkpoints: Checkpoints? = null,
    path: String = "",
) {
    val c = LocalReiad.current
    when (block) {
        /* A heading is bigger than the prose under it and it is
           SPACED, both of which the site does and neither of
           which this did: `BanglaHeading` is 20sp against 17sp
           body, which at Bengali's leading is a line of prose in
           bold. An article's h2 there is 1.6rem, which is 27 of
           the site's pixels against a 17px body. */
        is Block.Heading -> Text(
            block.inlines.annotated(c.accent),
            style = when {
                isBangla(block.inlines.plain()) ->
                    if (block.level <= 2) {
                        BanglaHeading.copy(fontSize = 25.sp, lineHeight = 36.sp)
                    } else {
                        BanglaTitle
                    }
                block.level <= 2 ->
                    MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 31.sp)
                else -> MaterialTheme.typography.titleMedium
            },
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )

        /* Asked of the words rather than of the piece, because a
           piece is one language and its prose is not: a Bangla
           lesson quotes an English term and an English piece
           quotes a Bangla one. The face and the leading follow
           the line that needs them. */
        is Block.Paragraph -> Text(
            block.inlines.annotated(c.accent),
            style = bodyStyleFor(block.inlines),
            color = c.ink,
        )

        is Block.Quote -> Row(Modifier.fillMaxWidth()) {
            AccentRail(Modifier.height(intrinsicQuoteHeight))
            Spacer(Modifier.width(Gap.s6))
            Text(
                block.inlines.annotated(c.accent),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif),
                color = c.ink,
            )
        }

        is Block.Bullets -> Column {
            for (item in block.items) Marked("•", item, c.accent, c.ink)
        }

        is Block.Numbers -> Column {
            block.items.forEachIndexed { i, item -> Marked("${i + 1}.", item, c.accent, c.ink) }
        }

        /* A step is a numbered walk-through: the site draws the
           number in a disc rather than as a list marker. */
        is Block.Steps -> Column {
            block.items.forEachIndexed { i, item ->
                Row(Modifier.padding(vertical = Gap.s4)) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(Corner.pill))
                            .background(c.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.accent,
                        )
                    }
                    Spacer(Modifier.width(Gap.s6))
                    Text(item.annotated(c.accent), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                }
            }
        }

        /* Inside a school lesson these ARE the checkpoints. */
        is Block.Checklist -> Column {
            /* The number this list starts at, worked out in core
               over the whole lesson. Absent means this body has
               no checkpoints, so the list is a list. */
            val base = checkpoints?.bases?.get(path)
            block.items.forEachIndexed { n, item ->
                /* By POSITION and not by text, because prose gets
                   edited: a checkpoint that forgot itself over a
                   fixed typo is worse than one that stays put
                   when a line is reworded. */
                val marks = checkpoints
                val id = if (base == null || marks == null) null
                else checkpointId(marks.lessonId, base + n)
                val ticked = id != null && marks != null && id in marks.done
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (id != null && marks != null) {
                                Modifier.clickable(role = Role.Checkbox) { marks.onToggle(id) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = Gap.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(Corner.xs))
                            .then(
                                if (ticked) Modifier.background(c.accent)
                                else Modifier.border(1.5.dp, c.accent, RoundedCornerShape(Corner.xs))
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (ticked) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.paper,
                            )
                        }
                    }
                    Spacer(Modifier.width(Gap.s6))
                    Text(
                        item.annotated(c.accent),
                        style = if (isBangla(item.plain())) BanglaBody
                        else MaterialTheme.typography.bodyLarge,
                        color = if (ticked) c.inkSoft else c.ink,
                    )
                }
            }
        }

        is Block.Callout -> Callout(block, checkpoints, path)

        is Block.KeyFigures -> Column {
            for (figure in block.figures) {
                Plate(Modifier.padding(bottom = Gap.s5)) {
                    Text(
                        figure.value.annotated(c.accent),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.ink,
                    )
                    Text(
                        figure.caption.annotated(c.accent),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSoft,
                    )
                }
            }
        }

        is Block.Photo -> PhotoBlock(
            src = block.src,
            alt = block.alt,
            classes = block.classes,
            caption = block.caption.takeIf { it.isNotEmpty() }?.annotated(c.accent),
        )

        is Block.Table -> Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (block.head.isNotEmpty()) {
                Row {
                    for (cell in block.head) {
                        Text(
                            cell.annotated(c.accent),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.accent,
                            modifier = Modifier.width(160.dp).padding(Gap.s4),
                        )
                    }
                }
            }
            for (row in block.rows) {
                Row {
                    for (cell in row) {
                        Text(
                            cell.annotated(c.accent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.ink,
                            modifier = Modifier.width(160.dp).padding(Gap.s4),
                        )
                    }
                }
            }
        }

        Block.Rule -> Box(
            Modifier.fillMaxWidth().height(1.dp).background(c.hairline)
        )

        /* Words the parser could not place. They are still words. */
        is Block.Unknown -> Text(
            block.inlines.annotated(c.accent),
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink,
        )
    }
}

private val intrinsicQuoteHeight = 48.dp

/** The plain words of a run, for asking which language it is in. */
internal fun List<Inline>.plain(): String = buildString {
    fun walk(list: List<Inline>) {
        for (inline in list) when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Strong -> walk(inline.children)
            is Inline.Emphasis -> walk(inline.children)
            is Inline.Code -> walk(inline.children)
            is Inline.Sup -> walk(inline.children)
            is Inline.Sub -> walk(inline.children)
            is Inline.Link -> walk(inline.children)
            Inline.Break -> append(" ")
        }
    }
    walk(this@plain)
}

@Composable
private fun bodyStyleFor(inlines: List<Inline>) =
    if (isBangla(inlines.plain())) BanglaBody else MaterialTheme.typography.bodyLarge

@Composable
private fun Marked(mark: String, item: List<Inline>, accent: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = Gap.s3)) {
        Text(mark, style = MaterialTheme.typography.bodyLarge, color = accent)
        Spacer(Modifier.width(Gap.s5))
        Text(item.annotated(accent), style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

@Composable
private fun Callout(block: Block.Callout, checkpoints: Checkpoints? = null, path: String = "") {
    val c = LocalReiad.current
    val label = block.label.annotated(c.accent).text.ifBlank {
        when (block.kind) {
            CalloutKind.AT_A_GLANCE -> "In short"
            CalloutKind.SIDE_NOTE -> "Aside"
            CalloutKind.NOTE -> "Note"
            CalloutKind.EXAMPLE -> "Worked example"
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.card))
            .background(if (block.kind == CalloutKind.AT_A_GLANCE) c.paperSunk else c.accentSoft)
            .border(
                1.dp,
                if (block.kind == CalloutKind.AT_A_GLANCE) c.accentLine else c.hairline,
                RoundedCornerShape(Corner.card),
            )
            .padding(Gap.s8)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.accent)
        Spacer(Modifier.height(Gap.s4))
        for (inner in block.body) BlockView(inner)
    }
}

/* ---------- inlines ---------- */

/** One run of inline content as styled text. A link is drawn in
    the accent and underlined; the glossary link the money school
    uses is dotted on the site, which is a decoration Compose does
    not have, so it keeps the accent and the underline. */
private fun List<Inline>.annotated(accent: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString { appendInlines(this@annotated, accent) }

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlines(
    inlines: List<Inline>,
    accent: androidx.compose.ui.graphics.Color,
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlines(inline.children, accent)
            }
            is Inline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(inline.children, accent)
            }
            is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                appendInlines(inline.children, accent)
            }
            is Inline.Sup -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                appendInlines(inline.children, accent)
            }
            is Inline.Sub -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                appendInlines(inline.children, accent)
            }
            is Inline.Link -> withStyle(
                SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            ) {
                appendInlines(inline.children, accent)
            }
            Inline.Break -> append("\n")
        }
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyle(
    style: SpanStyle,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val index = pushStyle(style)
    block()
    pop(index)
}
