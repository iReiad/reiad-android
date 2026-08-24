package uk.co.reiad.library.account

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import uk.co.reiad.library.core.Base
import uk.co.reiad.library.core.MergeRule
import uk.co.reiad.library.core.StoredValue
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.SyncKeys
import uk.co.reiad.library.core.SyncRules

/* ============================================================
   The mirror.

   `core/SyncRules.kt` is the arithmetic and is tested. This is
   the conversation: read the account's rows, reconcile, write
   back what changed, and remember what the account said so the
   next exchange can tell "this device added it" from "the other
   device removed it".

   ---- one sentence governs all of it ----

   **The account is the record and the device is a mirror.**
   Signing in ADOPTS: the account's rows are written on to the
   device and any synced key the account does not hold is removed
   from it. Nothing local goes up.

   That last clause looks like a bug and is the point. A handset
   is not a copy of an account: it may have been lent to somebody
   for five minutes, and nothing here can tell. Merging what was
   already on it would upload a stranger's reading into somebody's
   account, and there is no undo for that.

   ---- base lives on disk, and the memory-only draft was a bug ----

   `base` is the account as this app last saw it. It lived only
   in memory first, on the argument that a fresh launch should
   start with nothing and adopt, and that argument confused two
   different days. On the day an ACCOUNT arrives, adopting is
   right: the handset may be anybody's, and nothing local may go
   up. On every ordinary morning after, "adopt" meant this: the
   reader changed a setting or arranged the board, the write
   queued an exchange, the exchange found no base because the
   process was new, and the account's OLD copy of every mark was
   written over the change they had just made. "settings and
   cards rearranging are NOT working" was this, reported twice,
   because the snap-back arrived within a second of the tap and
   looked exactly like a dead control.

   So the base is stored, keyed to the account's own id: a
   relaunch reconciles, and only a genuinely new account adopts.
   A failed exchange now KEEPS the last good base rather than
   dropping it, for the same reason: the un-pushed local changes
   are still "what this reader did since the account last spoke",
   and turning a flaky network into an adopt was the same eater
   by another door.

   ---- what never goes up ----

   `deutsch-schrift` and `english-write`. They are not in
   `SyncKeys.ALL` and this file only ever walks that map, so the
   omission is structural rather than a rule somebody has to
   remember. `WritingStaysHereTest` asserts it from the other end.
   ============================================================ */

