package uk.co.reiad.library.ui

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.co.reiad.library.core.EdgeLayer
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Source
import uk.co.reiad.library.core.edgeOf
import uk.co.reiad.library.core.glintFor
import uk.co.reiad.library.core.glowStop
import uk.co.reiad.library.core.glowStrength
import uk.co.reiad.library.core.glowWidth
import uk.co.reiad.library.core.specularStrength
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/* ============================================================
   Six kinds of glass, drawn.

   `core/Material.kt` holds the four numbers per kind and derives
   every visible figure from them. This is the other half: what
   those figures look like on a handset. Nothing here decides how
   bright or how wide anything is, and that separation is the
   point, because the day a kind is retuned this file should not
   need reading.

   ---- the primitive Compose does not have ----

   Every part of a cut edge is an INSET shadow, and the site's own
   notes say at length why it has to be: a gradient stop is a
   straight line across the whole box, so the top of a bottom band
   is a chord. On a card that reads as a rule somebody drew under
   the content; on a pill it cuts across the lower curve and stops
   dead where the arc begins. An inset shadow is bounded by the
   border radius, so offsetting it down with a negative spread
   makes a crescent that hugs the bottom arc, thickest in the
   middle and tapering into the corners.

   Compose has `Modifier.shadow`, which is an outer shadow, and
   nothing else. `prepare` below builds the inset one: the shape's
   COMPLEMENT, offset and resized by the spread, blurred, and
   clipped to the shape.

   ---- what is not drawn, and why that is not a shortcut ----

   A layer whose colour lands under one step of eight-bit alpha is
   skipped. That is the same statement `--standing: 0` makes, that
   a row flush in a column of twenty draws nothing, and it is what
   keeps a ladder of forty rungs at forty clipped grounds rather
   than a hundred and sixty blurred paths.

   ---- one deviation from the site, on purpose ----

   The stylesheet defines `--rim-face-b`, a second rim colour at
   the accent's hue plus 62 degrees, and no rule reads it: the
   shadow uses `--rim-face-a` for the whole ring. So a cut edge
   there takes ONE tinted colour, not two, and this does the same.
   Drawing the two-sided split here would be the app inventing a
   look the site does not have, which is the one thing a port must
   not do.
   ============================================================ */

/** Below one step of eight-bit alpha there is nothing to draw. */
private const val INVISIBLE = 1.0f / 255.0f

/** How lit a surface is, and where the light is on it.

    One object rather than three parameters because all three
    change together and every surface reads all three. `a` is 0 at
    rest and 1 under a finger; `nx`/`ny` are the touch as signed
    numbers from the centre, which is the language `core` states
    the glint and the lean in. */
data class Lit(val a: Float = 0f, val nx: Float = 0f, val ny: Float = 0f) {
    companion object {
        val Rest = Lit()
    }
}

/** The material as a modifier, so anything can be glass without
    being wrapped in one more Box.

    `lit` and `grainOrigin` are lambdas rather than values, and
    that is load bearing rather than a style. A value would change
    the modifier's identity on every animation frame, which throws
    away the cache below and rebuilds four paths and a texture
    sixty times a second for every surface on screen. Read inside
    the draw block, they invalidate the draw and nothing else. */
