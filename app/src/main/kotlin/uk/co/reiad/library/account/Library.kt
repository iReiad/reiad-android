package uk.co.reiad.library.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import uk.co.reiad.library.core.Kept
import uk.co.reiad.library.core.Profile
import uk.co.reiad.library.core.Scenario
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.Target
import uk.co.reiad.library.core.encodeComponent

/* ============================================================
   What an account holds that is not a tick.

   A reading list with notes on it, and targets. Neither has a
   local copy and that is deliberate rather than an omission:
   progress has one because four schools have read localStorage
   since before there were accounts, and a reader with no account
   still gets all of it. Nothing here has that history and nothing
   here works signed out, so a second copy would be a second
   record to keep in step for nobody's benefit.

   ---- the filter on a read is not a second lock, except once ----

   Every table here is `auth.uid() = user_id`, so a read with no
   filter returns your own rows and nothing else. That is true of
   `library` and `targets` and it is why neither call below names
   a user.

   It is NOT true of `profiles`, which is the one table on this
   site whose select policy is `using (true)`, because a comment
   has to show its author's name to somebody signed out. The site
   learned that the hard way: `getProfile()` asked for
   `profiles?select=...&limit=1` and PostgREST answered with
   whichever row the planner reached first, so with two accounts
   SAVING your profile is what made the next read return somebody
   else's. Anything added here that touches `profiles` carries
   `id=eq.<me>`.
   ============================================================ */

class Library(private val account: Account) {

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

    /** Whether this account can open the courses shelf.

        Asked of the endpoint rather than guessed from a claim:
        `/api/courses` answers 200 for the admin and 401 for
        everybody else, and the server's answer is the only fact
        there is. Nothing is granted here; the card this gates
        opens the site, which checks again.

        A READ, not a write, which is why it may answer a Boolean
        where every write in this file answers a sentence: false
        on any doubt is the correct answer here, because a card
        that says "admin only" shown to somebody the site will
        refuse is a promise the screen cannot keep. */
    suspend fun courses(): Boolean {
        val token = account.token() ?: return false
        return runCatching {
            http.get("${uk.co.reiad.library.core.SITE_ORIGIN}/api/courses") {
                header("Authorization", "Bearer $token")
            }.status.isSuccess()
        }.getOrDefault(false)
    }

    /* ---------- the reading list ---------- */