class Sync(
    private val context: Context,
    private val account: Account,
    private val store: androidx.datastore.core.DataStore<Preferences>,
    /** The wire, injectable so `SyncBaseTest` can hold a whole
        conversation without a network. Production never passes
        it. */
    private val http: HttpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** What the account said at the last exchange. The in-memory
        copy is a cache; the stored one is the record, keyed to
        the account id so another person's sign-in can never
        inherit it. */
    private var base: Base? = null

    private val baseKey = stringPreferencesKey("sync:base")
    private val baseUserKey = stringPreferencesKey("sync:base-user")

    private suspend fun loadBase(user: String): Base? {
        base?.let { return it }
        val stored = store.data.first()
        if (stored[baseUserKey] != user) return null
        val raw = stored[baseKey] ?: return null
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull() ?: return null
        val out = mutableMapOf<String, StoredValue>()
        for ((key, element) in obj) {
            val rule = SyncKeys.ruleOf(key) ?: continue
            valueOf(rule, element)?.let { out[key] = it }
        }
        return out.also { base = it }
    }

    private suspend fun keepBase(user: String, value: Base) {
        base = value
        store.edit { stored ->
            stored[baseKey] = buildJsonObject {
                for ((key, held) in value) put(key, wireOf(held))
            }.toString()
            stored[baseUserKey] = user
        }
    }

    private val rest = "${Supabase.REST}/progress"

    /** One exchange. Returns whether it got all the way through.

        A failure KEEPS `base`: the last completed exchange is
        still the truth about what this reader did since, and the
        next attempt reconciles from it. */
    suspend fun exchange(): Boolean {
        val token = account.token() ?: return false
        val who = account.reader.first()?.id ?: return false
        val remote = runCatching { pull(token) }.getOrNull() ?: return false

        val was = loadBase(who)
        val mine = readLocal()

        /* The first exchange of an ACCOUNT adopts: no base under
           this id has ever been stored, so there is no way to
           tell a tick this device added from one the account
           never had, and the account is the record. A relaunch
           is not that day: its base is on disk. */
        if (was == null) {
            val adopted = SyncRules.adopt(remote)
            writeLocal(adopted.write, adopted.forget)
            keepBase(who, remote)
            return true
        }

        val settled = mutableMapOf<String, StoredValue>()
        val send = mutableMapOf<String, StoredValue>()
        for ((key, rule) in SyncKeys.ALL) {
            val value = SyncRules.reconcile(rule, was[key], mine[key], remote[key]) ?: continue
            settled[key] = value
            /* Only what the account does not already hold. An
               exchange that changes nothing writes nothing, which
               is what makes this safe to run often. */
            if (value != remote[key]) send[key] = value
        }

        writeLocal(settled, forget = emptySet())
        if (send.isNotEmpty() && runCatching { push(token, send) }.getOrDefault(false) != true) {
            /* The pull happened and the push did not. The base
               stays where it was: what failed to go up is still
               local-since-base and goes up next time. */
            return false
        }
        keepBase(who, remote + send)
        return true
    }

    /** Signing out takes the mirror off.

        Every synced key is removed, so the next person at this
        handset inherits nothing. What is NOT removed is what the
        account never had a copy of: a practice book's writing
        stays, because it was never the account's to take. */
    suspend fun forget() {
        base = null
        store.edit { prefs ->
            for (key in SyncKeys.ALL.keys) prefs.remove(stringPreferencesKey(key))
            prefs.remove(baseKey)
            prefs.remove(baseUserKey)
        }
    }

    /* ---------- the wire ---------- */

    private suspend fun pull(token: String): Map<String, StoredValue> {
        val text = http.get("$rest?select=key,value") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
        }.bodyAsText()

        val rows = json.parseToJsonElement(text) as? JsonArray ?: return emptyMap()
        val out = mutableMapOf<String, StoredValue>()
        for (row in rows) {
            val obj = row as? JsonObject ?: continue
            val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: continue
            val rule = SyncKeys.ruleOf(key) ?: continue
            valueOf(rule, obj["value"])?.let { out[key] = it }
        }
        return out
    }

    /** True only when the database took the rows.

        THE STATUS IS THE RETURN VALUE, and the caller's guard
        depends on it: `exchange` clears `base` when a push does
        not land, so the un-pushed ticks still read as "what this
        reader did" at the next exchange. With `push` returning
        Unit, a 400 counted as pushed (nothing throws on a status
        without `expectSuccess`), `base` recorded the ticks as the
        account's, and the NEXT exchange reconciled them away:
        local minus base is empty, so the remote copy without the
        ticks won, and the reader's marks came quietly off their
        own device. The routine lost a day to this exact shape one
        file along. */
    private suspend fun push(token: String, rows: Map<String, StoredValue>): Boolean {
        val body = buildJsonArray {
            for ((key, value) in rows) {
                add(
                    buildJsonObject {
                        put("key", key)
                        put("value", wireOf(value))
                    },
                )
            }
        }
        /* `user_id` is filled in by the column default from the
           token, so this device never names whose rows it is
           writing. It cannot get that wrong and it cannot be
           talked into getting it wrong. */
        return http.post("$rest?on_conflict=user_id,key") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }.status.isSuccess()
    }

    /* ---------- what a value looks like on each side ---------- */

    private fun valueOf(rule: MergeRule, element: JsonElement?): StoredValue? = when (rule) {
        MergeRule.SET -> StoredValue.Ids(
            (element as? JsonArray).orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull }
                .toSet(),
        )

        MergeRule.COUNT -> StoredValue.Count(
            (element as? JsonPrimitive)?.longOrNull
                ?: (element as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
                ?: 0L,
        )

        /* A MARK carries its own timestamp inside the value, and
           that is the one key here where the rule is not obvious
           from the shape. `reader-prefs` is an object with a `ts`
           in it, written by the settings screen. */
        MergeRule.MARK -> (element as? JsonObject)?.let {
            StoredValue.Mark(
                ts = it["ts"]?.jsonPrimitive?.longOrNull ?: 0L,
                json = it.toString(),
            )
        }
    }

    private fun wireOf(value: StoredValue): JsonElement = when (value) {
        is StoredValue.Ids -> buildJsonArray { for (id in value.ids.sorted()) add(JsonPrimitive(id)) }
        is StoredValue.Count -> JsonPrimitive(value.n)
        is StoredValue.Mark -> json.parseToJsonElement(value.json)
    }

    /* ---------- what this device holds ---------- */

    private suspend fun readLocal(): Map<String, StoredValue> {
        val prefs = store.data.first()
        val out = mutableMapOf<String, StoredValue>()
        for ((key, rule) in SyncKeys.ALL) {
            val raw = prefs[stringPreferencesKey(key)] ?: continue
            val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: continue
            valueOf(rule, parsed)?.let { out[key] = it }
        }
        return out
    }

    private suspend fun writeLocal(values: Map<String, StoredValue>, forget: Set<String>) {
        store.edit { prefs ->
            for ((key, value) in values) {
                prefs[stringPreferencesKey(key)] = wireOf(value).toString()
            }
            for (key in forget) prefs.remove(stringPreferencesKey(key))
        }
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
