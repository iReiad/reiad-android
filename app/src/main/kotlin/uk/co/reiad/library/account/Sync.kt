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

   ---- base lives in memory, deliberately ----

   `base` is the account as this app last saw it. Keeping it on
   disk would mean a fresh launch has an opinion about a
   conversation it has not had. A launch starts with nothing and
   adopts, which is the safe answer, and every failed exchange
   drops it rather than trusting half a conversation.

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
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    /** What the account said at the last exchange. In memory. */
    private var base: Base? = null

    private val rest = "${Supabase.REST}/progress"

    /** One exchange. Returns whether it got all the way through.

        A failure drops `base`, so the next attempt adopts rather
        than reconciling against a half-finished conversation. */
    suspend fun exchange(): Boolean {
        val token = account.token() ?: return false
        val remote = runCatching { pull(token) }.getOrNull()
        if (remote == null) {
            base = null
            return false
        }

        val was = base
        val mine = readLocal()

        /* The first exchange of a session ADOPTS. There is no
           `was`, so there is no way to tell a tick this device
           added from one the account never had, and the account
           is the record. */
        if (was == null) {
            val adopted = SyncRules.adopt(remote)
            writeLocal(adopted.write, adopted.forget)
            base = remote
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
        if (send.isNotEmpty() && !runCatching { push(token, send) }.isSuccess) {
            base = null
            return false
        }
        base = remote + send
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

    private suspend fun push(token: String, rows: Map<String, StoredValue>) {
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
        http.post("$rest?on_conflict=user_id,key") {
            header("apikey", Supabase.KEY)
            header("Authorization", "Bearer $token")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
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
