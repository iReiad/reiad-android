package uk.co.reiad.library.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotosTest {

    @Test
    fun `a bare figure is left the shape the camera made it`() {
        val crop = cropOf(emptySet())
        assertEquals(Frame.NATURAL, crop.frame)
        assertEquals(null, crop.frame.ratio)
        assertEquals(Focus.MIDDLE, crop.focus)
        assertEquals(Bleed.COLUMN, crop.bleed)
        assertTrue(!crop.lead)
    }

    @Test
    fun `the three frames are the site's three ratios`() {
        assertEquals(16f / 9f, cropOf(setOf("frame-wide")).frame.ratio)
        assertEquals(1f, cropOf(setOf("frame-square")).frame.ratio)
        assertEquals(4f / 5f, cropOf(setOf("frame-tall")).frame.ratio)
    }

    @Test
    fun `focus is the vertical half of object-position`() {
        assertEquals(0f, cropOf(setOf("focus-top")).focus.y)
        assertEquals(1f, cropOf(setOf("focus-bottom")).focus.y)
        assertEquals(0.5f, cropOf(setOf("frame-square")).focus.y)
    }

    /** Both rules exist in the stylesheet and `full` is written
        second, so it takes the width. Said here rather than left
        to whichever branch happens to come first. */
    @Test
    fun `full wins over wide`() {
        assertEquals(Bleed.FULL, cropOf(setOf("wide", "full")).bleed)
        assertEquals(Bleed.WIDE, cropOf(setOf("wide")).bleed)
    }

    /** The allowlist is a floor rather than a promise. Stored
        prose predates some of these classes, so a photo carrying
        one nothing here knows is still a photo. */
    @Test
    fun `a class nobody knows is ignored rather than refused`() {
        val crop = cropOf(setOf("frame-square", "focus-top", "vintage", "polaroid"))
        assertEquals(Frame.SQUARE, crop.frame)
        assertEquals(Focus.TOP, crop.focus)
    }

    @Test
    fun `a lead photo says so`() {
        assertTrue(cropOf(setOf("lead-photo")).lead)
        assertTrue(!cropOf(setOf("frame-wide")).lead)
    }

    /* ---------- addresses ---------- */

    @Test
    fun `a media path resolves against the site and nothing else`() {
        assertEquals(
            "https://reiad.co.uk/media/tiny-experiments/38e1f376c70ab924.webp",
            mediaUrl("/media/tiny-experiments/38e1f376c70ab924.webp"),
        )
        assertEquals("", mediaUrl(""))
    }

    @Test
    fun `an absolute url is left alone`() {
        assertEquals("https://example.com/a.jpg", mediaUrl("https://example.com/a.jpg"))
        assertEquals("https://example.com/a.jpg", mediaUrl("//example.com/a.jpg"))
    }

    /** The real body from the live API, parsed, still carries the
        class the crop is read from. A parser that dropped a
        figure's classes would leave every photo natural and
        nothing would fail. */
    @Test
    fun `a real lead photo survives the parse with its class`() {
        val piece = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(
                PieceResponse.serializer(),
                requireNotNull(javaClass.getResourceAsStream("/fixtures/article-photo.json"))
                    .readBytes().decodeToString(),
            ).article
        assertTrue(piece.body.isNotBlank(), "the fixture has no body")

        val photos = BodyParser.parse(piece.body).blocks.filterIsInstance<Block.Photo>()
        assertTrue(photos.isNotEmpty(), "the fixture's photo did not survive the parse")
        val lead = photos.first()
        assertTrue(cropOf(lead.classes).lead, "the lead photo lost its class")
        assertTrue(
            mediaUrl(lead.src).startsWith("https://reiad.co.uk/media/"),
            "the photo resolved to ${mediaUrl(lead.src)}",
        )
    }

    /** And the address a piece is at keeps its suffix, because
        the suffix is part of the slug rather than part of a
        route: it is in every link inside every lesson body and in
        the library row of everybody who has saved one. */
    @Test
    fun `a piece keeps its html`() {
        val piece = Piece(slug = "tiny-experiments", section = "insights")
        assertEquals("/insights/tiny-experiments.html", piece.url)
    }
}
