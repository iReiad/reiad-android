package uk.co.reiad.library.diet

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.diet.DietDay
import uk.co.reiad.library.core.diet.DietEntry
import uk.co.reiad.library.core.diet.DietProfile

/* ============================================================
   A person's diet log, read with their own token.

   Four tables behind `auth.uid() = user_id`, the same
   arrangement the routine uses one directory along and for the
   same reason: a Worker in this path would be a second thing
   holding somebody's private log, and there is nothing it could
   do that the reader's own token cannot.

   ---- no filter names the reader, and that is deliberate ----

   The select policy is `user_id = auth.uid()`, so a read with no
   filter returns your own rows and nothing else. Adding one would
   be a second lock on a door that cannot open, and the site's own
   `getProfile()` note is the reason to be careful about the
   inverse: `public.profiles` is the ONE table here readable by
   anyone, and it is not one of these four.

   ---- and a day is an upsert, never a read-then-write ----

   `(user_id, entry_date)` is unique, so saving is one round trip
   that cannot race with the same person's laptop. What it costs
   is that a partial write would erase the columns it omits, which
   is why `explicitNulls = false`: a field the reader did not
   touch is not in the body at all.
   ============================================================ */

class Log(private val account: Account) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        /* A field nobody filled is ABSENT rather than null. On an
           upsert that is the difference between "leave the waist
           alone" and "the reader has no waist any more". */
        explicitNulls = false
        encodeDefaults = false
    }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T? =
        withContext(Dispatchers.IO) {
            val token = account.token() ?: return@withContext null
            runCatching { block(token) }.getOrNull()
        }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(token: String) {
        header("apikey", Supabase.KEY)
        header("Authorization", "Bearer $token")
    }

    /** The reader's answers to the setup questions, or null where
        they have not answered them: a diet tool cannot say
        anything at all without a height and a birth year. */
    suspend fun profile(): DietProfile? = withToken { token ->
        val text = http.get("${Supabase.REST}/diet_profile?select=*&limit=1") { auth(token) }
            .bodyAsText()
        json.decodeFromString(ListSerializer(DietProfile.serializer()), text).firstOrNull()
    }

    /** Every day from `since`, newest first. Every chart in the
        tool is "the last n days", so this is the one read. */
    suspend fun days(since: String): List<DietDay> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/diet_days?select=*&entry_date=gte.$since" +
                "&order=entry_date.desc",
        ) { auth(token) }.bodyAsText()
        json.decodeFromString(ListSerializer(DietDay.serializer()), text)
    }.orEmpty()

    /** What was eaten on one day, oldest first, which is the order
        it happened in. */
    suspend fun entries(date: String): List<DietEntry> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/diet_entries?select=*&entry_date=eq.$date" +
                "&order=at_time.asc.nullslast,id.asc",
        ) { auth(token) }.bodyAsText()
        json.decodeFromString(ListSerializer(DietEntry.serializer()), text)
    }.orEmpty()

    /* ---------- the three writes ----------

       EVERY ONE OF THEM RETURNS A SENTENCE OR NULL, and none of
       them returns a Boolean. `Days.kt` next door explains why at
       length and the short version is this: `diet_days.user_id`
       was `not null` with no default, so every insert this made
       was a null in a not-null column, PostgREST answered 400,
       the caller dropped the `false`, and the screen went on
       showing what had been typed. A write that can fail and
       cannot say so is a write that loses work quietly.

       The column has `default auth.uid()` now, from
       `20260823124900_own_rows_by_default.sql`, so `user_id` is
       still never named here: it fills itself in from the token
       and this device cannot get it wrong. */

    private suspend fun write(
        what: String,
        block: suspend (String) -> io.ktor.client.statement.HttpResponse,
    ): String? = withContext(Dispatchers.IO) {
        val token = account.token()
            ?: return@withContext "You are not signed in, so $what is on this phone only."
        runCatching {
            val answer = block(token)
            if (answer.status.isSuccess()) {
                null
            } else {
                "Could not save $what (${answer.status.value}). " +
                    answer.bodyAsText().take(160)
            }
        }.getOrElse { "Could not save $what: ${it.message ?: "no connection"}." }
    }

    /**
     * One day, saved whole.
     *
     * The columns the caller left null are not in the body, so
     * this is a partial update as well as an insert: a reader
     * typing a weight does not erase the waist they measured this
     * morning.
     */
    suspend fun saveDay(day: DietDay): String? = write("today") { token ->
        val body = json.encodeToString(ListSerializer(DietDay.serializer()), listOf(day))
        http.post("${Supabase.REST}/diet_days?on_conflict=user_id,entry_date") {
            auth(token)
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** One thing eaten, added. */
    suspend fun addEntry(entry: DietEntry): String? = write("what you ate") { token ->
        val body = json.encodeToString(ListSerializer(DietEntry.serializer()), listOf(entry))
        http.post("${Supabase.REST}/diet_entries") {
            auth(token)
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    /** And removed. By id, which is the only thing that can
        identify one of several identical lines. */
    suspend fun removeEntry(id: String): String? = write("that change") { token ->
        http.delete("${Supabase.REST}/diet_entries?id=eq.$id") {
            auth(token)
            header("Prefer", "return=minimal")
        }
    }
}
