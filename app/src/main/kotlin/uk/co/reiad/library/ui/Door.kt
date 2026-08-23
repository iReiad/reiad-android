package uk.co.reiad.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import uk.co.reiad.library.core.Kind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size

/* ============================================================
   The door: the first screen, and the site's own type in it.

   `next/app/(home)/page.tsx` and `.gate-*` in `next/styles/site.css`
   are the original, and every number below is read off one of
   them rather than chosen here. The parts are deliberately four
   small pieces rather than one Door component, because the school
   hubs, the reading hubs and the tools each want the top of this
   and not the rest of it.

   ---- why the app had none of this ----

   The app's home page was a serif line, a grey line and a stack
   of cards. Every one of those is correct and the page still read
   as a list of links, because what makes the site's front door a
   front door is entirely in this file: an eyebrow that says who
   is talking, a headline at four times body size with a marker
   stroke under the half of it that is the promise, and a strip of
   counts that turns three facts into one statement.
   ============================================================ */

/** The mono line above a headline: who is talking, in the
    section's own colour. `.gate-eyebrow.mono`. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.08.em),
        color = c.accent,
    )
}

/**
 * A headline with a marker stroke under part of it.
 *
 * `.gate-mark` is `linear-gradient(transparent 62%, accent 28%)`,
 * which is a stroke across the BOTTOM 38 per cent of the line
 * rather than a highlight behind the whole of it. That difference
 * is the whole effect: a full-height fill reads as a selection,
 * and a reader tries to work out what selected it.
 *
 * Drawn from the text's own layout rather than as a `SpanStyle`
 * background, for the same reason: a span background fills the
 * line box. This asks the finished layout where those characters
 * ended up and paints under them, so it survives a wrap, a
 * different face and a reader's own font scale.
 */
@Composable
fun Marked(
    text: String,
    mark: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
) {
    val c = LocalReiad.current
    val at = text.indexOf(mark)
    var layout by remember(text, mark) { mutableStateOf<TextLayoutResult?>(null) }
    val face = style ?: headlineStyle(text)
    val ink = c.accent.copy(alpha = 0.28f)

    /* 18ch is the ceiling and balance chooses inside it, which
       is both of the site's rules on this element and in the
       order a browser applies them. */
    val ceiling = measureCh(18, face)
    Text(
        text,
        modifier.width(balanced(text, face, ceiling)).drawBehind {
            val result = layout ?: return@drawBehind
            if (at < 0) return@drawBehind
            val first = result.getLineForOffset(at)
            val last = result.getLineForOffset(at + mark.length - 1)
            for (line in first..last) {
                val top = result.getLineTop(line)
                val bottom = result.getLineBottom(line)
                /* The stroke starts 62 per cent down the line, so
                   it sits under the letters and clear of the
                   matra above them. */
                val band = top + (bottom - top) * 0.62f
                /* The horizontal extent from the RANGE'S OWN
                   PATH rather than from two character positions.

                   `getHorizontalPosition` answers for a cursor
                   sitting at an offset, and in Bengali an offset
                   is not a glyph: a conjunct or a vowel sign
                   reorders, so the caret before আমাদের is not the
                   left edge of what আমাদের draws. Measured that
                   way the stroke started a syllable and a half
                   into the word. A selection path is what the
                   platform uses to highlight, and highlighting
                   this range is exactly what this is. */
                val from = maxOf(at, result.getLineStart(line))
                val to = minOf(at + mark.length, result.getLineEnd(line, true))
                if (to <= from) continue
                val box = result.getPathForRange(from, to).getBounds()
                val x0 = box.left
                val x1 = box.right
                drawRoundRect(
                    ink,
                    topLeft = Offset(x0 - 2.dp.toPx(), band),
                    size = Size((x1 - x0) + 4.dp.toPx(), bottom - band),
                    cornerRadius = CornerRadius(Corner.xs.toPx()),
                )
            }
        },
        style = face,
        color = c.ink,
        onTextLayout = { layout = it },
    )
}

/**
 * The headline face.
 *
 * `.gate-h1` is `clamp(1.9rem, 4.4vw, 3.4rem)`, and on a handset
 * the FLOOR of that clamp is what wins: 4.4vw of 412 is 18px, so
 * every phone gets 1.9rem, which is 30.4 of the site's pixels.
 * That is the number here, and it is worth writing down why it
 * looks bigger than it sounds: the headline is 18ch wide, so at
 * this size it takes two lines, and two lines of serif Bengali is
 * what a front door looks like. Set larger it fits on one and
 * stops being a headline.
 *
 * Bengali takes the Bengali serif and a 1.3 line, exactly as
 * `.gate-h1[lang="bn"]` does.
 */
