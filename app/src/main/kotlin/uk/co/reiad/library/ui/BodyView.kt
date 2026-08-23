package uk.co.reiad.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.widthIn
import uk.co.reiad.library.core.Block
import uk.co.reiad.library.core.CalloutKind
import uk.co.reiad.library.core.Inline
import uk.co.reiad.library.core.checkpointId

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

/** What pressing a link in a body does.

    A composition local rather than a parameter, because a link
    can be nine blocks deep in a callout inside a pattern box and
    threading a callback through every level is how one call site
    forgets. The default does nothing, which is what a render test
    wants; the app installs the real answer once, at the top.

    The href arrives EXACTLY as written, which for a term link
    inside a money lesson is relative: only the screen that knows
    the stage can resolve it, so resolution happens in the handler
    rather than here. */
val LocalOpenLink = staticCompositionLocalOf<(String) -> Unit> { {} }

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
    val open = LocalOpenLink.current
    when (block) {
        /* A heading is bigger than the prose under it and it is
           SPACED, both of which the site does and neither of
           which this did: `BanglaHeading` is 20sp against 17sp
           body, which at Bengali's leading is a line of prose in
           bold. An article's h2 there is 1.6rem, which is 27 of
           the site's pixels against a 17px body. */
        is Block.Heading -> Text(
            block.inlines.annotated(c.accent, open),
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
            block.inlines.annotated(c.accent, open),
            style = bodyStyleFor(block.inlines),
            color = c.ink,
        )

        is Block.Quote -> Row(Modifier.fillMaxWidth()) {
            AccentRail(Modifier.height(intrinsicQuoteHeight))
            Spacer(Modifier.width(Gap.s6))
            Text(
                block.inlines.annotated(c.accent, open),
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
                    Text(item.annotated(c.accent, open), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                }
            }
        }

        /* Inside a school lesson these ARE the checkpoints. */
        is Block.Checklist -> Column {
            /* The number this list starts at, worked out in core
               over the whole lesson. Absent means this body has
               no checkpoints, so the list is a list. */
            val base = checkpoints?.bases?.get(path)
            val touch = rememberTouch()
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
                                Modifier.clickable(role = Role.Checkbox) {
                                    touch.latch(!ticked)
                                    marks.onToggle(id)
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = Gap.s4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tickbox(ticked, c)
                    Spacer(Modifier.width(Gap.s6))
                    Text(
                        item.annotated(c.accent, open),
                        style = if (isBangla(item.plain())) BanglaBody
                        else MaterialTheme.typography.bodyLarge,
                        color = if (ticked) c.inkSoft else c.ink,
                    )
                }
            }
        }

        is Block.Callout -> Callout(block, checkpoints, path)

        is Block.Pattern -> PatternBlock(block, checkpoints, path)

        is Block.Sentences -> SentencesBlock(block)

        is Block.KeyFigures -> Column {
            for (figure in block.figures) {
                Plate(Modifier.padding(bottom = Gap.s5)) {
                    Text(
                        figure.value.annotated(c.accent, open),
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.ink,
                    )
                    Text(
                        figure.caption.annotated(c.accent, open),
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
            caption = block.caption.takeIf { it.isNotEmpty() }?.annotated(c.accent, open),
        )

        is Block.Table -> TableBlock(block)

        Block.Rule -> Box(
            Modifier.fillMaxWidth().height(1.dp).background(c.hairline)
        )

        /* Words the parser could not place. They are still words. */
        is Block.Unknown -> Text(
            block.inlines.annotated(c.accent, open),
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink,
        )
    }
}

private val intrinsicQuoteHeight = 48.dp

/** The tick itself. Filled when ticked, an outline when not, and
    the fill ARRIVES rather than appearing: the same 190ms the
    material's own light takes, because a mark a reader makes
    deserves at least as much answer as a surface they brush. */
@Composable
private fun Tickbox(ticked: Boolean, c: ReiadColours) {
    val fill = androidx.compose.animation.animateColorAsState(
        targetValue = if (ticked) c.accent else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(uk.co.reiad.library.core.Motion.IN_MS),
        label = "tick",
    )
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(Corner.xs))
            .background(fill.value)
            .border(1.5.dp, if (ticked) fill.value else c.accent, RoundedCornerShape(Corner.xs)),
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
}

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
    val open = LocalOpenLink.current
    Row(Modifier.padding(vertical = Gap.s3)) {
        Text(mark, style = MaterialTheme.typography.bodyLarge, color = accent)
        Spacer(Modifier.width(Gap.s5))
        Text(item.annotated(accent, open), style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

@Composable
private fun Callout(block: Block.Callout, checkpoints: Checkpoints? = null, path: String = "") {
    val c = LocalReiad.current

    /* The German school's merke is a rail, not a box with a
       label: `border-left: 3px solid var(--accent)` against the
       sunk paper, and `.warn` swaps the rail to the danger
       colour. The site gives it no label at all, so none is
       invented for it. */
    if (block.kind == CalloutKind.REMEMBER || block.kind == CalloutKind.CAUTION) {
        val rail = if (block.kind == CalloutKind.CAUTION) c.danger else c.accent
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(rail),
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topEnd = Corner.field, bottomEnd = Corner.field))
                    .background(c.paperSunk)
                    .padding(horizontal = Gap.s7, vertical = Gap.s6),
            ) {
                /* Inner blocks keep their place in the lesson, so
                   a checklist inside survives with its numbers. */
                block.body.forEachIndexed { i, inner ->
                    if (i > 0) Spacer(Modifier.height(Gap.s5))
                    BlockView(inner, checkpoints, if (path.isEmpty()) "$i" else "$path.$i")
                }
            }
        }
        return
    }

    val label = block.label.annotated(c.accent).text.ifBlank {
        when (block.kind) {
            CalloutKind.AT_A_GLANCE -> "In short"
            CalloutKind.SIDE_NOTE -> "Aside"
            CalloutKind.NOTE -> "Note"
            CalloutKind.EXAMPLE -> "Worked example"
            CalloutKind.REMEMBER, CalloutKind.CAUTION -> ""
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
        /* The path travels in, because a checkpoint inside a
           callout is a descendant of the LESSON: core numbers it
           in place and a renderer that dropped the path here drew
           the list as a list and forgot the reader's ticks. */
        block.body.forEachIndexed { i, inner ->
            if (i > 0) Spacer(Modifier.height(Gap.s5))
            BlockView(inner, checkpoints, if (path.isEmpty()) "$i" else "$path.$i")
        }
    }
}

