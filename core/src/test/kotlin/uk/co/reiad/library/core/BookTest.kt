package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The book the app receives, and the key it does not.

   The fixture is what `/api/book/stufe-1` actually sends, dumped
   from the route rather than written by hand. The site's own test
   proves the endpoint strips the answers; this proves the app
   would notice if it stopped, which is a different question and
   the one that matters on this side.
   ============================================================ */
class BookTest {

    private val book: BookResponse = Json { ignoreUnknownKeys = true }.decodeFromString(
        BookResponse.serializer(),
        requireNotNull(javaClass.getResourceAsStream("/fixtures/book-stufe-1.json"))
            .readBytes().decodeToString(),
    )

    @Test
    fun `a book arrives whole`() {
        assertEquals("deutsch", book.book.school)
        assertEquals(30, book.book.days.size)
        assertTrue(book.book.days.all { it.n > 0 })
        assertEquals((1..30).toList(), book.book.days.map { it.n })
    }

    @Test
    fun `every day has the same four parts`() {
        for (day in book.book.days) {
            assertTrue(day.bn.isNotBlank(), "day ${day.n} has no title")
            assertTrue(day.pattern.shape.isNotBlank(), "day ${day.n} has no pattern")
            assertTrue(day.watch.isNotEmpty(), "day ${day.n} has nothing to read aloud")
            assertTrue(day.say.isNotEmpty(), "day ${day.n} has nothing to translate")
            assertTrue(day.heart.bn.isNotBlank(), "day ${day.n} has no writing task")
        }
    }

    /** No answer reaches this app in the book. `BookPrompt` has
        no field for one, so an endpoint that started sending them
        would be dropping them here, and this asserts on the BYTES
        rather than on the parsed object for exactly that reason:
        a field with nowhere to land is invisible after parsing.

        `ManifestSurfaceTest` is the other half. It fails on a
        field the site sends and nothing here reads, so between
        them an answer cannot arrive quietly OR be quietly
        ignored. */
    @Test
    fun `no answer is in the bytes`() {
        val raw = requireNotNull(javaClass.getResourceAsStream("/fixtures/book-stufe-1.json"))
            .readBytes().decodeToString()
        assertTrue(
            "\"a\":" !in raw,
            "the book fixture carries an answer field. Either the endpoint stopped " +
                "stripping them, or the fixture was refreshed from a broken one.",
        )
    }

    /** Only the first book of each school has a sound key: after
        that the sounds are behind you. Stufe 1 is the first. */
    @Test
    fun `the first book carries its sound key`() {
        assertTrue(book.book.sounds.isNotEmpty())
        assertTrue(book.book.sounds.all { it.pair.isNotBlank() })
    }

    /** A day's tick is filed under the school's own shape, and
        the two schools disagree on purpose: `stufe-1/tag-3`
        against `term-1/day-3`. Both are in real browsers, and the
        shared engine on the web built the German shape for both
        once, so an English day could be ticked and came back
        unticked. */
    @Test
    fun `a day is filed under its school's own shape`() {
        assertEquals("stufe-1/tag-3", dayId(School.DEUTSCH, "stufe-1", 3))
        assertEquals("term-1/day-3", dayId(School.ENGLISH, "term-1", 3))
    }

    /* ---------- the digits ---------- */

    /** Asserted on the CODE POINT, never against a typed string.

        Devanagari zero is U+0966 and Bengali zero is U+09E6, and
        they are all but identical at any size a diff is read at.
        A test comparing this function's output to a literal
        somebody typed would agree with the mistake it exists to
        catch, which is exactly how the wrong digits reached a
        whole site once. */
    @Test
    fun `a bangla numeral is the bengali block and not the devanagari one`() {
        val eight = bengaliNumber(8)
        assertEquals(1, eight.length)
        assertEquals(0x09E6 + 8, eight[0].code, "expected BENGALI EIGHT")

        val thirty = bengaliNumber(30)
        assertEquals(0x09E6 + 3, thirty[0].code)
        assertEquals(0x09E6 + 0, thirty[1].code)

        /* And nothing in the Devanagari block, whatever the
           number. */
        assertTrue(
            (0..999).flatMap { bengaliNumber(it).toList() }
                .none { it.code in 0x0966..0x096F },
            "a Devanagari digit got in",
        )
    }

    @Test
    fun `anything that is not a digit is left alone`() {
        assertEquals("-", bengaliNumber(-1).take(1))
    }

    /** And what a learner types is filed per day per box, under
        the school's own write key, which never leaves the
        device. */
    @Test
    fun `a written box is filed by day and slot`() {
        assertEquals("deutsch-schrift", ProgressKeys.write(School.DEUTSCH))
        assertEquals("tag-3:heart", writeSlot(3, "heart"))
        assertEquals("tag-12:say-2", writeSlot(12, "say-2"))
    }
}