@Composable
fun headlineStyle(text: String): TextStyle =
    if (isBangla(text)) {
        TextStyle(
            fontFamily = Faces.bengaliSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 30.sp,
            lineHeight = 39.sp,
        )
    } else {
        MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 37.sp)
    }

/**
 * A width in CHARACTERS, which is what the site says everywhere
 * it says a measure: `.gate-h1` is 18ch and `.gate-lede` is 56ch.
 *
 * Measured in the style it will be set in rather than guessed at
 * in dp, so it follows the face, the size and the reader's own
 * font scale. On a handset the lede's 56ch is wider than the
 * screen and does nothing, which is right: a measure is a
 * ceiling, not a layout.
 *
 * The headline's 18ch is the one that bites, and it is the
 * difference between a front page whose headline is a sentence
 * across the top and one that is a headline: the site breaks
 * টাকার ভাষা, / আমাদের ভাষায়। into two lines because of this
 * number and nothing else.
 */
@Composable
fun measureCh(count: Int, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(count, style, measurer, density) {
        /* U+0030, always, including in a Bengali face. That is
           what CSS `ch` is defined as: the advance of the LATIN
           zero in the element's own font, whatever script the
           element is written in.

           It was the Bengali zero here for one render, on the
           reasonable-sounding argument that a Bengali measure
           should be measured in Bengali. ০ is half again as wide
           as 0 in Noto Bengali Serif, so 18ch came out wider than
           the screen, the ceiling never bit, and the headline sat
           on one line looking like a sentence. The site's own
           number only means what it means with the reference
           character the site's engine uses. */
        val width = measurer.measure(
            "0".repeat(count),
            /* And measured in the LATIN SERIF even for a Bengali
               line, because that is the font the browser measures
               it in: the Bengali webfont this site loads carries
               no Latin digits, so `0` falls through to
               `var(--font-serif)` and `ch` is Spectral's zero.
               Measured in Noto Serif Bengali's own the ceiling
               came out a third narrower and the headline broke
               into three lines with one word alone on the last. */
            style.copy(fontFamily = Faces.serif),
        ).size.width
        with(density) { width.toDp() }
    }
}

/**
 * `text-wrap: balance`, which Compose has no property for.
 *
 * The site sets it on every heading (`h1, h2, h3` in
 * `site.css`), and it is what stops a two-line headline putting
 * one word alone on the second. It is not a nicety on this site:
 * the front door's headline is a phrase, a comma and a second
 * phrase with a marker stroke under it, and an unbalanced break
 * cuts the marked phrase in half.
 *
 * The algorithm is the browser's own: lay the text out at the
 * ceiling, count the lines, then find the NARROWEST width that
 * still takes that many lines. Nothing gets a line it did not
 * already have, and every line ends up as full as the fullest.
 *
 * Ten measurements of a headline, memoised on the text and the
 * style. It is not run on prose: `text-wrap: pretty` is what a
 * paragraph gets there, and a paragraph balanced would be a
 * paragraph narrower than its column for no reason.
 */
@Composable
fun balanced(text: String, style: TextStyle, ceiling: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, ceiling, measurer, density) {
        with(density) {
            val max = ceiling.roundToPx()
            if (max <= 0) return@with ceiling
            fun lines(px: Int) = measurer.measure(
                text, style, constraints = Constraints(maxWidth = px),
            ).lineCount
            val want = lines(max)
            if (want <= 1) return@with ceiling
            var low = 1
            var high = max
            while (low < high) {
                val mid = (low + high) / 2
                if (lines(mid) <= want) high = mid else low = mid + 1
            }
            low.toDp()
        }
    }
}

/** The paragraph under a headline. `.gate-lede`, 56ch. */
@Composable
fun Lede(text: String, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    val style = bodyStyle(text)
    Text(
        text,
        modifier.widthIn(max = measureCh(56, style)),
        style = style,
        color = c.inkSoft,
    )
}

/** One fact in the strip: a numeral and what it counts. */
data class Fact(val n: String, val label: String)

