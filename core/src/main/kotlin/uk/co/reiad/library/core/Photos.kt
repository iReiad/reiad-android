package uk.co.reiad.library.core

/* ============================================================
   How big a photo is, and which part of it survives the crop.

   A photo arrives whatever shape the camera made it, and a page
   that takes that shape as an instruction is a page where a
   portrait snap of a form fills the screen and pushes the next
   paragraph off it. The site fixes that with three small
   decisions, all of them a class on the `<figure>` so they
   survive both sanitisers and are legible in the exported file.

   Those classes reach here already, because the parser keeps a
   photo's classes rather than throwing them away, and this is
   what they MEAN. It is in core rather than in the renderer for
   the reason the material is: a decision about a photo is the
   same decision on a phone, on a tablet and in a test, and only
   the drawing differs.

   ---- and the focus is the share card's focus ----

   `focus-top` and `focus-bottom` are the same value the Studio
   uses when it draws the 1200 by 630 share card, so what a reader
   sees in the piece and what WhatsApp shows when the link is
   pasted agree. Reading them differently here would be the app
   and the site disagreeing about the same photo.
   ============================================================ */

/** The shape a photo is cropped to, or none at all. */
enum class Frame(val ratio: Float?) {
    /** However the camera left it. */
    NATURAL(null),
    WIDE(16f / 9f),
    SQUARE(1f),
    TALL(4f / 5f),
}

/** Which part to keep when the crop has to throw some away.
    A number from 0 at the top to 1 at the bottom, which is
    `object-position`'s vertical half said as a fraction. */
enum class Focus(val y: Float) {
    TOP(0f), MIDDLE(0.5f), BOTTOM(1f),
}

/** How far out of the column a photo is allowed to go. */
enum class Bleed {
    /** Held to the measure, like the prose. */
    COLUMN,

    /** Out past the measure but not to the screen edge. */
    WIDE,

    /** Edge to edge, and it loses its rounded corners with the
        margin, because a corner radius on something touching both
        edges of a screen is a gap, not a corner. */
    FULL,
}

/** Everything the classes on one `<figure>` add up to. */
data class Crop(
    val frame: Frame,
    val focus: Focus,
    val bleed: Bleed,
    /** The photo at the head of a piece, which gets more room
        above and below it than one in the middle of the prose. */
    val lead: Boolean,
)

/** What a figure's classes mean.

    Unknown classes are ignored rather than refused. The site's
    allowlist is a floor rather than a promise, exactly as it is
    for tags: stored prose predates some of these classes, and a
    photo carrying one nobody here has heard of should still be a
    photo at its natural shape. */
fun cropOf(classes: Set<String>): Crop = Crop(
    frame = when {
        "frame-wide" in classes -> Frame.WIDE
        "frame-square" in classes -> Frame.SQUARE
        "frame-tall" in classes -> Frame.TALL
        else -> Frame.NATURAL
    },
    focus = when {
        "focus-top" in classes -> Focus.TOP
        "focus-bottom" in classes -> Focus.BOTTOM
        else -> Focus.MIDDLE
    },
    bleed = when {
        /* `full` wins over `wide` where both are present, which
           is what the stylesheet does too: the rules are in that
           order and the later one takes the width. */
        "full" in classes -> Bleed.FULL
        "wide" in classes -> Bleed.WIDE
        else -> Bleed.COLUMN
    },
    lead = "lead-photo" in classes,
)

/** A media path, as a URL the app can fetch.

    Every photo on this site is served from the site's own origin
    under `/media/`, and a cover is checked against that on the
    way in for a reason worth restating: an off-site URL in an
    `og:image` is somebody else's bandwidth and somebody else's
    uptime on our social cards. So a path is resolved against the
    origin and an absolute URL is left alone, and nothing here
    invents a host. */
fun mediaUrl(src: String): String = when {
    src.isBlank() -> ""
    src.startsWith("http://") || src.startsWith("https://") -> src
    src.startsWith("//") -> "https:$src"
    src.startsWith("/") -> SITE_ORIGIN + src
    else -> "$SITE_ORIGIN/$src"
}
