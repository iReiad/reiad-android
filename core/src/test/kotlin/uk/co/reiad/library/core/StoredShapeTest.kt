package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   What each synced key HOLDS, not just what it is called.

   `StorageKeyTest` asserts the names. This asserts the shapes,
   and it exists because the names being right is not enough: a
   key written in the wrong SHAPE syncs silently as nothing.

   It caught two in one afternoon.

   `reader-prefs` is a MARK, which reconciles on a `ts` inside the
   value, and this app was writing the record without one. A mark
   with no timestamp reads as zero, so a preference set on the
   phone loses to a laptop's every single exchange, for ever, with
   nothing failing anywhere.

   `<school>-last` is a MARK too, and this app was storing a bare
   lesson id. A bare id is not an object, has no `ts`, and is not
   even valid JSON, so the exchange skipped the key entirely and
   every bookmark would have stayed on the device that made it.

   Both look completely fine from inside the app.
   ============================================================ */
class StoredShapeTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** A MARK's value has to be an object carrying `ts`, or the
        rule it is filed under cannot do its job. */
    private fun assertIsMark(what: String, encoded: String) {
        val value = runCatching { json.parseToJsonElement(encoded) }.getOrNull()
        assertTrue(value is JsonObject, "$what is not even JSON, so an exchange skips it")
        val ts = (value as JsonObject)["ts"]
        assertTrue(ts is JsonPrimitive, "$what has no ts, so it loses every exchange")
        assertTrue(
            (ts as JsonPrimitive).content.toLongOrNull() != null,
            "$what's ts is not a number",
        )
    }

    @Test
    fun `a bookmark is a mark and carries its own timestamp`() {
        for (school in School.entries) {
            assertEquals(MergeRule.MARK, SyncKeys.ruleOf(ProgressKeys.last(school)))
        }
        assertIsMark(
            "a bookmark",
            json.encodeToString(
                Bookmark.serializer(),
                Bookmark(id = "stufe-1/tag-3", title = "T", stage = "stufe-1", ts = 1L),
            ),
        )
    }

    /** `id` is the only field anything decides anything by, so it
        has to survive whatever else changes. */
    @Test
    fun `a bookmark round trips with its id intact`() {
        val mark = Bookmark("basics-1/share", "শেয়ার", "basics-1", "/money/terms/share.html", 7L)
        val back = json.decodeFromString(
            Bookmark.serializer(),
            json.encodeToString(Bookmark.serializer(), mark),
        )
        assertEquals(mark, back)
    }

    @Test
    fun `preferences are a mark and carry their own timestamp`() {
        assertEquals(MergeRule.MARK, SyncKeys.ruleOf(PREFS_KEY))
        assertIsMark("reader-prefs", json.encodeToString(Prefs.serializer(), Prefs(ts = 1L)))
    }

    /** A SET is an array of strings. Anything else and the
        reconciliation has nothing to union. */
    @Test
    fun `every set key holds an array`() {
        val ids = json.parseToJsonElement("""["a","b"]""")
        assertTrue(ids is JsonArray)
        for (school in School.entries) {
            assertEquals(MergeRule.SET, SyncKeys.ruleOf(ProgressKeys.read(school)))
            assertEquals(MergeRule.SET, SyncKeys.ruleOf(ProgressKeys.checks(school)))
        }
    }

    /** A COUNT is a number. The two schools with a book are the
        only keys on that rule. */
    @Test
    fun `the only counts are the two book counters`() {
        val counts = SyncKeys.ALL.filterValues { it == MergeRule.COUNT }.keys
        assertEquals(setOf("deutsch-tag", "english-day"), counts)
    }

    /** And every key in the table has a rule, which is what makes
        walking the table a safe way to sync: a key with no rule
        would be silently skipped by anything that iterates. */
    @Test
    fun `every synced key has a rule`() {
        for ((key, rule) in SyncKeys.ALL) {
            assertTrue(key.isNotBlank())
            assertTrue(rule in MergeRule.entries)
        }
        assertTrue(SyncKeys.ALL.size >= 20, "the table lost entries: ${SyncKeys.ALL.size}")
    }
}