    /** Everything saved or noted, newest first.

        Both in one call, because they are one row: `saved` and
        `note` are two columns of one fact about one page, and a
        trigger removes the row once both have gone. */
    suspend fun kept(): List<Kept> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/library?select=id,url,title,kind,saved,note&order=updated_at.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Kept.serializer()), text)
    }.orEmpty()

    /* ---------- how a write answers ----------

       A SENTENCE OR NULL, never a Boolean, on every write in
       this file. The routine lost a whole day to a Boolean
       nobody read (`Days.kt` tells it in full), and two writes
       here were worse than that: `keep` and `removeScenario`
       returned `true` without looking at the status at all, so a
       note that hit a 400 was reported saved to the caller AND
       to the reader.

       The sentence carries what the database actually said,
       because "not saved" is not actionable and "violates check
       constraint profiles_pace_check" is. */
    private suspend fun write(
        what: String,
        block: suspend (String) -> io.ktor.client.statement.HttpResponse,
    ): String? {
        val token = account.token()
            ?: return "You are not signed in, so $what stayed on this phone."
        return runCatching {
            val answer = block(token)
            if (answer.status.isSuccess()) {
                null
            } else {
                "Could not save $what (${answer.status.value}). " +
                    answer.bodyAsText().take(160)
            }
        }.getOrElse { "Could not save $what: ${it.message ?: "no connection"}." }
    }

    /** Saves, unsaves, or writes a note.

        An UPSERT rather than a read-then-decide, which is one
        round trip instead of two and cannot race with the same
        reader's laptop. `user_id` is the column's default from
        the token, so this device never names whose row it is
        writing: it cannot get that wrong and cannot be talked
        into getting it wrong.

        Each control writes only ITS OWN column. The site's own
        note is why: a Save and a note are two controls over one
        row, and one that sent the whole row would overwrite what
        the other had just put there. */
    suspend fun keep(
        url: String,
        title: String,
        kind: String,
        saved: Boolean? = null,
        note: String? = null,
    ): String? = write(if (note != null) "your note" else "that") { token ->
        val row = buildJsonObject {
            put("url", url)
            put("title", title)
            put("kind", kind)
            saved?.let { put("saved", it) }
            note?.let { put("note", it) }
        }
        http.post("${Supabase.REST}/library?on_conflict=user_id,url") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(buildJsonArray { add(row) }.toString())
        }
    }

    /* ---------- saved scenarios ---------- */

    /** Every saved check, newest first.

        No filter, because `scenarios` is `auth.uid() = user_id`
        like everything else here except `profiles`: a read with
        no filter returns your own rows and nothing else. */
    suspend fun scenarios(tool: String = "stock"): List<Scenario> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/scenarios" +
                "?select=id,tool,name,inputs,summary,updated_at" +
                "&tool=eq.${encodeComponent(tool)}&order=updated_at.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Scenario.serializer()), text)
    }.orEmpty()

    /** Saves one.

        `name` and `summary` are cut to the constraint's lengths
        HERE as well as there, because a 400 arriving after the
        button is pressed is a form that looked finished. */
    suspend fun saveScenario(
        tool: String,
        name: String,
        query: String,
        summary: String,
    ): String? = write("the scenario") { token ->
        val row = buildJsonObject {
            put("tool", tool)
            put("name", name.trim().take(80))
            putJsonObject("inputs") { put("query", query) }
            put("summary", summary.take(200))
        }
        http.post("${Supabase.REST}/scenarios") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(buildJsonArray { add(row) }.toString())
        }
    }

    suspend fun removeScenario(id: String): String? = write("that change") { token ->
        http.delete("${Supabase.REST}/scenarios?id=eq.${encodeComponent(id)}") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
        }
    }

    /* ---------- the profile, and the one filter that matters ---------- */

    /** This reader's own profile row.

        ---- `id=eq.<me>` IS THE LOCK HERE, not a second one ----

        Every other read in this file leaves the filter off,
        because every other table is `auth.uid() = user_id` and a
        read with no filter returns your own rows and nothing
        else. `profiles` is the exception: its select policy is
        `using (true)`, deliberately, because a comment has to
        show its author's name to somebody signed out.

        The site learned this the hard way. `getProfile()` asked
        for `profiles?select=...&limit=1`, so PostgREST answered
        with whichever row the planner reached first out of the
        WHOLE table. With one account that was always right. With
        two it was worse than a coin toss, because a non-HOT
        update moves a row to the end of the heap: SAVING your
        profile was what made the next read return somebody
        else's. The account page drew a stranger's name and
        courses, `setup_at` came back null so the setup form
        reappeared, and pressing Save again wrote the right row
        and guaranteed the same wrong read.

        So this names the reader, and `ProfileTest` fails if it
        ever stops. */
    suspend fun profile(): Profile? = withToken { token ->
        val me = account.reader.first()?.id ?: return@withToken null
        val text = http.get(
            "${Supabase.REST}/profiles" +
                "?select=display_name,following,pace,setup_at" +
                "&id=eq.${encodeComponent(me)}&limit=1",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Profile.serializer()), text).firstOrNull()
    }

    /** Saves part of the profile, and carries the filter too.

        `id=eq.<me>` on a PATCH is the second lock on a door the
        update policy already makes impossible to open, and it is
        written out for the same reason the site writes it out:
        the read above is the one with no second lock, and a
        reader comparing the two should see that this one is
        belt-and-braces and that one is the belt.

        A PATCH rather than an upsert, because the row exists: a
        trigger writes it when the account is created, so an
        insert here would be a second row for one person. */
    suspend fun saveProfile(
        displayName: String? = null,
        following: List<String>? = null,
        pace: String? = null,
        setupAt: String? = null,
    ): String? {
        val me = account.reader.first()?.id
            ?: return "You are not signed in, so your settings stayed on this phone."
        val patch = buildJsonObject {
            displayName?.let { put("display_name", it) }
            following?.let { list ->
                put("following", buildJsonArray { for (key in list) add(JsonPrimitive(key)) })
            }
            pace?.let { put("pace", it) }
            setupAt?.let { put("setup_at", it) }
        }
        /* And the answer is READ, through `write`. A `following`
           carrying a school the CHECK constraint has not heard of
           is a 400 on the whole patch, which is how every save on
           the site answered "Could not save that" for two days.
           What `write` adds is the constraint's own name in the
           sentence, which is the difference between a reader
           reporting "it says could not save" and one reporting
           the line that names the bug. */
        return write("your settings") { token ->
            http.patch("${Supabase.REST}/profiles?id=eq.${encodeComponent(me)}") {
                header("apikey", Supabase.KEY)
                header("Authorization", "Bearer $token")
                header("Prefer", "return=minimal")
                contentType(ContentType.Application.Json)
                setBody(patch.toString())
            }
        }
    }

    /* ---------- targets ---------- */

    suspend fun targets(): List<Target> = withToken { token ->
        val text = http.get(
            "${Supabase.REST}/targets?select=id,kind,subject,label,target,reached,unit,done_at" +
                "&order=created_at.desc",
        ) {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()
        json.decodeFromString(ListSerializer(Target.serializer()), text)
    }.orEmpty()

    suspend fun addTarget(target: Target): String? = write("the target") { token ->
        http.post("${Supabase.REST}/targets") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("kind", target.kind)
                            put("subject", target.subject)
                            put("label", target.label)
                            put("target", target.target)
                            /* Only a metric carries its own
                               number. Sending one for a course
                               would be storing a derived value,
                               which is a value that goes stale. */
                            if (target.kind == "metric") put("reached", target.reached)
                            put("unit", target.unit)
                        },
                    )
                }.toString(),
            )
        }
    }

    /** Every row this account holds, gone.

        The rows and not the auth user: deleting the user needs a
        service-role key, which this project deliberately does not
        have and has no reason to start having. What a reader can
        do from here is empty everything of theirs, which is what
        "erase everything" means on the site too, and the screen
        says so rather than implying the login itself disappears.

        Each table separately rather than one call, because they
        are separate tables and PostgREST has no cascade to ask
        for. `user_id` is never named: the row-level policy is
        `auth.uid() = user_id`, so a delete with no filter can
        only ever reach this reader's own rows. */
    suspend fun eraseAll(): String? {
        val token = account.token()
            ?: return "You are not signed in, so there is nothing of yours to erase."
        /* Table by table, and the STATUS is read on each: the old
           version asked only whether the request threw, so a 400
           counted as erased, on the one operation where reporting
           false success means somebody walks away believing their
           rows are gone. */
        val left = mutableListOf<String>()
        for (table in listOf("library", "targets", "scenarios", "progress")) {
            val gone = runCatching {
                http.delete("${Supabase.REST}/$table?user_id=not.is.null") {
                    header("apikey", Supabase.KEY)
                    header("Authorization", "Bearer $token")
                    header("Prefer", "return=minimal")
                }.status.isSuccess()
            }.getOrDefault(false)
            if (!gone) left.add(table)
        }
        return if (left.isEmpty()) {
            null
        } else {
            "Not everything was erased: ${left.joinToString(", ")} did not answer. " +
                "Try again, and if it keeps failing say so rather than assuming it is gone."
        }
    }

    suspend fun removeTarget(id: String): String? = write("that change") { token ->
        http.delete("${Supabase.REST}/targets?id=eq.${encodeComponent(id)}") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=minimal")
        }
    }
}

