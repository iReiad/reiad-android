package uk.co.reiad.library.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uk.co.reiad.library.core.LadderResponse
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.SiteManifest

/* ============================================================
   Talking to the site, and remembering what the reader did.

   Two rules from the site, and both are load bearing.

   **The ladder is the server's and the ticks are the device's.**
   A ladder is fetched; a tick is never sent anywhere by this
   file. What a reader has read is not a fact the server has, and
   syncing it to an account is a signed-in concern that arrives in
   phase 2.

   **A storage key is a fact.** The keys written here are the same
   strings the browser writes and `public.progress` holds, spelled
   by `ProgressKeys`. Nothing in this file invents a name.

   Every endpoint under `/api` answers `no-store` whatever a
   handler sets, which is measured and written down in the site's
   own `functions/api/site.ts`, so the app holds its own copy
   rather than relying on an HTTP cache that will not be there.

   (And note that the path above is written without a star.
   Kotlin block comments NEST, so a slash-star inside one opens a
   second comment and the file's closing star-slash then closes
   only that. Writing the glob cost this file its whole body once,
   and the error it produced named a line 80 lines further down.)
   ============================================================ */

private val Context.store by preferencesDataStore(name = "reiad")

class Reiad(private val context: Context) {

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
        }
        expectSuccess = true
    }

    /* ---------- what the site says ---------- */

    suspend fun manifest(): SiteManifest = http.get("$SITE_ORIGIN/api/site").body()

    suspend fun ladder(school: String): LadderResponse =
        http.get("$SITE_ORIGIN/api/schools/$school").body()

    suspend fun lesson(school: String, stage: String, slug: String): LessonResponse =
        http.get("$SITE_ORIGIN/api/schools/$school/$stage/$slug").body()

    /* ---------- what the reader did ----------

       Stored as the same JSON the browser stores: an array of ids
       under the school's own key. Keeping the wire format means a
       later sync is a comparison rather than a translation. */

    private fun key(name: String) = stringPreferencesKey(name)

    fun ticks(school: School): Flow<Set<String>> {
        val name = uk.co.reiad.library.core.ProgressKeys.read(school)
        return context.store.data.map { prefs -> decode(prefs[key(name)]) }
    }

    suspend fun ticksNow(school: School): Set<String> = ticks(school).first()

    /** Opening is not finishing in the money school: the tick is a
        button a reader presses. The other three mark a lesson on
        opening, and that difference is the school's, not this
        file's, so it takes the school as an argument rather than
        deciding. */
    suspend fun toggleTick(school: School, lessonId: String): Set<String> {
        val name = uk.co.reiad.library.core.ProgressKeys.read(school)
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val now = decode(prefs[key(name)])
            after = if (lessonId in now) now - lessonId else now + lessonId
            prefs[key(name)] = encode(after)
        }
        return after
    }

    suspend fun markRead(school: School, lessonId: String) {
        val name = uk.co.reiad.library.core.ProgressKeys.read(school)
        context.store.edit { prefs ->
            val now = decode(prefs[key(name)])
            if (lessonId !in now) prefs[key(name)] = encode(now + lessonId)
        }
    }

    /* The serializer is named rather than reified, so that a
       release build stripping type information cannot change what
       this reads and writes. These strings are somebody's ticks. */
    private val idList = ListSerializer(String.serializer())

    private fun decode(raw: String?): Set<String> = when {
        raw.isNullOrBlank() -> emptySet()
        else -> runCatching { Json.decodeFromString(idList, raw).toSet() }
            .getOrDefault(emptySet())
    }

    private fun encode(ids: Set<String>): String = Json.encodeToString(idList, ids.toList())
}