fun Modifier.material(
    kind: Kind,
    colours: ReiadColours,
    corner: Dp = Corner.card,
    ground: Color? = null,
    wash: ((Lit) -> Brush?)? = null,
    lit: () -> Lit = { Lit.Rest },
    grainOrigin: () -> Offset = { Offset.Zero },
): Modifier = this.drawWithCache {
    val radius = corner.toPx().coerceAtMost(min(size.width, size.height) / 2f)
    val shape = RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))
    val corners = CornerRadius(radius)

    /* Built once per size and per palette. The three static edge
       layers do not move; the glint and the two lights do, and
       those are the only things rebuilt per frame. */
    val edge = edgeOf(kind).mapNotNull { it.prepare(kind, colours, shape, this) }
    val grain = grainBrush(colours, this)
    val tile = grainSide(this).toFloat()
    /* The weave's clip, built ONCE per size along with the edges
       rather than inside the draw.

       It was a `Path()` allocated and filled on every draw call,
       which is one allocation per surface per frame: a list of
       twenty rows being scrolled was twenty of them a frame,
       sixty times a second, for a shape that only changes when
       the row changes size. Nothing about it was ever wrong on
       screen, which is exactly why it survived: the cost of a
       redraw is the one thing a snapshot cannot show. */
    val weave = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), corners)) }
    /* SOLID, all six of them, and that is a correction.

       Panes and cards used to let a tenth of the field through
       on the argument that a sheet of glass on a desk picks up
       the wood. What it actually picked up was whichever ambient
       pool happened to be behind it, which on a dark handset
       read as a lamp switched on inside the card: reported twice,
       the second time as "the light in the middle of all cards
       etc is weirdly there".

       It was the wrong place for the effect. The material's own
       doctrine, written three files away, is that the face of a
       slab is FLAT and the depth lives at the cut edge, and the
       thing that makes these read as glass is the edge, the rim
       and the light that arrives under a finger. The field
       belongs BEHIND the content, in the gaps between the
       cards, where it is atmosphere rather than a stain on the
       thing you are trying to read.

       The bars keep their frost, because an explicit `ground`
       still wins and that is what the shell hands them: chrome
       is where translucency says something (there is a page
       under this and it is moving), and a card sitting on the
       page is not chrome. */
    val bed = ground ?: colours.panel

    onDrawBehind {
        val now = lit()
        drawRoundRect(bed, cornerRadius = corners)

        /* ---- back to front, and the order IS the illusion ----

           The glow is light INSIDE the material and sits deepest;
           the grain is in the material above it; a surface's own
           wash is on the face; the specular is the reflection off
           the very front. Reorder them and it stops being glass:
           a glow in front of the grain is a lamp taped to a
           window. */
        glow(kind, colours.accent, now, corners)
        grain(grain, tile, grainOrigin(), weave)
        wash?.invoke(now)?.let { drawRoundRect(it, cornerRadius = corners) }
        specular(kind, colours, now, corners)

        /* And the edge above all four, because an inset shadow is
           drawn over a background rather than under it. */
        for (layer in edge) layer.draw(this)
        glintFor(kind.glass, now.nx.toDouble(), now.ny.toDouble(), now.a.toDouble())
            .prepare(kind, colours, shape, this)
            ?.draw(this)
    }
}

/* ---------- the two lights ---------- */

/** The finger's own light scattering in the material near where
    it is. The accent's colour, because this is the one of the two
    lights that is coloured by the page. */
private fun DrawScope.glow(kind: Kind, accent: Color, lit: Lit, corners: CornerRadius) {
    if (!kind.follows || lit.a <= 0f) return
    val strength = (glowStrength(kind) * lit.a).toFloat()
    if (accent.alpha * strength < INVISIBLE) return
    drawRoundRect(
        Brush.radialGradient(
            0f to accent.copy(alpha = strength),
            glowStop(kind).toFloat() to Color.Transparent,
            center = Offset(
                x = size.width * (0.5f + lit.nx / 2f),
                y = size.height * (0.5f + lit.ny / 2f),
            ),
            radius = max(1f, glowWidth(kind).dp.toPx()),
        ),
        cornerRadius = corners,
    )
}

/** The light coming OFF the face, which is a different thing from
    the light in the glass and has to stay distinguishable from
    it: achromatic, on the very front, and raked at 105 degrees
    because a vertical band reads as a seam and a raked one reads
    as a reflection. */
private fun DrawScope.specular(kind: Kind, colours: ReiadColours, lit: Lit, corners: CornerRadius) {
    val strength = specularStrength(kind, lit.a.toDouble()).toFloat()
    val top = colours.paneTop
    if (top.alpha * strength < INVISIBLE) return

    /* Kept off both ends so the three stops stay strictly
       ascending: a gradient with two stops at the same position
       is a hard edge where a sweep was meant. */
    val at = (0.5f + lit.nx / 2f).coerceIn(0.02f, 0.98f)
    val axis = 105.0 * PI / 180.0
    val direction = Offset(sin(axis).toFloat(), -cos(axis).toFloat())
    val reach = size.width * abs(direction.x) + size.height * abs(direction.y)
    val centre = Offset(size.width / 2f, size.height / 2f)

    drawRoundRect(
        Brush.linearGradient(
            (at - 0.15f).coerceIn(0f, 1f) to Color.Transparent,
            at to top.copy(alpha = top.alpha * strength),
            (at + 0.15f).coerceIn(0f, 1f) to Color.Transparent,
            start = Offset(
                centre.x - direction.x * reach / 2f,
                centre.y - direction.y * reach / 2f,
            ),
            end = Offset(
                centre.x + direction.x * reach / 2f,
                centre.y + direction.y * reach / 2f,
            ),
        ),
        cornerRadius = corners,
    )
}