/**
 * The counts, as ONE strip with hairline dividers.
 *
 * `.gate-facts`, and the dividers are the point rather than
 * decoration: three floating pairs read as three things and a
 * ruled row reads as one statement about the site.
 *
 * The numerals arrive already in Bangla from the manifest, so
 * nothing here converts anything: the site counts and the app
 * draws.
 */
@Composable
fun Facts(facts: List<Fact>, modifier: Modifier = Modifier) {
    if (facts.isEmpty()) return
    val c = LocalReiad.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        facts.forEachIndexed { i, f ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(26.dp)
                        .drawBehind { drawRect(c.hairline) },
                )
            }
            Row(
                Modifier.padding(start = if (i == 0) 0.dp else Gap.s7, end = Gap.s7),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Gap.s4),
            ) {
                Text(
                    f.n,
                    style = TextStyle(
                        fontFamily = Faces.bengaliSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                    ),
                    color = c.accent,
                )
                Text(
                    f.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = if (isBangla(f.label)) Faces.bengali else Faces.sans,
                    ),
                    color = c.inkSoft,
                )
            }
        }
    }
}

/** The whole door, for a page that wants all of it. */
@Composable
fun Door(
    eyebrow: String?,
    headline: String,
    mark: String?,
    lede: String?,
    facts: List<Fact> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (!eyebrow.isNullOrBlank()) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(Gap.s7))
        }
        Marked(headline, mark.orEmpty())
        if (!lede.isNullOrBlank()) {
            Spacer(Modifier.height(Gap.s6))
            Lede(lede)
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(Gap.s8))
            Facts(facts)
        }
    }
}

/* ============================================================
   The top of every other page
   ============================================================ */

/**
 * Graph paper, masked to a soft ellipse.
 *
 * `.hero::before`: two 1px gradients at 44px, at 0.45 opacity,
 * under a radial mask centred at 30% 20%. It is the site's one
 * piece of texture and it is on every page's hero, which makes it
 * as much of the site's face as the colours are. The app had a
 * flat ground everywhere and looked, correctly, like a different
 * product.
 *
 * Drawn rather than tiled from a bitmap, because the mask has to
 * follow the box: a fixed image would repeat its own fade.
 */
fun Modifier.graphPaper(colours: ReiadColours, cell: Dp = 44.dp): Modifier =
    this.drawBehind {
        val step = cell.toPx()
        if (step <= 0f) return@drawBehind
        val line = colours.hairline.copy(alpha = colours.hairline.alpha * 0.45f)
        /* The mask, as a radial fade painted INTO a layer with the
           grid, is what a browser does. Compose has no mask
           property on a draw scope, so the fade is applied per
           line instead: a line's alpha falls with its distance
           from the centre of the ellipse. Same picture, no
           offscreen layer, and an offscreen layer per surface is
           what would make this expensive. */
        val cx = size.width * 0.30f
        val cy = size.height * 0.20f
        val rx = size.width * 0.90f
        val ry = size.height * 0.80f

        fun fade(dx: Float, dy: Float): Float {
            val d = kotlin.math.hypot(dx / rx, dy / ry)
            return (1f - (d / 0.7f)).coerceIn(0f, 1f)
        }

        var x = 0f
        while (x <= size.width) {
            val a = fade(x - cx, 0f).coerceAtLeast(fade(x - cx, size.height - cy))
            if (a > 0.02f) {
                drawLine(line.copy(alpha = line.alpha * a), Offset(x, 0f), Offset(x, size.height), 1f)
            }
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            val a = fade(0f, y - cy).coerceAtLeast(fade(size.width - cx, y - cy))
            if (a > 0.02f) {
                drawLine(line.copy(alpha = line.alpha * a), Offset(0f, y), Offset(size.width, y), 1f)
            }
            y += step
        }
    }

/**
 * The head of a page that is not the front door.
 *
 * The site's inner pages all wear this: a mono eyebrow in the
 * section's accent, a serif headline at three times body, and a
 * lede. `/tools` is "TOOLS · ক্যালকুলেটর" over "The five sums
 * worth doing before you decide anything." over a paragraph, and
 * every school hub, every reading hub and every calculator is the
 * same three things.
 *
 * The app's screens each had a small heading and a grey line, so
 * a reader arriving from the site met the same words at a third
 * of the size with none of the hierarchy.
 */