/* ---------- the German school's furniture ---------- */

/** `.muster`: the pattern box. An accent border on the accent
    wash, the label in mono, the pattern itself at display size,
    and the why underneath — every number off the site's own
    `body.deutsch .muster` rules. */
@Composable
private fun PatternBlock(block: Block.Pattern, checkpoints: Checkpoints? = null, path: String = "") {
    val c = LocalReiad.current
    val open = LocalOpenLink.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Corner.card))
            .background(c.accentSoft)
            .border(1.dp, c.accent.copy(alpha = 0.55f), RoundedCornerShape(Corner.card))
            .padding(horizontal = Gap.s8, vertical = Gap.s7),
    ) {
        if (block.label.isNotEmpty()) {
            Text(
                block.label.plain().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = c.accent,
            )
            Spacer(Modifier.height(Gap.s5))
        }
        if (block.shape.isNotEmpty()) {
            Text(
                /* `.muster-shape i` is the accent with the italic
                   taken off: the site uses `<i>` inside a pattern
                   as a highlighter, not as emphasis. */
                block.shape.annotated(c.accent, open, emphasisInAccent = true),
                style = TextStyle(
                    fontFamily = if (isBangla(block.shape.plain())) Faces.bengali else Faces.sans,
                    fontWeight = FontWeight.Medium,
                    fontSize = 21.sp,
                    lineHeight = 32.sp,
                ),
                color = c.ink,
            )
            Spacer(Modifier.height(Gap.s5))
        }
        block.body.forEachIndexed { i, inner ->
            if (i > 0) Spacer(Modifier.height(Gap.s5))
            BlockView(inner, checkpoints, if (path.isEmpty()) "$i" else "$path.$i")
        }
    }
}