/* ============================================================
   Taking a copy, and erasing everything.

   Leaving should be as easy as arriving. The site says so and
   means it: one JSON file with the progress, the library, the
   targets and the profile in it, and an erase that takes the
   account AND the mirror.
   ============================================================ */

/** Everything this account holds, as one file.

    Built from what the account answers rather than from what this
    device happens to hold, because a copy of a mirror is not a
    copy of the record. A device that had never exchanged would
    otherwise export an empty file and call it everything. */
suspend fun Library.exportAll(
    reader: uk.co.reiad.library.core.Reader?,
    progress: Map<String, String>,
): String {
    val kept = kept()
    val targets = targets()
    return buildJsonObject {
        put("exported_from", "reiad.co.uk (Android)")
        putJsonObject("reader") {
            put("id", reader?.id.orEmpty())
            put("email", reader?.email.orEmpty())
            put("name", reader?.name.orEmpty())
        }
        putJsonObject("progress") {
            for ((key, value) in progress) {
                /* Written as the JSON it is rather than as a
                   string containing JSON, so the file is readable
                   by anything rather than needing a second parse
                   per key. */
                put(key, runCatching { Json.parseToJsonElement(value) }
                    .getOrElse { JsonPrimitive(value) })
            }
        }
        put(
            "library",
            Json.encodeToJsonElement(ListSerializer(Kept.serializer()), kept),
        )
        put(
            "targets",
            Json.encodeToJsonElement(ListSerializer(Target.serializer()), targets),
        )
    }.toString()
}
