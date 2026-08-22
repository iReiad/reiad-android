package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechTest {

    private fun piece(name: String): Piece =
        Json { ignoreUnknownKeys = true }.decodeFromString(
            PieceResponse.serializer(),
            requireNotNull(javaClass.getResourceAsStream("/fixtures/$name"))
                .readBytes().decodeToString(),
        ).article

    @Test
    fun `prose is read and furniture is not`() {
        val blocks = BodyParser.parse(piece("article-blocks.json").body).blocks
        val said = speakable(blocks)
        assertTrue(said.isNotEmpty(), "nothing at all was readable")

        /* A table's cells never reach the voice. This fixture has
           one, which is why it is the fixture used. */
        assertTrue(
            blocks.any { it is Block.Table },
            "the fixture has no table, so this proves nothing",
        )
        val tableAt = blocks.indexOfFirst { it is Block.Table }
        assertTrue(said.none { it.block == tableAt }, "a table was read out")
    }

    @Test
    fun `a photo caption is not read`() {
        val blocks = BodyParser.parse(piece("article-photo.json").body).blocks
        val photoAt = blocks.indexOfFirst { it is Block.Photo }
        assertTrue(photoAt >= 0, "the fixture has no photo")
        assertTrue(speakable(blocks).none { it.block == photoAt })
    }

    /** A list is read one item at a time rather than as one long
        sentence, because a synthesiser running six bullets
        together loses the shape of a list entirely. */
    @Test
    fun `each item of a list is its own utterance`() {
        val blocks = listOf(
            Block.Bullets(
                listOf(
                    listOf(Inline.Text("First thing")),
                    listOf(Inline.Text("Second thing")),
                ),
            ),
        )
        assertEquals(
            listOf("First thing", "Second thing"),
            speakable(blocks).map { it.text },
        )
    }

    @Test
    fun `a one character line is not spoken`() {
        val blocks = listOf(
            Block.Paragraph(listOf(Inline.Text("—"))),
            Block.Paragraph(listOf(Inline.Text("A real sentence."))),
        )
        assertEquals(listOf("A real sentence."), speakable(blocks).map { it.text })
    }

    /** An unknown block keeps its words, so it keeps its voice.
        Losing a paragraph because the parser had not met its
        wrapper is worse than reading it in the wrong tone. */
    @Test
    fun `words the parser could not place are still read`() {
        val blocks = listOf(Block.Unknown("aside", listOf(Inline.Text("Still words."))))
        assertEquals(listOf("Still words."), speakable(blocks).map { it.text })
    }

    @Test
    fun `a callout says its label and then what is in it`() {
        val blocks = listOf(
            Block.Callout(
                kind = CalloutKind.NOTE,
                label = listOf(Inline.Text("Note")),
                body = listOf(Block.Paragraph(listOf(Inline.Text("Mind the gap.")))),
            ),
        )
        assertEquals(listOf("Note", "Mind the gap."), speakable(blocks).map { it.text })
    }

    /** Markup inside a run is stripped: a synthesiser should not
        pause at a bold tag. */
    @Test
    fun `nested inline runs come out as one line of words`() {
        val blocks = listOf(
            Block.Paragraph(
                listOf(
                    Inline.Text("A "),
                    Inline.Strong(listOf(Inline.Text("bold"))),
                    Inline.Text(" word and a "),
                    Inline.Link("/x", listOf(Inline.Text("link"))),
                    Inline.Text("."),
                ),
            ),
        )
        assertEquals(listOf("A bold word and a link."), speakable(blocks).map { it.text })
    }

    /* ---------- which voice ---------- */

    @Test
    fun `one bangla character decides the language`() {
        assertEquals("bn", languageOf("This is mostly English but has একটা word"))
        assertEquals("en", languageOf("Entirely English."))
    }

    @Test
    fun `the exact tag wins, then the language, then english`() {
        val voices = listOf("en-GB", "en-US", "bn-BD", "de-DE")
        assertEquals("bn-BD", voiceFor(voices, "bn-BD"))
        assertEquals("bn-BD", voiceFor(voices, "bn"))
        assertEquals("en-GB", voiceFor(voices, "en"))
    }

    /** A machine with no Bangla voice reads Bangla in English
        rather than staying silent: mispronounced words are still
        words. */
    @Test
    fun `a language with no voice falls back to english`() {
        assertEquals("en-GB", voiceFor(listOf("en-GB", "de-DE"), "bn"))
    }

    /** But English falling back to English would be a loop that
        looks like a fallback, so it returns nothing and the
        caller uses the system default. */
    @Test
    fun `english with no english voice asks for nothing`() {
        assertEquals(null, voiceFor(listOf("de-DE"), "en"))
        assertEquals(null, voiceFor(emptyList(), "bn"))
    }

    @Test
    fun `the three paces are offered and named`() {
        assertEquals(Pace.entries.size, PACES.size)
        assertEquals(1.0f, paceOf(null).rate)
        assertEquals(0.8f, paceOf("slow").rate)
        assertEquals(Pace.NORMAL, paceOf("blistering"))
    }
}