/** `.satz-list`: the example pairs. Each row is the sentence in
    the language being learnt over its Bangla meaning, and every
    other row sits on the sunk paper, which is what keeps eight
    examples legible as eight rather than as a wall. */
@Composable
private fun SentencesBlock(block: Block.Sentences) {
    val c = LocalReiad.current
    val open = LocalOpenLink.current
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s1)) {
        block.rows.forEachIndexed { i, row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Corner.field))
                    .then(if (i % 2 == 0) Modifier.background(c.paperSunk) else Modifier)
                    .padding(horizontal = Gap.s5, vertical = Gap.s4),
            ) {
                if (row.lead.isNotEmpty()) {
                    Text(
                        row.lead.annotated(c.accent, open),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = c.ink,
                    )
                }
                if (row.gloss.isNotEmpty()) {
                    Text(
                        row.gloss.annotated(c.accent, open),
                        style = if (isBangla(row.gloss.plain())) {
                            BanglaBody.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = c.inkSoft,
                    )
                }
            }
        }
    }
}

/* ---------- the table ---------- */

/** A table, on its own piece of glass.

    Three things the first rendering did not have, and each is the
    difference between a table and four columns of loose words:

    1. A SURFACE. The row of figures sat straight on the page with
       nothing saying where the table began or ended, so the third
       column simply fell off the edge of the screen. Now the
       whole thing sits on a plate and SCROLLS INSIDE it, so the
       cut-off edge reads as "more this way" rather than as a
       mistake.
    2. MEASURED columns. Every column was a flat 160dp, so ১ · ২ ·
       বাকি each took as much room as a paragraph. Now a column is
       as wide as the widest thing in it, up to a cap, and the
       whole table stretches to fill its plate when it is narrow.
    3. The site's own ruling: a hairline under every row, the head
       in the accent's mono caps, which is `:is(.article) th, td`
       verbatim. */
@Composable
private fun TableBlock(block: Block.Table) {
    val c = LocalReiad.current
    val open = LocalOpenLink.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val headStyle = MaterialTheme.typography.labelMedium
    val cellBase = MaterialTheme.typography.bodyMedium
    val cellBangla = BanglaBody.copy(
        fontSize = cellBase.fontSize,
        lineHeight = cellBase.fontSize * 1.8f,
    )
    fun styleFor(text: String, head: Boolean): TextStyle = when {
        head -> headStyle
        isBangla(text) -> cellBangla
        else -> cellBase
    }

    val columns = maxOf(block.head.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        /* The width a column may take, and the width the table
           has to fill. Measured in the text's own styles so the
           answer follows the reader's type size. */
        val innerPad = Gap.s6
        val cellPad = Gap.s5
        val viewport = maxWidth - innerPad * 2

        val widths = remember(block, viewport, density.fontScale, headStyle, cellBase) {
            val cap = with(density) { 250.dp.toPx() }
            val floor = with(density) { 44.dp.toPx() }
            val pad = with(density) { (cellPad * 2).toPx() }
            val want = FloatArray(columns)
            fun consider(cells: List<List<Inline>>, head: Boolean) {
                cells.forEachIndexed { j, cell ->
                    if (j >= columns) return@forEachIndexed
                    val text = cell.plain()
                    val style = styleFor(text, head)
                    /* Measured a shade bolder than it is drawn,
                       because a cell's strong is wider than its
                       regular and a column a pixel too narrow
                       wraps the one word that mattered. */
                    val w = measurer.measure(
                        AnnotatedString(text),
                        style.copy(fontWeight = FontWeight.SemiBold),
                    ).size.width + pad
                    if (w > want[j]) want[j] = w.toFloat().coerceAtMost(cap)
                }
            }
            consider(block.head, head = true)
            for (row in block.rows) consider(row, head = false)
            for (j in want.indices) if (want[j] < floor) want[j] = floor

            /* Narrower than its plate, a table stretches to fill
               it, each column growing in proportion: the site says
               `width: 100%` and means it. */
            val total = want.sum()
            val room = with(density) { viewport.toPx() }
            if (total in 1f..room) {
                val grow = room / total
                for (j in want.indices) want[j] *= grow
            }
            want.map { with(density) { it.toDp() } }
        }
        val tableWidth = widths.fold(0.dp) { a, b -> a + b }

        Column(
            Modifier
                .clip(RoundedCornerShape(Corner.card))
                .material(
                    uk.co.reiad.library.core.Kind.PLATE,
                    c,
                    Corner.card,
                    ground = c.paperSunk,
                )
                .padding(innerPad)
                .horizontalScroll(rememberScrollState()),
        ) {
            if (block.head.isNotEmpty()) {
                Row(Modifier.width(tableWidth)) {
                    block.head.forEachIndexed { j, cell ->
                        Text(
                            cell.annotated(c.accent, open),
                            style = headStyle,
                            color = c.accent,
                            modifier = Modifier
                                .width(widths.getOrElse(j) { 0.dp })
                                .padding(horizontal = cellPad, vertical = Gap.s4),
                        )
                    }
                }
                Rule(tableWidth, c.hairline)
            }
            block.rows.forEachIndexed { i, row ->
                Row(Modifier.width(tableWidth), verticalAlignment = Alignment.Top) {
                    row.forEachIndexed { j, cell ->
                        val text = cell.plain()
                        Text(
                            cell.annotated(c.accent, open),
                            style = styleFor(text, head = false),
                            color = c.ink,
                            modifier = Modifier
                                .width(widths.getOrElse(j) { 0.dp })
                                .padding(horizontal = cellPad, vertical = Gap.s4),
                        )
                    }
                }
                if (i < block.rows.lastIndex) Rule(tableWidth, c.hairline)
            }
        }
    }
}