/* ---------- the weave ---------- */

/** The site's own paper: two families of hairlines at 45 degrees.

    Drawn once into a tile and repeated rather than stroked line
    by line. A card 350 by 200 needs 78 strokes per family, and a
    ladder has forty cards on it.

    The tile is 7dp square and the spacing that falls out of it is
    4.95dp against the site's 5. The exact tile for a 5dp spacing
    measured along the normal is 5 root 2, which is 7.07 and does
    not tile in whole pixels: rounding it to 7 drifts a pixel
    every fourteen repeats, and a drifting weave is a seam that
    walks across the page. One per cent of a spacing nobody can
    name is the right thing to give up to keep the pattern
    registered. */
private fun grainSide(density: Density): Int =
    max(2, (7f * density.density).roundToInt())

/* One tile per palette and density, not one per surface.

   The tile is small, about 1.7KB at three times density, and a
   screen of forty rungs would otherwise hold forty identical
   copies of it and rebuild each on every resize. The key is
   everything the tile is drawn from, so a theme change or a
   section change makes a new one and nothing stale survives. */
private val grainCache = HashMap<String, ShaderBrush>()

private fun grainBrush(colours: ReiadColours, density: Density): ShaderBrush {
    val key = "${colours.texInk.value}/${colours.texA}/${colours.texB}/${density.density}"
    grainCache[key]?.let { return it }
    val side = grainSide(density)
    val stroke = max(1f, density.density)
    val tile = ImageBitmap(side, side)
    val canvas = Canvas(tile)
    val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = stroke
        style = PaintingStyle.Stroke
    }

    fun family(alpha: Float, downhill: Boolean) {
        if (colours.texInk.alpha * alpha < INVISIBLE) return
        paint.color = colours.texInk.copy(alpha = alpha)
        /* Three passes so the line WRAPS. A tile has to carry the
           parts of its neighbours' diagonals that cross it, or
           every repeat shows a seam where the line restarts. */
        for (shift in -1..1) {
            val x = shift * side.toFloat()
            if (downhill) {
                canvas.drawLine(Offset(x, 0f), Offset(x + side, side.toFloat()), paint)
            } else {
                canvas.drawLine(Offset(x, side.toFloat()), Offset(x + side, 0f), paint)
            }
        }
    }

    family(colours.texA, downhill = true)
    family(colours.texB, downhill = false)
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        .also { grainCache[key] = it }
}

/** And drawn from the PAGE's origin rather than the surface's.

    A pattern restarting at each surface's own top-left makes a
    screen of glass read as separate stickers rather than as
    pieces cut from one sheet, which is the site's own reason for
    handing each surface its page offset. Only the grain is
    positioned this way: the other three layers are facts about
    one piece of glass and belong to it. */
private fun DrawScope.grain(
    brush: ShaderBrush,
    tile: Float,
    origin: Offset,
    clip: Path,
) {
    if (tile <= 0f) return
    val dx = -(origin.x % tile)
    val dy = -(origin.y % tile)
    clipPath(clip) {
        translate(dx, dy) {
            drawRect(brush, size = Size(size.width + tile * 2, size.height + tile * 2))
        }
    }
}

/* ---------- the primitive ---------- */

/** One inset shadow, ready to draw: what casts it, what clips it,
    and what it is drawn in. */
private class PreparedEdge(
    private val caster: Path,
    private val clip: Path,
    private val paint: Paint,
) {
    fun draw(scope: DrawScope) {
        scope.clipPath(clip) {
            drawIntoCanvas { it.drawPath(caster, paint) }
        }
    }
}

/** An inset shadow is the shape's COMPLEMENT, cast inward.

    So the caster is a rectangle well outside the surface with a
    hole cut out of it, where the hole is the surface resized by
    the spread and slid by the offset. A negative spread GROWS the
    hole, which pulls the shadow back to a thin band along one
    edge, and that is how a crescent hugging a bottom arc is made
    rather than a bar across a box.

    Null when the result could not be seen, which is a statement
    rather than an optimisation: see `INVISIBLE`. */
