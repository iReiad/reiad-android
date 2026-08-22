package uk.co.reiad.library.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import uk.co.reiad.library.core.Bleed
import uk.co.reiad.library.core.Crop
import uk.co.reiad.library.core.Focus
import uk.co.reiad.library.core.Frame
import uk.co.reiad.library.core.Kind
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.cropOf
import uk.co.reiad.library.core.mediaUrl

/* ============================================================
   A photo in a piece, cropped the way the site crops it.

   `cropOf` in core turns the classes on a `<figure>` into three
   decisions and this draws them. The decisions are there rather
   than here for the reason the material's numbers are: a crop is
   the same crop on a phone, on a tablet and in a test.

   ---- what a phone changes, and what it must not ----

   `wide` and `full` mean something on a page with a measure and
   a wide screen either side of it. A handset column IS the
   screen, so a photo cannot leave it: what survives is the
   difference in how much room a photo takes above and below, and
   whether it keeps its corners. `full` loses them, for the same
   reason it does on the site: a corner radius on something
   touching both edges of a screen is a gap rather than a corner.

   ---- and it fades in rather than appearing ----

   A photo that pops in when it decodes shifts the prose a
   reader was mid-sentence in. The frame is reserved from the
   ratio BEFORE the bytes arrive, which is what the frame classes
   are for, and a photo with no frame gets the measured ratio the
   moment it is known. Only the opacity moves.
   ============================================================ */

@Composable
fun PhotoBlock(
    src: String,
    alt: String,
    classes: Set<String>,
    caption: AnnotatedString?,
    modifier: Modifier = Modifier,
) {
    val crop = cropOf(classes)
    val url = mediaUrl(src)
    if (url.isBlank()) return

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (crop.lead) Gap.s9 else Gap.s7),
    ) {
        Photo(url, alt, crop, Modifier.fillMaxWidth())
        if (caption != null && caption.isNotEmpty()) {
            val c = LocalReiad.current
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSoft,
                modifier = Modifier.padding(top = Gap.s5),
            )
        }
    }
}

/** The image itself. */
@Composable
fun Photo(
    url: String,
    alt: String,
    crop: Crop = Crop(Frame.NATURAL, Focus.MIDDLE, Bleed.COLUMN, lead = false),
    modifier: Modifier = Modifier,
) {
    val c = LocalReiad.current
    val corner = if (crop.bleed == Bleed.FULL) 0.dp else Corner.card
    val shape = RoundedCornerShape(corner)

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            /* Fading is this file's job, not Coil's: the whole
               point is that the FRAME does not move, and a
               crossfade that also animates size would undo it. */
            .crossfade(false)
            .build(),
        contentDescription = alt.ifBlank { null },
        /* Cover for a framed photo, because a frame is a promise
           about the shape of the hole and `Fit` would letterbox
           it. Natural fills the width and takes whatever height
           the picture asks for, which is what a browser does with
           no aspect-ratio set. */
        contentScale = if (crop.frame.ratio != null) ContentScale.Crop else ContentScale.FillWidth,
        alignment = when (crop.focus) {
            Focus.TOP -> Alignment.TopCenter
            Focus.BOTTOM -> Alignment.BottomCenter
            Focus.MIDDLE -> Alignment.Center
        },
        modifier = modifier
            .then(crop.frame.ratio?.let { Modifier.aspectRatio(it) } ?: Modifier)
            .clip(shape),
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> {
                val shown by animateFloatAsState(1f, tween(Motion.ENTER_MS), label = "photo")
                SubcomposeAsyncImageContent(alpha = shown)
            }

            is AsyncImagePainter.State.Error -> Missing(alt, crop)

            /* Waiting. A GROOVE rather than a grey box: a hole
               waiting to be filled is exactly what this is, and
               it is the one kind in the system that means that. */
            else -> Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (crop.frame.ratio == null) Modifier.height(200.dp) else Modifier)
                    .material(Kind.GROOVE, c, corner, ground = c.paperSunk),
            )
        }
    }
}

/** A photo that would not load.

    Named rather than blank, because a blank rectangle in the
    middle of prose reads as a rendering fault and an empty frame
    with its own alt text reads as a photo that is missing, which
    is the true thing. */
@Composable
private fun Missing(alt: String, crop: Crop) {
    val c = LocalReiad.current
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (crop.frame.ratio == null) Modifier.height(160.dp) else Modifier)
            .clip(RoundedCornerShape(Corner.card))
            .background(c.paperSunk)
            .padding(Gap.s7),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            alt.ifBlank { "A photo that would not load" },
            style = MaterialTheme.typography.bodySmall,
            color = c.inkSoft,
        )
    }
}
