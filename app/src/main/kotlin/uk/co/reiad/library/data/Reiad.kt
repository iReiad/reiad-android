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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import uk.co.reiad.library.core.LadderResponse
import uk.co.reiad.library.core.LessonResponse
import uk.co.reiad.library.core.AUDIENCE_KEY
import uk.co.reiad.library.core.PREFS_KEY
import uk.co.reiad.library.core.Prefs
import uk.co.reiad.library.core.Bookmark
import uk.co.reiad.library.core.BookKeyResponse
import uk.co.reiad.library.core.BookResponse
import uk.co.reiad.library.core.PieceResponse
import uk.co.reiad.library.core.PiecesResponse
import uk.co.reiad.library.core.stock.ToolWords
import uk.co.reiad.library.core.ProgressKeys
import uk.co.reiad.library.core.THEME_KEY
import uk.co.reiad.library.core.TOOL_LANG_KEY
import uk.co.reiad.library.core.TRACK_KEY
import uk.co.reiad.library.core.Theme
import uk.co.reiad.library.core.School
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.SyncKeys

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

    /** The one store, shared with `Sync`.

        Exposed rather than reached for a second time, because
        `preferencesDataStore(name = "reiad")` declared twice
        gives two objects over one file and DataStore throws on
        the second read. A tick written through one and looked for
        through the other would be a tick that vanished. */
    val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
        get() = context.store

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

    /** Every word the calculators say, in both languages.

        Fetched rather than bundled, and that is the contract at
        the top of CLAUDE.md rather than a preference: the stock
        check's MODEL is here in Kotlin and needed this release,
        its 366 phrases are data and must not. An edited Bangla
        sentence reaches a phone on the next fetch.

        Cached like everything else, so the tool works on a train,
        and the screen renders nothing until this has answered:
        the alternative is a page of key names that turns into
        words a second later. */
    suspend fun toolWords(): Cached<ToolWords> =
        fetch("$SITE_ORIGIN/api/tools", "cache:tools", ToolWords.serializer())

    suspend fun ladder(school: String): Cached<LadderResponse> =
        fetch("$SITE_ORIGIN/api/schools/$school", "cache:ladder:$school", LadderResponse.serializer())

    /** Every live piece, without bodies. The endpoint splits it
        that way and the split is right for a handset too: a hub
        of six pieces should not pull six bodies. */
    suspend fun pieces(): Cached<PiecesResponse> =
        fetch("$SITE_ORIGIN/api/articles", "cache:pieces", PiecesResponse.serializer())

    /** One piece, body included. Cached under its own slug, so a
        piece read once is readable on a train. */
    suspend fun piece(slug: String): Cached<PieceResponse> =
        fetch("$SITE_ORIGIN/api/articles/$slug", "cache:piece:$slug", PieceResponse.serializer())

    /** A practice book, with every answer already taken out by
        the endpoint. */
    suspend fun book(stage: String): Cached<BookResponse> =
        fetch("$SITE_ORIGIN/api/book/$stage", "cache:book:$stage", BookResponse.serializer())

    /** And one day's answers, asked for only when the reader
        presses Show.

        NOT cached to disk, and that is the difference between
        this and everything else here. A book cached is a book
        readable on a train, which is the point; a key cached is
        a key sitting on the device beside the prompts, which
        undoes the reason the endpoint splits them at all. */
    suspend fun bookKey(stage: String, day: Int): List<String> = runCatching {
        json.decodeFromString(
            BookKeyResponse.serializer(),
            http.get("$SITE_ORIGIN/api/book/$stage/key/$day").bodyAsText(),
        ).answers
    }.getOrDefault(emptyList())

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

    /** Which practice-book days have been ticked. */
    fun days(school: School): Flow<Set<String>> =
        context.store.data.map { prefs ->
            val name = ProgressKeys.days(school) ?: return@map emptySet()
            decode(prefs[key(name)])
        }

    suspend fun toggleDay(school: School, id: String): Set<String> {
        val name = ProgressKeys.days(school) ?: return emptySet()
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val now = decode(prefs[key(name)])
            after = if (id in now) now - id else now + id
            prefs[key(name)] = encode(after)

            /* The furthest day reached, which is a COUNT rather
               than a set and is filed under its own key. It only
               ever goes up: un-ticking day twelve does not mean a
               reader has not seen it. */
            ProgressKeys.dayCount(school)?.let { counter ->
                val reached = after.mapNotNull { it.substringAfterLast('-').toIntOrNull() }
                    .maxOrNull() ?: 0
                val had = prefs[key(counter)]?.toIntOrNull() ?: 0
                if (reached > had) prefs[key(counter)] = reached.toString()
            }
        }
        return after
    }

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

    /* ---------- what the reader has chosen ----------

       Stored under the site's own key, `reader-prefs`, holding
       the site's own JSON. The rule above about a tick's key
       covers this exactly: renaming it does not move somebody's
       setting, it loses it, and a reader who set the type larger
       on their laptop should find it larger here.

       The whole record is round-tripped, including the three
       fields this app does not use yet, because a device that
       drops what it does not understand resets a setting made
       somewhere else, silently, on first launch. */

    val prefs: Flow<Prefs> = context.store.data.map { stored ->
        val raw = stored[key(PREFS_KEY)]
        val record = if (raw.isNullOrBlank()) Prefs()
        else runCatching { json.decodeFromString(Prefs.serializer(), raw) }.getOrDefault(Prefs())
        /* The theme comes from its OWN key, because that is where
           the site keeps it: the record deliberately has no theme
           field, so a device reading one out of it would read
           nothing. Absent means "follow the system", which is
           what removing the key means on the site too. */
        record.copy(theme = stored[key(THEME_KEY)] ?: Theme.SYSTEM.id)
    }

    suspend fun savePrefs(change: (Prefs) -> Prefs) {
        context.store.edit { stored ->
            val raw = stored[key(PREFS_KEY)]
            val now = (if (raw.isNullOrBlank()) Prefs()
            else runCatching { json.decodeFromString(Prefs.serializer(), raw) }.getOrDefault(Prefs()))
                .copy(theme = stored[key(THEME_KEY)] ?: Theme.SYSTEM.id)
            /* Stamped on every save. `reader-prefs` reconciles
               on the `ts` inside its value, so a record written
               without a fresh one loses the exchange. */
            val next = change(now).copy(ts = System.currentTimeMillis())
            stored[key(PREFS_KEY)] = json.encodeToString(Prefs.serializer(), next)

            /* The site writes `theme` and `tool-lang` BESIDE the
               record, and they are not duplicates to be tidied:
               they are the names other code reads. Its boot
               script answers "which theme" before it can afford
               to parse JSON, and the calculators have read
               `tool-lang` since long before there were accounts.
               A device that wrote only the record would sync a
               theme the browser then ignored. */
            if (next.themeChoice == Theme.SYSTEM) stored.remove(key(THEME_KEY))
            else stored[key(THEME_KEY)] = next.themeChoice.id
            stored[key(TOOL_LANG_KEY)] = next.lang
        }
    }

    /** Which groups lead. Null is a real answer and means the
        reader has never said, so they get the site's own order
        rather than one chosen for them. */
    val audience: Flow<String?> = context.store.data.map { it[key(AUDIENCE_KEY)] }

    suspend fun setAudience(id: String) {
        context.store.edit { stored ->
            stored[key(AUDIENCE_KEY)] = id
            /* The site clears `track` when somebody says they are
               here for work, because a track is a learner's
               answer to "which school" and means nothing to
               somebody hiring. Same behaviour, same two keys. */
            if (id == "work") stored.remove(key(TRACK_KEY))
        }
    }

    /** Which days this reader turned up.

        A SET, for the obvious reason the site gives: a phone on
        the bus and a laptop at a desk are the same Tuesday, and
        either one alone under-counts. */
    suspend fun daysActive(): Set<String> =
        decode(context.store.data.first()[key(ProgressKeys.DAYS_ACTIVE)])

    /** Every synced value this device holds, raw, for the export.

        Raw rather than parsed because the export writes them out
        as they are: a copy of somebody's record should be what
        the record says, not this app's reading of it. */
    suspend fun everything(): Map<String, String> {
        val prefs = context.store.data.first()
        return SyncKeys.ALL.keys.mapNotNull { name ->
            prefs[key(name)]?.let { name to it }
        }.toMap()
    }

    /* ---------- the bookmark ----------

       Where a reader last WAS, under `<school>-last`. Not where
       they got to: opening is not finishing, so a visit moves
       this and ticks nothing.

       It stores a lesson ID rather than a URL, and that is the
       site's own correction: the money school's old module stored
       a URL, so a lesson that moved took the bookmark with it and
       the resume card pointed at a page that was not there. */

    fun bookmark(school: School): Flow<Bookmark?> =
        context.store.data.map { stored ->
            val raw = stored[key(ProgressKeys.last(school))] ?: return@map null
            runCatching { json.decodeFromString(Bookmark.serializer(), raw) }.getOrNull()
        }

    suspend fun remember(school: School, mark: Bookmark) {
        context.store.edit {
            /* Stamped here rather than by the caller, so a
               bookmark cannot be written without one: the key is
               a MARK and a value with no `ts` loses every
               exchange it is in. */
            it[key(ProgressKeys.last(school))] =
                json.encodeToString(
                    Bookmark.serializer(),
                    mark.copy(ts = System.currentTimeMillis()),
                )
        }
    }

    /* ---------- checkpoints, which are the ticks inside a lesson ----------

       A lesson's own tick is about the whole page and is the
       right unit for a ladder. A checklist inside the prose is
       five things a reader does over a fortnight, and without
       this the page cannot remember which three are done.

       Filed `<lesson id>#<n>` under `<school>-checks`, which is
       the shape and the key the browser already uses, and
       carried to the account like any other tick.

       **Counted towards no ladder, anywhere.** A checkpoint is
       not a lesson, and the one way to get this wrong is to let
       it into the arithmetic that draws a school's ring. */

    fun checkpoints(school: School): Flow<Set<String>> =
        context.store.data.map { decode(it[key(ProgressKeys.checks(school))]) }

    suspend fun toggleCheckpoint(school: School, id: String): Set<String> {
        var after: Set<String> = emptySet()
        context.store.edit { prefs ->
            val name = key(ProgressKeys.checks(school))
            val now = decode(prefs[name])
            after = if (id in now) now - id else now + id
            prefs[name] = encode(after)
        }
        return after
    }

    /* ---------- and what a learner typed, which stays here ----------

       `deutsch-schrift` and `english-write`. The only progress
       keys with no path to an account, and see `ProgressKeys` for
       why: a tick is one bit, and this is somebody writing about
       their own life in a language they are learning badly. */

    fun writing(school: School): Flow<Map<String, String>> =
        context.store.data.map { prefs ->
            val name = ProgressKeys.write(school) ?: return@map emptyMap()
            val raw = prefs[key(name)]
            if (raw.isNullOrBlank()) emptyMap()
            else runCatching { json.decodeFromString(writings, raw) }.getOrDefault(emptyMap())
        }

    suspend fun write(school: School, slot: String, text: String) {
        val name = ProgressKeys.write(school) ?: return
        context.store.edit { prefs ->
            val raw = prefs[key(name)]
            val now = if (raw.isNullOrBlank()) emptyMap()
            else runCatching { json.decodeFromString(writings, raw) }.getOrDefault(emptyMap())
            /* An emptied box REMOVES its slot rather than storing
               an empty string, so the record is what was written
               rather than every box ever touched. */
            val next = if (text.isBlank()) now - slot else now + (slot to text)
            prefs[key(name)] = json.encodeToString(writings, next)
        }
    }

    private val writings = MapSerializer(String.serializer(), String.serializer())

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
) {
    /** What went wrong, in a sentence a reader can act on.

        Null when nothing went wrong OR when a saved copy
        answered, because a reader looking at their own ladder
        does not need to be told the network was busy. It is a
        problem only when there is nothing to show. */
    val problem: String?
        get() {
            if (value != null) return null
            val why = failed ?: return null
            return when (why) {
                is java.net.UnknownHostException ->
                    "No connection, and this phone has not saved a copy yet."
                is java.net.SocketTimeoutException,
                is io.ktor.client.plugins.HttpRequestTimeoutException ->
                    "The site took too long to answer."
                is io.ktor.client.plugins.ClientRequestException ->
                    "The site answered ${why.response.status.value}. " +
                        "That address may not be live yet."
                is io.ktor.client.plugins.ServerResponseException ->
                    "The site answered ${why.response.status.value}."
                else -> why.message ?: why::class.simpleName ?: "Something went wrong."
            }
        }
}