private fun EdgeLayer.prepare(
    kind: Kind,
    colours: ReiadColours,
    shape: RoundRect,
    density: Density,
): PreparedEdge? {
    val tint = when (source) {
        Source.FACE -> colours.glassFace
        Source.UNDER -> colours.glassUnder
        Source.RIM -> colours.rimFace(kind)
    }
    val effective = tint.alpha * alpha.toFloat()
    if (effective < INVISIBLE) return null

    with(density) {
        val spreadPx = spread.dp.toPx()
        val dxPx = dx.dp.toPx()
        val dyPx = dy.dp.toPx()
        val blurPx = blur.dp.toPx()

        val hole = RoundRect(
            left = shape.left + spreadPx + dxPx,
            top = shape.top + spreadPx + dyPx,
            right = shape.right - spreadPx + dxPx,
            bottom = shape.bottom - spreadPx + dyPx,
            cornerRadius = CornerRadius(max(0f, shape.topLeftCornerRadius.x - spreadPx)),
        )
        if (hole.right <= hole.left || hole.bottom <= hole.top) return null

        /* Far enough out that the blur has material to come from.
           Too tight and the shadow fades in from the caster's own
           outer boundary as well as from the hole, which draws a
           soft second ring round the whole surface. */
        val margin = (abs(dxPx) + abs(dyPx) + blurPx + abs(spreadPx)) * 3f + 24f
        val outside = Path().apply {
            addRect(
                Rect(
                    shape.left - margin,
                    shape.top - margin,
                    shape.right + margin,
                    shape.bottom + margin,
                ),
            )
        }
        val cut = Path().apply { addRoundRect(hole) }

        val paint = Paint().apply {
            color = tint.copy(alpha = effective)
            isAntiAlias = true
        }
        if (blurPx > 0f) {
            if (CAN_BLUR) {
                paint.asFrameworkPaint().maskFilter =
                    BlurMaskFilter(sigmaFor(blurPx), BlurMaskFilter.Blur.NORMAL)
            } else {
                /* A hard ring at full strength is a drawn outline,
                   which is the exact failure a blurred rim exists
                   to avoid. Where the blur cannot be drawn the
                   line is quietened to a hairline rather than
                   left bright, so an old handset gets a fainter
                   edge and never a box. */
                paint.color = tint.copy(alpha = effective * 0.55f)
            }
        }
        return PreparedEdge(
            caster = Path.combine(PathOperation.Difference, outside, cut),
            clip = Path().apply { addRoundRect(shape) },
            paint = paint,
        )
    }
}

/** `BlurMaskFilter` is honoured on a hardware-accelerated canvas
    only from API 28. Below that it is ignored silently, which
    would turn every soft rim into the drawn outline it exists to
    avoid, so the version is asked rather than hoped about. */
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

/** CSS states a blur as the width of the whole falloff; Skia
    takes a radius and turns it into a standard deviation by
    `0.57735 * r + 0.5`. A CSS blur of b is a Gaussian of b/2, so
    this is that relation solved for r. */
private fun sigmaFor(cssBlurPx: Float): Float =
    max(0.1f, ((cssBlurPx / 2f) - 0.5f) / 0.57735f)

/* ---------- the one edge that is not the material's ---------- */

/** A dashed rim, which is how an INFORMATIONAL surface says it is
    the end of the road.

    It is not part of the material and must not be: the material's
    edge is a physical fact about a piece of glass, and a dashed
    line is a convention about what a thing does. The site keeps
    them apart the same way, `border-style: dashed` in `@layer
    deck` beside a lit edge from `@layer glow`, and mixing them
    would mean a kind whose thickness depended on what it linked
    to. */
fun Modifier.dashedEdge(
    colour: Color,
    corner: Dp,
    width: Dp = 1.dp,
    dash: Dp = 4.dp,
    gap: Dp = 4.dp,
): Modifier = drawWithCache {
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
        width = width.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(dash.toPx(), gap.toPx()),
        ),
    )
    val radius = CornerRadius(corner.toPx().coerceAtMost(min(size.width, size.height) / 2f))
    val inset = width.toPx() / 2f
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            colour,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = radius,
            style = stroke,
        )
    }
}
