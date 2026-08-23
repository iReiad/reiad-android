package uk.co.reiad.library.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ============================================================
   The site's own drawings.

   `next/components/icons.tsx` holds thirty-two paths on a 24 by
   24 grid, stroked rather than filled, and `IconName` in
   `shared/nav.ts` is the list of names. Every nav item names one,
   and the name arrives in the manifest, so an icon here is looked
   up BY NAME rather than chosen by a `when` over destinations:
   a school added to the site tomorrow arrives with its icon
   already named and needs no app release to draw it.

   What a name that is not here gets is a dot, not a crash and not
   a blank. A missing drawing should look like a missing drawing.

   These are the site's paths, copied. That is a second copy and
   it is the one place in this app that has one, because an SVG
   path is not something an endpoint can usefully send and a
   drawing is not data the way a lesson is. `SHAPES` below is
   checked against `IconName` by hand when the site adds one; the
   dot is what covers the gap in the meantime.
   ============================================================ */

internal val SHAPES: Map<String, String> = mapOf(
    "home" to "M3 11l9-8 9 8M5 9.5V21h14V9.5",
    "skills" to "M12 3l9 5-9 5-9-5 9-5zM5 11v5c0 1.7 3.1 3 7 3s7-1.3 7-3v-5",
    "coins" to "M12 7c4.4 0 8-1.3 8-2s-3.6-2-8-2-8 1.3-8 2 3.6 2 8 2zM4 5v6c0 .7 3.6 2 8 2s8-1.3 8-2V5M4 11v6c0 .7 3.6 2 8 2s8-1.3 8-2v-6",
    "book" to "M4 4h6a3 3 0 013 3v13a2.5 2.5 0 00-2.5-2.5H4zM20 4h-6a3 3 0 00-3 3v13a2.5 2.5 0 012.5-2.5H20z",
    "scroll" to "M7 4h10a2 2 0 012 2v12a2 2 0 01-2 2H7a2 2 0 01-2-2V6a2 2 0 012-2zM9 8h6M9 12h6M9 16h3",
    "signpost" to "M12 3v18M12 6h7l2 2.5L19 11h-7zM12 13H5l-2 2.5L5 18h7",
    "cart" to "M3 4h2l2.5 11h10L20 7H6M9 20a1 1 0 100-2 1 1 0 000 2zM17 20a1 1 0 100-2 1 1 0 000 2z",
    "compass" to "M12 21a9 9 0 100-18 9 9 0 000 18zM15.5 8.5l-2 5-5 2 2-5z",
    "calculator" to "M6 3h12a1 1 0 011 1v16a1 1 0 01-1 1H6a1 1 0 01-1-1V4a1 1 0 011-1zM8 7h8M8 11h2M12 11h2M16 11h0M8 15h2M12 15h2M16 15h0",
    "gauge" to "M4 18a8 8 0 1116 0M12 18l4-5",
    /* The site's own mark, beside its name in the top bar:
       three bars of a chart, which is what a finance library
       draws rather than a monogram. */
    "bars" to "M5 20V11M12 20V4M19 20v-6",
    "pen" to "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3",
    "briefcase" to "M4 8h16a1 1 0 011 1v10a1 1 0 01-1 1H4a1 1 0 01-1-1V9a1 1 0 011-1zM9 8V5.5A1.5 1.5 0 0110.5 4h3A1.5 1.5 0 0115 5.5V8",
    "person" to "M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c0-3.9 3.6-6 8-6s8 2.1 8 6",
    "mail" to "M3 6h18v12H3zM3 7l9 6 9-6",
    "user" to "M12 12a4 4 0 100-8 4 4 0 000 8zM4 21c0-3.9 3.6-6 8-6s8 2.1 8 6",
    /* The site's own proportions, out of `next/components/icons.tsx`:
       r 6.3 at (10.8, 10.8) with the handle from 15.4 to 20.5. It
       was r 7 at (11, 11) with a handle running to 21, which is a
       lens filling the whole grid and a stub poking out of it, and
       at 19dp in a round button it reads as a letter P. */
    "search" to "M10.8 17.1a6.3 6.3 0 100-12.6 6.3 6.3 0 000 12.6zM15.4 15.4l5.1 5.1",
    "magnifier" to "M10.8 17.1a6.3 6.3 0 100-12.6 6.3 6.3 0 000 12.6zM15.4 15.4l5.1 5.1",
    /* A crescent, not the site's half-filled circle.

       `icons.tsx` draws a circle with the left half FILLED, which
       is the contrast mark everybody knows. Everything here is
       STROKED, so that drawing arrives as a circle with a line
       down the middle of it, which is the international sign for
       "no". It was in the top bar at 19dp for three releases and
       was reported as one. A crescent survives being stroked. */
    "theme" to "M20.2 14.8A8.5 8.5 0 019.2 3.8a8.5 8.5 0 1011 11z",
    /* Settings, which is what the top bar's second button opens.
       It was `theme` there, and a theme mark on a screen holding
       type size, measure, theme and language names one row of
       four. */
    "sliders" to "M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M8 14v6",
    "chevron" to "M9 5l7 7-7 7",
    "arrow" to "M4 12h15M13.5 6.5L20 12l-6.5 5.5",
    "menu" to "M4 7h16M4 12h16M4 17h16",
    "close" to "M6 6l12 12M18 6L6 18",
    "check" to "M5 13l4 4L19 7",
    "plus" to "M12 5v14M5 12h14",
    "spark" to "M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z",
    /* A bird, for the flock: `ROUTINE.md` counts how many
       times the birds have been fed and never how recently. */
    "bird" to "M4 14c3-6 8-8 12-8 0 5-2 8-5 9l-2 5M9 15l-3 4M8 6h.01",
    "seed" to "M12 21c0-6 3-10 8-11-1 6-4 9-8 11zM12 21C8 19 5 16 4 10c5 1 8 5 8 11z",
    "cap" to "M12 3l9 5-9 5-9-5 9-5zM7 11v4c0 1.4 2.2 2.5 5 2.5s5-1.1 5-2.5v-4",
    "microscope" to "M9 4h4l1 8H8zM6 20h13M8 16h6a4 4 0 000-8M11 12v4",
    "wallet" to "M4 7h14a2 2 0 012 2v8a2 2 0 01-2 2H4a1 1 0 01-1-1V6a2 2 0 012-2h11M17 13h.01",
    "id" to "M3 6h18v12H3zM9 12a2 2 0 100-4 2 2 0 000 4zM6 16c.6-1.6 1.8-2.4 3-2.4s2.4.8 3 2.4M15 10h4M15 13h3",
    "shield" to "M12 3l8 3v6c0 4.4-3.2 7.7-8 9-4.8-1.3-8-4.6-8-9V6z",
    "door" to "M6 3h9a1 1 0 011 1v16a1 1 0 01-1 1H6zM12 12h.01M15 3h3v18h-3",
    "calendar" to "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4",
    "warning" to "M12 4l9 16H3zM12 10v4M12 17h.01",
)

/** One drawing, at the site's stroke weight.

    Stroked at 1.6 on a 24 grid, which is what `icons.tsx` uses:
    a filled icon beside stroked prose reads as a button, and
    every one of these sits next to words. */
@Composable
fun Icon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color? = null,
) {
    val colour = tint ?: LocalReiad.current.accent
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        val shape = SHAPES[name]
        if (shape == null) {
            /* A name with no drawing gets a dot. Not a crash, and
               not nothing: a missing icon should look missing. */
            drawCircle(colour, radius = 3f * scale, center = center)
            return@Canvas
        }
        val path: Path = PathParser().parsePathString(shape).toPath()
        val matrix = androidx.compose.ui.graphics.Matrix().apply { scale(scale, scale) }
        path.transform(matrix)
        drawPath(
            path,
            colour,
            style = Stroke(
                width = 1.6f * scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
