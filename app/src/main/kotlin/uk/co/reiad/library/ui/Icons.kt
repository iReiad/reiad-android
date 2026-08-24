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
    "book" to "M4 4h6a3 3 0 0 1 3 3v13a2.5 2.5 0 0 0 -2.5-2.5H4zM20 4h-6a3 3 0 0 0 -3 3v13a2.5 2.5 0 0 1 2.5-2.5H20z",
    "scroll" to "M7 4h10a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2-2V6a2 2 0 0 1 2-2zM9 8h6M9 12h6M9 16h3",
    "signpost" to "M12 3v18M12 6h7l2 2.5L19 11h-7zM12 13H5l-2 2.5L5 18h7",
    "cart" to "M3 4h2l2.5 11h10L20 7H6M9 20a1 1 0 1 0 0-2 1 1 0 000 2zM17 20a1 1 0 1 0 0-2 1 1 0 000 2z",
    "compass" to "M12 21a9 9 0 1 0 0-18 9 9 0 000 18zM15.5 8.5l-2 5-5 2 2-5z",
    "calculator" to "M6 3h12a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1H6a1 1 0 0 1 -1-1V4a1 1 0 0 1 1-1zM8 7h8M8 11h2M12 11h2M16 11h0M8 15h2M12 15h2M16 15h0",
    "gauge" to "M3.5 17 A 8.5 8.5 0 1 1 20.5 17 M12 17 L16 10 M13.4 17 A 1.4 1.4 0 1 0 10.6 17 A 1.4 1.4 0 1 0 13.4 17",
    /* The site's own mark, beside its name in the top bar:
       three bars of a chart, which is what a finance library
       draws rather than a monogram. */
    "bars" to "M5 20V11M12 20V4M19 20v-6",
    "pen" to "M4 20l1-4L16 5l3 3L8 19zM14 7l3 3",
    "briefcase" to "M4 8h16a1 1 0 0 1 1 1v10a1 1 0 0 1 -1 1H4a1 1 0 0 1 -1-1V9a1 1 0 0 1 1-1zM9 8V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V8",
    "person" to "M12 12a4 4 0 1 0 0-8 4 4 0 000 8zM4 21c0-3.9 3.6-6 8-6s8 2.1 8 6",
    "mail" to "M3 6h18v12H3zM3 7l9 6 9-6",
    "user" to "M12 12a4 4 0 1 0 0-8 4 4 0 000 8zM4 21c0-3.9 3.6-6 8-6s8 2.1 8 6",
    /* The site's own proportions, out of `next/components/icons.tsx`:
       r 6.3 at (10.8, 10.8) with the handle from 15.4 to 20.5. It
       was r 7 at (11, 11) with a handle running to 21, which is a
       lens filling the whole grid and a stub poking out of it, and
       at 19dp in a round button it reads as a letter P. */
    "search" to "M9.5 14.5 A 5 5 0 1 0 9.5 4.5 A 5 5 0 1 0 9.5 14.5 M13.6 13.6 L20 20",
    "magnifier" to "M9.5 14.5 A 5 5 0 1 0 9.5 4.5 A 5 5 0 1 0 9.5 14.5 M13.6 13.6 L20 20",
    /* A crescent, not the site's half-filled circle.

       `icons.tsx` draws a circle with the left half FILLED, which
       is the contrast mark everybody knows. Everything here is
       STROKED, so that drawing arrives as a circle with a line
       down the middle of it, which is the international sign for
       "no". It was in the top bar at 19dp for three releases and
       was reported as one. A crescent survives being stroked. */
    "theme" to "M15.6 3.6 A 8.5 8.5 0 1 0 20.4 14.5 A 7 7 0 0 1 15.6 3.6 Z",
    /* Settings, which is what the top bar's second button opens.
       It was `theme` there, and a theme mark on a screen holding
       type size, measure, theme and language names one row of
       four. */
    "sliders" to "M4 7h9M17 7h3M4 17h3M11 17h9M15 4v6M8 14v6",
    "chevron" to "M9 5l7 7-7 7",
    /* The board's arranging strip. Up, down, and the two that
       say which way a widget is about to change width: an arrow
       pointing in for narrower and out for wider, because a
       reader has to know what a control does BEFORE pressing it
       and "resize" alone does not say which way. */
    "chevron-up" to "M5 15l7-7 7 7",
    "chevron-down" to "M5 9l7 7 7-7",
    "shrink" to "M9.5 4.5v5h-5M14.5 19.5v-5h5M9.5 9.5L4 4M14.5 14.5L20 20",
    "grow" to "M4.5 9.5v-5h5M19.5 14.5v5h-5M4.5 4.5L10 10M19.5 19.5L14 14",
    /* The Save under a byline, and the reading-list widget. The
       site draws this one as `keep`, and the catalogue in
       `shared/widgets.ts` is shared, so the NAME has to be the
       site's. */
    "keep" to "M6.5 3.5h11v17l-5.5-4-5.5 4z",
    "leaf" to "M19 5c0 7.2-4.4 11.5-9.6 11.9C7 17.1 5 15.2 5 12.6 5 7.7 10.4 5 19 5zM16 8L5.5 18.5",
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
    "microscope" to "M9 4h4l1 8H8zM6 20h13M8 16h6a4 4 0 0 0 0-8M11 12v4",
    "wallet" to "M4 7h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H4a1 1 0 0 1 -1-1V6a2 2 0 0 1 2-2h11M17 13h.01",
    "id" to "M3 6h18v12H3zM9 12a2 2 0 1 0 0-4 2 2 0 000 4zM6 16c.6-1.6 1.8-2.4 3-2.4s2.4.8 3 2.4M15 10h4M15 13h3",
    "shield" to "M12 3l8 3v6c0 4.4-3.2 7.7-8 9-4.8-1.3-8-4.6-8-9V6z",
    "door" to "M6 3h9a1 1 0 0 1 1 1v16a1 1 0 0 1 -1 1H6zM12 12h.01M15 3h3v18h-3",
    "calendar" to "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4",
    "warning" to "M12 4l9 16H3zM12 10v4M12 17h.01",

    /* ---- the rest of the site's table, extracted ----

       Pulled from the site's own compiled bundle rather than
       redrawn, circles and ellipses rewritten as arc pairs so
       one stroked path can carry each. The handful after them
       are the school lesson marks the API names and the site
       never drew: hand-set here in the same stroked manner,
       because a ladder of dots reads as a ladder of missing
       images. */
    "pause" to "M9 5.5v13M15 5.5v13",
    "back" to "M20 12H5M10.5 6.5L4 12l6.5 5.5",
    "basket" to "M4 10h16l-1.5 9h-13zM8 10l4-6 4 6M9.5 13.5v3M14.5 13.5v3",
    "bracket" to "M8 4H5.5v16H8 M16 4h2.5v16H16 M10.5 12h3",
    "branch" to "M7 4v10a4 4 0 0 0 4 4h2M7 6.5A1.5 1.5 0 1 0 7 3.5 1.5 1.5 0 1 0 7 6.5ZM17 13.5A1.5 1.5 0 1 0 17 10.5 1.5 1.5 0 1 0 17 13.5ZM17 13.5a4 4 0 0 1-4 4.5M15 18h2",
    "bridge" to "M3 16c2.5-5 15.5-5 18 0M3 16v3M21 16v3M7 14.2V19M12 13.2V19M17 14.2V19",
    "call" to "M6 4h4l1.5 4.5L9 10a11 11 0 0 0 5 5l1.5-2.5L20 14v4a2 2 0 0 1-2 2A14 14 0 0 1 4 6a2 2 0 0 1 2-2z",
    "clock" to "M3.5 12A8.5 8.5 0 1 0 20.5 12A8.5 8.5 0 1 0 3.5 12Z M12 7.5V12l3 2",
    "cup" to "M4.5 7h12v7.5a4 4 0 0 1-4 4h-4a4 4 0 0 1-4-4Z M16.5 9h2a2.5 2.5 0 0 1 0 5h-2 M4.5 21h12",
    "engine" to "M3 15V9h5l3-3h5v9H3Z M16 12h4.5v3H16 M6 15v3 M13 15v3",
    "equals" to "M6 9.5h12M6 14.5h12",
    "eye" to "M3 12c3.2-5 14.8-5 18 0-3.2 5-14.8 5-18 0ZM14.5 12A2.5 2.5 0 1 0 9.5 12 2.5 2.5 0 1 0 14.5 12Z",
    "flip" to "M12 3v18M9 7H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h4M15 7h4a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1h-4",
    "fork" to "M7 4v5a5 5 0 0 0 5 5 5 5 0 0 0 5-5V4M12 14v6",
    "forward" to "M4 12h15M13.5 6.5L20 12l-6.5 5.5",
    "gears" to "M7 10A3 3 0 1 0 13 10A3 3 0 1 0 7 10Z M10 4v2 M10 14v2 M4 10h2 M14 10h2 M5.8 5.8 7.2 7.2 M12.8 12.8l1.4 1.4 M14.8 17A2.2 2.2 0 1 0 19.2 17A2.2 2.2 0 1 0 14.8 17Z M17 13.5V15 M17 19v1.5 M13.5 17H15",
    "gift" to "M3.5 11h17v9.5h-17z M2.5 7.5h19V11h-19z M12 7.5V21 M12 7.5C10.5 4.5 6 4 6 6.6 6 8 8 8 12 7.5Z M12 7.5c1.5-3 6-3.5 6-.9 0 1.4-2 1.4-6 .9Z",
    "glue" to "M12 4c3 3.8 5 6.6 5 9a5 5 0 0 1-10 0c0-2.4 2-5.2 5-9z",
    "grid" to "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
    "hand" to "M9 11V5.5a1.5 1.5 0 0 1 3 0V11 M12 10.5V4.8a1.5 1.5 0 0 1 3 0V11 M15 11V7.3a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-.5a5.5 5.5 0 0 1-5.5-5.5v-3a1.5 1.5 0 0 1 3 0",
    "hat" to "M6.5 12.5V6a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v6.5 M3 12.5h18 M4.5 12.5c0 3.5 3.4 5.5 7.5 5.5s7.5-2 7.5-5.5",
    "heart" to "M12 20s-7.5-4.6-7.5-9.6A4.4 4.4 0 0 1 12 7.6a4.4 4.4 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20Z",
    "key" to "M4 12A4 4 0 1 0 12 12A4 4 0 1 0 4 12Z M12 12h9 M17.5 12v3.5 M20.5 12v2.5",
    "ladder" to "M8 3v18M16 3v18M8 7h8M8 12h8M8 17h8",
    "layers" to "M12 3l9 5-9 5-9-5 9-5zM3.8 12.5L12 17l8.2-4.5M3.8 16.5L12 21l8.2-4.5",
    "link" to "M9.5 14.5l5-5M8 12l-2.4 2.4a3.4 3.4 0 0 0 4.8 4.8L13 16.6M11 7.4l2.6-2.6a3.4 3.4 0 0 1 4.8 4.8L16 12",
    "lock" to "M6 11h12v9H6z M8.5 11V8a3.5 3.5 0 0 1 7 0v3",
    "map" to "M3 6.5 9 4l6 2.5L21 4v13.5L15 20l-6-2.5L3 20Z M9 4v13.5 M15 6.5V20",
    "merge" to "M6 4v4a6 6 0 0 0 6 6M18 4v4a6 6 0 0 1-6 6M12 14v6M9.5 17.5L12 20l2.5-2.5",
    "mould" to "M5 4h14M7 4v5a5 5 0 0 0 10 0V4M9 20h6M12 14v6",
    "mouth" to "M3.5 12c3-4.5 14-4.5 17 0-3 4.5-14 4.5-17 0Z M8 12c0-2 1.8-3.2 4-3.2s4 1.2 4 3.2",
    "nest" to "M5 15c1-4 4-6 7-6s6 2 7 6M8 15c.8-2.4 2.3-3.6 4-3.6s3.2 1.2 4 3.6M3.5 15h17",
    "no" to "M3.5 12A8.5 8.5 0 1 0 20.5 12A8.5 8.5 0 1 0 3.5 12Z M6 18 18 6",
    "note" to "M5.5 3.5h9L19 8v12.5H5.5Z M14.5 3.5V8H19 M8.5 12.5h7 M8.5 16h4.5",
    "open-book" to "M12 6c-2-1.8-5-2.3-8-2v14c3-.3 6 .2 8 2 2-1.8 5-2.3 8-2V4c-3-.3-6 .2-8 2zM12 6v14",
    "pair" to "M9.5 14A4.5 4.5 0 1 0 9.5 5 4.5 4.5 0 1 0 9.5 14ZM14.5 19A4.5 4.5 0 1 0 14.5 10 4.5 4.5 0 1 0 14.5 19Z",
    "play" to "M8 5.5 18.5 12 8 18.5Z",
    "puzzle" to "M9 4h6v3.2a2 2 0 1 1 0 3.6V14h-3.2a2 2 0 1 0-3.6 0H5v-3.2a2 2 0 1 0 0-3.6V4h4z",
    "question" to "M8.5 8.5A3.5 3.5 0 0 1 12 5.5c2 0 3.5 1.3 3.5 3 0 2.5-3.5 2.7-3.5 5.5M12 18h.01",
    "quote" to "M5 7h5v5a4 4 0 0 1-4 4M14 7h5v5a4 4 0 0 1-4 4",
    "ring" to "M12 20A8 8 0 1 0 12 4 8 8 0 1 0 12 20ZM12 16.5A4.5 4.5 0 1 0 12 7.5 4.5 4.5 0 1 0 12 16.5Z",
    "root" to "M12 3v8M12 11c0 4-3 6-7 7M12 11c0 4 3 6 7 7M12 11c-1 5 0 8 0 10",
    "star" to "M12 3.5l2.5 5.4 5.9.7-4.4 4 1.2 5.9L12 16.6l-5.2 2.9 1.2-5.9-4.4-4 5.9-.7z",
    "strong" to "M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10",
    "three" to "M12 6.5h.01M7 12h.01M17 12h.01M12 17.5h.01M12 6.5a5.5 9 0 0 0-5 5.5M12 6.5a5.5 9 0 0 1 5 5.5M7 12a5.5 9 0 0 0 5 5.5M17 12a5.5 9 0 0 1-5 5.5",
    "tone" to "M4 12h2.5l2-5 3 10 2.5-7 1.5 2H20",
    "two" to "M8.5 14A4 4 0 1 0 8.5 6 4 4 0 1 0 8.5 14ZM15.5 18A4 4 0 1 0 15.5 10 4 4 0 1 0 15.5 18Z",
    "wave" to "M3 12c2-4 4-4 6 0s4 4 6 0 4-4 6 0",
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