@Composable
fun PageHead(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    lede: String? = null,
    mark: String? = null,
    paper: Boolean = true,
) {
    val c = LocalReiad.current
    Column(
        modifier
            .fillMaxWidth()
            .then(if (paper) Modifier.graphPaper(c) else Modifier)
            .padding(bottom = Gap.s8),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(Gap.s6))
        }
        if (mark.isNullOrBlank()) {
            Text(
                title,
                Modifier.width(balanced(title, headlineStyle(title), measureCh(18, headlineStyle(title)))),
                style = headlineStyle(title),
                color = c.ink,
            )
        } else {
            Marked(title, mark)
        }
        if (!lede.isNullOrBlank()) {
            Spacer(Modifier.height(Gap.s6))
            Lede(lede)
        }
    }
}

/**
 * The way back, at the top of a page you went into.
 *
 * The site draws a breadcrumb in its top bar; a handset has no
 * room for one, so the page carries the one step that matters. It
 * is a `chip`, because a chip is the site's word for a small
 * thing you press that latches you somewhere, and because a line
 * of mono text with an arrow in front of it is not a target: this
 * one is a full tap height.
 */
@Composable
fun Crumb(label: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalReiad.current
    Row(
        modifier
            /* It is the way back, which makes it the one target
               on a lesson page a reader reaches for without
               looking. At chip padding it was 33dp. */
            .heightIn(min = Gap.tap)
            .clip(RoundedCornerShape(Corner.pill))
            .material(Kind.CHIP, c, Corner.pill, ground = c.accent.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Gap.s6, vertical = Gap.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("chevron", Modifier.rotate(180f), size = 13.dp, tint = c.accent)
        Spacer(Modifier.width(Gap.s4))
        Text(
            label,
            style = if (isBangla(label)) {
                BanglaBody.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = c.accent,
            maxLines = 1,
        )
    }
}

/* ============================================================
   A lesson's own head, which is four things and was one.

   The site sets it as: a trail, then an ICON and the name in
   both languages, then the one-line definition against an accent
   rail, then the minutes in mono. The app drew a plain
   `PageHead`, so the icon, the English name and the rail were
   all missing and the definition read as another paragraph of
   grey text.

   That is the whole of "course lectures don't have nice
   layouts": every word was there and none of the shape was.
   ============================================================ */

/**
 * The name in both languages, with the lesson's own mark beside
 * it.
 *
 * The second name is whichever the school teaches under: `en`
 * for the money school and English, `de` for German, `ar` for
 * Quranic Arabic. Passed in rather than picked here, because a
 * school knows its own language and this composable should not
 * have to.
 */
@Composable
fun LessonHead(
    title: String,
    /** The other name, in mono beside the Bangla, or null. */
    also: String?,
    /** The lesson's icon, from the row. Null draws nothing at
        all rather than a placeholder: an empty disc is a mark
        that means something and it does not. */
    icon: String?,
    eyebrow: String?,
    /** The one-line definition. Against an accent rail, which is
        what makes it read as a definition rather than as the
        first paragraph. */
    oneLiner: String?,
    /** `৩ মিনিট পড়া`, already worded and already in the right
        digits by the caller. */
    meta: String?,
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    Column(modifier.fillMaxWidth().graphPaper(c).padding(bottom = Gap.s8)) {
        if (!eyebrow.isNullOrBlank()) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(Gap.s6))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!icon.isNullOrBlank()) {
                /* A disc, at the height of the line it sits on,
                   the same as the site's `.lesson-art`. */
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(Corner.field))
                        .material(Kind.CHIP, c, Corner.field),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, size = 20.dp, tint = c.accent)
                }
                Spacer(Modifier.width(Gap.s6))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = headlineStyle(title),
                    color = c.ink,
                )
                if (!also.isNullOrBlank()) {
                    Text(
                        also,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = Faces.mono,
                        color = c.inkSoft,
                    )
                }
            }
        }

        if (!oneLiner.isNullOrBlank()) {
            Spacer(Modifier.height(Gap.s6))
            Row(Modifier.height(IntrinsicSize.Min)) {
                /* Three pixels of accent, full height, which is
                   `border-left: 3px solid var(--accent)` said in
                   Compose. A Divider would be a fixed height and
                   this has to match however many lines the
                   definition takes. */
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(Corner.xs))
                        .background(c.accent),
                )
                Spacer(Modifier.width(Gap.s6))
                Text(
                    oneLiner,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.inkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (!meta.isNullOrBlank()) {
            Spacer(Modifier.height(Gap.s6))
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = Faces.mono,
                color = c.inkSoft,
            )
        }
    }
}
