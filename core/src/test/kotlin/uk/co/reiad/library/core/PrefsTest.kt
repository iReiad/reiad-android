package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The keys and the values, by name.

   Every string asserted here is in real browsers and, for a
   signed-in reader, in `public.progress`. This file exists for
   the same reason `StorageKeyTest` does: a rename is not a
   refactor, it is somebody losing a setting they made.
   ============================================================ */
class PrefsTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `the keys are the site's own`() {
        assertEquals("reader-prefs", PREFS_KEY)
        assertEquals("theme", THEME_KEY)
        assertEquals("tool-lang", TOOL_LANG_KEY)
        assertEquals("audience", AUDIENCE_KEY)
        assertEquals("track", TRACK_KEY)
    }

    /** Every table has an entry per value. A value with no option
        is a setting a reader can hold and never choose, which is
        how a preference becomes unreachable. */
    @Test
    fun `every value is offered`() {
        assertEquals(Theme.entries.size, THEMES.size)
        assertEquals(Finish.entries.size, GLASSES.size)
        assertEquals(Blur.entries.size, BLURS.size)
        assertEquals(Veil.entries.size, VEILS.size)
        assertEquals(Theme.entries.toSet(), THEMES.map { it.id }.toSet())
        assertEquals(Finish.entries.toSet(), GLASSES.map { it.id }.toSet())
    }

    @Test
    fun `every option id is the site's own`() {
        assertEquals(listOf("system", "light", "dark"), Theme.entries.map { it.id })
        assertEquals(listOf("frost", "paper", "plain"), Finish.entries.map { it.id })
        assertEquals(listOf("soft", "normal", "deep"), Blur.entries.map { it.id })
        assertEquals(listOf("clear", "normal", "dense"), Veil.entries.map { it.id })
    }

    /** The field names inside somebody's stored value. Kotlin's
        own naming would have written `toolLang` into a record the
        browser then failed to read. */
    /** The record's fields are the site's, exactly: no more and
        no fewer.

        Two ways this went wrong at once. `ts` was missing, and
        `reader-prefs` is the one key in the sync table whose rule
        is MARK: a record with no `ts` reads as timestamp zero and
        loses every exchange, so a preference set on this phone
        would be overwritten by a laptop's for ever, silently.

        And `theme` was IN it, which it is not on the site: the
        theme lives in its own key so the pre-paint boot script
        can read it without parsing JSON. A device writing it into
        the record writes a field nothing reads. */
    @Test
    fun `the stored shape is exactly the shape the browser writes`() {
        val text = json.encodeToString(Prefs.serializer(), Prefs())
        val fields = Regex("\"([a-z]+)\":").findAll(text).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf("text", "measure", "lang", "glass", "blur", "veil", "ts"),
            fields,
            "the record must hold what aab/src/prefs.ts writes, field for field",
        )
    }

    /** And a mark that always loses is a mark nobody can see
        losing, so the rule is asserted here as well as the
        field. */
    @Test
    fun `reader-prefs is a mark and carries its own timestamp`() {
        assertEquals(MergeRule.MARK, SyncKeys.ruleOf(PREFS_KEY))
        val saved = json.decodeFromString(Prefs.serializer(), """{"ts":1700000000000}""")
        assertEquals(1_700_000_000_000L, saved.ts)
    }

    /** The defaults are the site's defaults, which matters
        because a reader who has never opened the settings should
        get the same page on both. */
    @Test
    fun `an empty record is the site's defaults`() {
        val prefs = json.decodeFromString(Prefs.serializer(), "{}")
        assertEquals(Theme.SYSTEM, prefs.themeChoice)
        assertEquals(Finish.FROST, prefs.finish)
        assertEquals(Blur.NORMAL, prefs.blurChoice)
        assertEquals(Veil.NORMAL, prefs.veilChoice)
        assertEquals("bn", prefs.lang)
    }

    /** A field this app does not use yet still survives a round
        trip. A device that drops what it does not understand is a
        device that resets a setting somebody made on their
        laptop, silently, the first time they open the app. */
    @Test
    fun `settings this app does not use yet are not lost`() {
        val stored = """{"text":"large","measure":"wide","lang":"en",
            "glass":"paper","blur":"deep","veil":"dense","ts":7}"""
        val prefs = json.decodeFromString(Prefs.serializer(), stored)
        assertEquals("large", prefs.text)
        assertEquals("wide", prefs.measure)
        assertEquals("en", prefs.lang)
        assertEquals(7L, prefs.ts)
        val back = json.encodeToString(Prefs.serializer(), prefs)
        assertTrue("\"large\"" in back && "\"wide\"" in back && "\"en\"" in back)
    }

    /** The numbers are the stylesheet's: 14px frost, 9px paper,
        0.72 veil, and the multipliers around them. */
    @Test
    fun `the finish decides the radius and the reader scales it`() {
        assertEquals(14.0, Prefs().blurRadius(), 1e-9)
        assertEquals(9.0, Prefs(glass = "paper").blurRadius(), 1e-9)
        assertEquals(23.8, Prefs(blur = "deep").blurRadius(), 1e-9)
        assertEquals(0.72, Prefs().veilAlpha(), 1e-9)
    }

    /** `plain` is a finish in its own right, not the others with
        a feature switched off, and it answers for itself rather
        than being special-cased by whatever draws a surface. */
    @Test
    fun `plain is not glass and says so itself`() {
        val plain = Prefs(glass = "plain", blur = "deep", veil = "clear")
        assertEquals(0.0, plain.blurRadius(), 1e-9)
        assertEquals(1.0, plain.veilAlpha(), 1e-9)
    }

    /** A value nobody recognises resolves to the default rather
        than throwing. A preference written by a newer version of
        the site should leave the app usable. */
    @Test
    fun `an unknown value falls back rather than failing`() {
        assertEquals(Theme.SYSTEM, themeOf("sepia"))
        assertEquals(Finish.FROST, finishOf(null))
        assertEquals(Blur.NORMAL, blurOf(""))
        assertEquals(Veil.NORMAL, veilOf("thick"))
    }

    /* ---------- the audience reorders and never hides ---------- */

    @Test
    fun `the order the site sends is the order the reader gets`() {
        val groups = listOf("learn", "make", "read", "work", "you")
        assertEquals(
            listOf("work", "make", "read", "learn", "you"),
            orderFor(groups, listOf("work", "make", "read", "learn", "you")),
        )
    }

    /** A group the order does not name is APPENDED, never
        dropped. That is the "reorders, never hides" rule as code:
        a sixth group added to the site tomorrow turns up at the
        bottom of both orders instead of vanishing from one. */
    @Test
    fun `a group the order forgets is still shown`() {
        val groups = listOf("learn", "make", "read", "work", "you", "shop")
        val got = orderFor(groups, listOf("work", "learn"))
        assertEquals(listOf("work", "learn", "make", "read", "you", "shop"), got)
        assertEquals(groups.toSet(), got.toSet())
    }

    /** And an order naming a group the site no longer has does
        not leave a hole. */
    @Test
    fun `an order naming a group that is gone drops nothing`() {
        val got = orderFor(listOf("learn", "work"), listOf("work", "archive", "learn"))
        assertEquals(listOf("work", "learn"), got)
    }

    @Test
    fun `no order at all is the site's own order`() {
        val groups = listOf("learn", "make", "read")
        assertEquals(groups, orderFor(groups, null))
        assertEquals(groups, orderFor(groups, emptyList()))
    }
}
