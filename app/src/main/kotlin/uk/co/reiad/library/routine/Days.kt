package uk.co.reiad.library.routine

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.routine.Band
import uk.co.reiad.library.core.routine.Entry
import uk.co.reiad.library.core.routine.RoutineShape
import uk.co.reiad.library.core.routine.Task

/* ============================================================
   A person's routine, and their days.

   Two tables in Supabase, read with the reader's own token under
   the same row-level security the reading list has. There is no
   Worker in this path and there should not be one: it would be a
   second thing holding somebody's private days.

   `public.routines` is the shape, one row per routine.
   `public.routine_entries` is one row per person per DAY, which
   is what makes an upsert on `(user_id, entry_date)` the whole of
   saving: no read-then-decide, one round trip, and it cannot race
   with the same person's laptop.

   ---- what is never written ----

   A zero. Unticking DELETES the key rather than writing one,
   because an absent key is a day with nothing to say about that
   task and a zero is a judgement wearing a number's clothes.
   Every piece of arithmetic in `core/routine` reads that
   distinction, and a device that wrote zeroes would poison it
   from the outside.
   ============================================================ */

@Serializable
data class RoutineRow(
    val id: String,
    val name: String = "",
    val bands: List<Band> = emptyList(),
    val tasks: List<Task> = emptyList(),
    @SerialName("is_active") val active: Boolean = true,
) {
    val shape: RoutineShape get() = RoutineShape(bands, tasks)
}

class Days(private val account: Account) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    private suspend fun <T> withToken(block: suspend (String) -> T): T? {
        val token = account.token() ?: return null
        return runCatching { block(token) }.getOrNull()
    }

    /** The reader's active routine, or null if they have none.

        No filter naming a user, and that is not an omission: the
        policy is `user_id = auth.uid()`, so a read with no filter
        returns your own rows and nothing else. */
    suspend fun routine(): RoutineRow? = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/routines?select=id,name,bands,tasks,is_active" +
                "&is_active=eq.true&order=updated_at.desc&limit=1",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(RoutineRow.serializer()), text).firstOrNull()
    }

    /** Every day from `since` onwards, which is how every chart in
        the tool is fed: they are all "the last n days". */
    suspend fun entries(since: String): List<Entry> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/routine_entries" +
                "?select=entry_date,marks,mood,note,chose" +
                "&entry_date=gte.$since&order=entry_date.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Entry.serializer()), text)
    }.orEmpty()

    /**
     * One day, saved whole. Null on success, a sentence on
     * failure.
     *
     * **IT USED TO RETURN A BOOLEAN AND THAT IS WHY A WHOLE DAY
     * WENT MISSING.** `routine_entries.user_id` was `not null`
     * with no default, so every insert this made was a null in a
     * not-null column: PostgREST answered 400, this answered
     * `false`, and the caller dropped it on the floor. The screen
     * had already drawn the marks. A reader marked a full day,
     * opened the site, and found nothing.
     *
     * So the type is the fix as much as the column is. A save
     * that can fail must be able to SAY so, and a caller holding
     * a `Boolean` it does not read is the same bug waiting for
     * the next table.
     *
     * An UPSERT on `(user_id, entry_date)`, which the unique
     * index makes the whole of saving. `user_id` is still not
     * named here: `20260823124900_own_rows_by_default.sql` gives
     * the column `default auth.uid()`, so it fills itself in from
     * the token and this device cannot get it wrong or be talked
     * into getting it wrong.
     */
    suspend fun save(routineId: String, entry: Entry): String? = withContext(Dispatchers.IO) {
        /* NOT through `withToken`, and that is deliberate. It
           answers `null` for "no token" AND for "the block threw"
           AND, here, for "it worked", which is three different
           things collapsed into one value. Collapsing them is
           what this whole function is being rewritten to stop. */
        val token = account.token()
            ?: return@withContext "You are not signed in, so today is on this phone only."

        val body = json.encodeToString(
            ListSerializer(Written.serializer()),
            listOf(
                Written(
                    routineId = routineId,
                    date = entry.date,
                    /* Zeroes are stripped on the way out, not
                       just left unwritten by the UI: a mark that
                       reached here as nought is a bug upstream
                       and this is the last place to catch it. */
                    marks = entry.marks.filterValues { it > 0 },
                    mood = entry.mood?.ifBlank { null },
                    note = entry.note?.ifBlank { null },
                    chose = entry.chose?.ifBlank { null },
                ),
            ),
        )

        runCatching {
            val answer = http.post(
                "${Supabase.REST}/routine_entries?on_conflict=user_id,entry_date",
            ) {
                header("apikey", Supabase.KEY)
                header("Authorization", "Bearer $token")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (answer.status.value in 200..299) {
                null
            } else {
                /* What the database actually said. A reader
                   cannot act on "not saved" and the next person
                   reading a report can act on the sentence
                   Postgres wrote. */
                "Today was not saved (${answer.status.value}). " +
                    answer.bodyAsText().take(160)
            }
        }.getOrElse { "Today was not saved: ${it.message ?: "no connection"}." }
    }

    @Serializable
    private data class Written(
        @SerialName("routine_id") val routineId: String,
        @SerialName("entry_date") val date: String,
        val marks: Map<String, Double>,
        val mood: String? = null,
        val note: String? = null,
        val chose: String? = null,
    )
}
