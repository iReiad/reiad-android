package uk.co.reiad.library.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/* ============================================================
   Offline as a thing a reader ASKED FOR.

   The site's service worker keeps what you visited plus the hubs,
   which is the best a browser can do and is entirely incidental:
   a reader who wants a school on a plane has to open every lesson
   of it first, and has no way of knowing whether they got them
   all.

   An app is allowed to want things. FOLLOWING a school downloads
   every lesson of it and keeps them, so "will this work on the
   plane" has a yes or a no rather than a maybe.

   ---- what is deliberately NOT here ----

   A size limit, an eviction policy and a "manage storage" screen
   with a bar chart on it. The whole corpus is four schools of
   prose: two hundred and fifty-one lessons of text, which is
   about a megabyte and a half. There is nothing to manage. A
   reader who wants it gone presses Forget, and Android's own
   storage settings can clear the lot.

   ---- the shelf is a SET OF KEYS, not a second store ----

   Nothing is copied. `Reiad.fetch` already writes every answer to
   the one DataStore under `cache:<what>`, so following a school
   means asking for its lessons once; the cache does the keeping.
   That is why this file is fifty lines and not five hundred, and
   it is why a followed school and a browsed one are byte for byte
   the same thing on disk.
   ============================================================ */

/** Which schools the reader asked to keep. */
class Shelf(private val context: Context) {

    private val followed = stringPreferencesKey("shelf:followed")

    /** The school keys, as a set.

        Stored as one comma-separated string rather than a
        `stringSetPreferencesKey`, for the reason every other set
        in this app is: the wire format is what `Sync` reads, and
        a set that is stored differently is a set that has to be
        translated the day somebody carries it between devices. */
    fun schools(): Flow<Set<String>> =
        context.store.data.map { prefs -> split(prefs[followed]) }

    suspend fun following(school: String): Boolean = schools().first().contains(school)

    /** Follow, or stop. Returns what the shelf holds afterwards. */
    suspend fun toggle(school: String): Set<String> {
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val now = split(prefs[followed])
            after = if (school in now) now - school else now + school
            prefs[followed] = after.sorted().joinToString(",")
        }
        return after
    }

    private fun split(raw: String?): Set<String> =
        raw.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** What is actually held for a school, and how much of it.

    Counted from the cache rather than remembered, which is the
    whole point of it being a report: a number this app kept for
    itself would say "held" about a lesson somebody cleared in
    Android's settings. */
data class Held(val lessons: Int, val bytes: Long)

suspend fun heldFor(context: Context, school: String): Held {
    val prefix = "cache:lesson:$school/"
    var lessons = 0
    var bytes = 0L
    val all = context.store.data.first().asMap()
    for ((key, value) in all) {
        if (!key.name.startsWith(prefix)) continue
        lessons += 1
        bytes += (value as? String)?.length?.toLong() ?: 0L
    }
    return Held(lessons, bytes)
}

/** Everything this app is holding, over every school. */
suspend fun heldAll(context: Context): Held {
    var lessons = 0
    var bytes = 0L
    for ((key, value) in context.store.data.first().asMap()) {
        if (!key.name.startsWith("cache:")) continue
        if (key.name.startsWith("cache:lesson:")) lessons += 1
        bytes += (value as? String)?.length?.toLong() ?: 0L
    }
    return Held(lessons, bytes)
}

/**
 * Forget everything cached, and NOTHING ELSE.
 *
 * The prefix is the guard and it has to be: this is the same
 * DataStore that holds every tick, every bookmark, the reader's
 * preferences and their session. A `clear()` here would sign
 * somebody out and lose a year of ticks while calling itself
 * "free up space".
 */
suspend fun forgetHeld(context: Context) {
    context.store.edit { prefs ->
        val names = prefs.asMap().keys.filter { it.name.startsWith("cache:") }
        for (name in names) prefs.remove(stringPreferencesKey(name.name))
    }
}

/** "1.4 MB", "812 KB", the way a person says it. */
fun sizeWords(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    bytes > 0 -> "$bytes bytes"
    else -> "nothing yet"
}