/** A ruled line the width of the TABLE, not of the screen: inside
    a horizontal scroll `fillMaxWidth` means the viewport, and a
    divider that stopped where the screen did drew the table's
    off-screen half as floating. */
@Composable
private fun Rule(width: Dp, colour: Color) {
    Box(
        Modifier
            .width(width)
            .height(1.dp)
            .background(colour.copy(alpha = colour.alpha * 0.8f)),
    )
}

/* ---------- inlines ---------- */

/** One run of inline content as styled text. A link is drawn in
    the accent and underlined, and PRESSES: `LinkAnnotation` hands
    the tap to whatever the screen installed in `LocalOpenLink`.
    The glossary link the money school uses is dotted on the site,
    which is a decoration Compose does not have, so it keeps the
    accent and the underline. */
private fun List<Inline>.annotated(
    accent: androidx.compose.ui.graphics.Color,
    onLink: ((String) -> Unit)? = null,
    emphasisInAccent: Boolean = false,
): AnnotatedString =
    buildAnnotatedString { appendInlines(this@annotated, accent, onLink, emphasisInAccent) }

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlines(
    inlines: List<Inline>,
    accent: androidx.compose.ui.graphics.Color,
    onLink: ((String) -> Unit)? = null,
    emphasisInAccent: Boolean = false,
) {
    for (inline in inlines) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlines(inline.children, accent, onLink, emphasisInAccent)
            }
            is Inline.Emphasis -> withStyle(
                if (emphasisInAccent) SpanStyle(color = accent)
                else SpanStyle(fontStyle = FontStyle.Italic),
            ) {
                appendInlines(inline.children, accent, onLink, emphasisInAccent)
            }
            is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                appendInlines(inline.children, accent, onLink, emphasisInAccent)
            }
            is Inline.Sup -> withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                appendInlines(inline.children, accent, onLink, emphasisInAccent)
            }
            is Inline.Sub -> withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                appendInlines(inline.children, accent, onLink, emphasisInAccent)
            }
            is Inline.Link -> {
                val look = SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
                if (onLink == null) {
                    /* No handler installed: the look without the
                       press, which is what a render test draws. */
                    withStyle(look) { appendInlines(inline.children, accent, onLink, emphasisInAccent) }
                } else {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = inline.href,
                            styles = TextLinkStyles(
                                style = look,
                                pressedStyle = look.copy(
                                    background = accent.copy(alpha = 0.16f),
                                ),
                            ),
                        ) { onLink(inline.href) },
                    ) {
                        appendInlines(inline.children, accent, onLink, emphasisInAccent)
                    }
                }
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
