package uk.co.reiad.library.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uk.co.reiad.library.core.LadderResponse
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.ProgressKeys
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.SiteManifest

/* ============================================================
   Talking to the site, and remembering what the reader did.

   Three rules from the site, and all three are load bearing.

   **The ladder is the server's and the ticks are the device's.**
   A ladder is fetched; a tick is never sent anywhere by this
   file. What a reader has read is not a fact the server has, and
   putting it on an account is a signed-in concern for phase 2.

   **A storage key is a fact.** The keys written here are the same
   strings the browser writes and `public.progress` holds, spelled
   by `ProgressKeys`. Nothing in this file invents a name.

   **Offline is a state, not a cache accident.** Every fetch
   writes its raw JSON down and every failure reads it back, so a
   second launch with no network is the site as it last was rather
   than an error screen. That is a decision rather than a
   defensive habit: every endpoint under the api prefix answers
   no-store whatever a handler asks for, measured against the live
   site, so there is no HTTP cache to lean on and the app has to
   keep its own.

   (The api prefix is written out in words above. Kotlin block
   comments NEST, so a slash-star inside one opens a second
   comment and the closing marker shuts only that. Writing the
   glob cost this file its whole body once, and the error named a
   line eighty further down.)
   ============================================================ */

private val Context.store by preferencesDataStore(name = "reiad")

class Reiad(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = true
    }

    /* ---------- what the site says ---------- */

    /** The site's own furniture: the menu, the accents, the
        schools with ladders, the counts. Everything the app draws
        a list from comes from here rather than from a copy in the
        binary, which is what makes a fifth school appear with no
        app release. */
    suspend fun manifest(): Cached<SiteManifest> =
        fetch("$SITE_ORIGIN/api/site", "cache:site", SiteManifest.serializer())

    suspend fun ladder(school: String): Cached<LadderResponse> =
        fetch("$SITE_ORIGIN/api/schools/$school", "cache:ladder:$school", LadderResponse.serializer())

    suspend fun lesson(school: String, stage: String, slug: String): Cached<LessonResponse> =
        fetch(
            "$SITE_ORIGIN/api/schools/$school/$stage/$slug",
            "cache:lesson:$school/$stage/$slug",
            LessonResponse.serializer(),
        )

    /** Network first, and the last good answer when that fails.

        Network first rather than cache first because prose is
        edited in the Studio and a reader with a connection should
        get the current words. The stored copy is the answer to no
        network, not a way to avoid asking. */
    private suspend fun <T> fetch(
        url: String,
        cacheKey: String,
        serializer: DeserializationStrategy<T>,
    ): Cached<T> {
        val live = runCatching { http.get(url).bodyAsText() }
        if (live.isSuccess) {
            val text = live.getOrThrow()
            val parsed = runCatching { json.decodeFromString(serializer, text) }
            if (parsed.isSuccess) {
                context.store.edit { it[stringPreferencesKey(cacheKey)] = text }
                return Cached(parsed.getOrThrow(), stale = false)
            }
        }
        val saved = context.store.data.first()[stringPreferencesKey(cacheKey)]
            ?: return Cached(null, stale = false, failed = live.exceptionOrNull())
        val fromCache = runCatching { json.decodeFromString(serializer, saved) }
        return Cached(fromCache.getOrNull(), stale = true, failed = live.exceptionOrNull())
    }

    /* ---------- what the reader did ----------

       Stored as the same JSON the browser stores: an array of ids
       under the school's own key. Keeping the wire format means a
       later sync is a comparison rather than a translation. */

    private fun key(name: String) = stringPreferencesKey(name)

    fun ticks(school: School): Flow<Set<String>> =
        context.store.data.map { prefs -> decode(prefs[key(ProgressKeys.read(school))]) }

    suspend fun ticksNow(school: School): Set<String> = ticks(school).first()

    /** The money school's tick is a button a reader presses. The
        other three mark a lesson on opening. That difference is
        the school's, not this file's, so both verbs exist and the
        screen says which one it means. */
    suspend fun toggleTick(school: School, lessonId: String): Set<String> {
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val name = key(ProgressKeys.read(school))
            val now = decode(prefs[name])
            after = if (lessonId in now) now - lessonId else now + lessonId
            prefs[name] = encode(after)
        }
        return after
    }

    suspend fun markRead(school: School, lessonId: String): Set<String> {
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val name = key(ProgressKeys.read(school))
            val now = decode(prefs[name])
            after = now + lessonId
            if (after != now) prefs[name] = encode(after)
        }
        return after
    }

    /* The serializer is named rather than reified, so that a
       release build stripping type information cannot change what
       this reads and writes. These strings are somebody's ticks. */
    private val idList = ListSerializer(String.serializer())

    private fun decode(raw: String?): Set<String> = when {
        raw.isNullOrBlank() -> emptySet()
        else -> runCatching { json.decodeFromString(idList, raw).toSet() }
            .getOrDefault(emptySet())
    }

    private fun encode(ids: Set<String>): String = json.encodeToString(idList, ids.toList())
}

/** An answer, and whether it came off the shelf.

    `stale` is shown rather than hidden. The site refuses to serve
    yesterday's headlines silently, and a reader deserves to know
    they are looking at what the app last saw. */
data class Cached<T>(
    val value: T?,
    val stale: Boolean,
    val failed: Throwable? = null,
)
