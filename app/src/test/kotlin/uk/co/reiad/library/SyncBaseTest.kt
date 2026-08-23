package uk.co.reiad.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.co.reiad.library.account.Account
import uk.co.reiad.library.account.Sync
import uk.co.reiad.library.data.store
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/* ============================================================
   The exchange does not eat what the reader just did.

   ---- the report, twice ----

   "all setting except light dark isn't working", and weeks
   later, "settings and cards rearranging are NOT working". Both
   were this: `base` lived only in memory, so the first exchange
   of EVERY PROCESS took the adopt path, and adopt writes the
   account's copy of every synced key over the device's. Change
   a setting, arrange the board, and the very write that queued
   the sync summoned the exchange that un-did it, within a
   second, on every cold launch. It looked exactly like a dead
   control, because it was reverted faster than an eye moves.

   Adopting is right on the day an ACCOUNT arrives; a relaunch
   is not that day. So the base is stored, keyed to the account
   id, and these hold the difference:

   a brand-new account still adopts; a relaunch reconciles and
   the fresh local mark WINS and is pushed; somebody else's
   stored base is not inherited; and a failed push keeps the
   base, so what could not go up remains "what this reader did"
   rather than becoming food for the next adopt.

   The conversation is real: a MockEngine answers the pull and
   records the push, and the account is a real `Account` over a
   seeded session whose token is an unsigned JWT the parser
   reads exactly like a live one.
   ============================================================ */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncBaseTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun jwt(sub: String): String {
        fun b64(text: String) = Base64.UrlSafe.encode(text.encodeToByteArray()).trimEnd('=')
        val header = b64("""{"alg":"none"}""")
        val payload = b64("""{"sub":"$sub","exp":4102444800,"email":"r@x"}""")
        return "$header.$payload.sig"
    }

    /** Through the front door: the same redirect a live sign-in
        arrives on, so the session store is written by Account's
        own code and the test never needs to know its keys. The
        token's `exp` is 2100, so `token()` answers without ever
        touching the wire. */
    private fun signIn(sub: String) = runBlocking {
        val arrival = Account(context).arrived(
            "uk.co.reiad.library://auth#access_token=${jwt(sub)}&refresh_token=r",
        )
        assertTrue(
            arrival is uk.co.reiad.library.core.Arrival.SignedIn,
            "the seeded redirect must sign in, or every assertion below tests nobody",
        )
    }

    /** What the account's rows hold, per test. */
    private var remoteRows: String = "[]"
    private val pushes = mutableListOf<String>()
    private var pushAnswer = HttpStatusCode.Created

    private fun sync(): Sync {
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    remoteRows,
                    HttpStatusCode.OK,
                    headersOf("Content-Type", "application/json"),
                )
                else -> {
                    pushes.add(
                        String((request.body as io.ktor.http.content.TextContent).bytes()),
                    )
                    respond("", pushAnswer)
                }
            }
        }
        return Sync(context, Account(context), context.store, HttpClient(engine))
    }

    private fun localPrefs(): String? = runBlocking {
        context.store.data.first()[stringPreferencesKey("reader-prefs")]
    }

    private fun writeLocalPrefs(json: String) = runBlocking {
        context.store.edit { it[stringPreferencesKey("reader-prefs")] = json }
    }

    private fun storedBase(): String? = runBlocking {
        context.store.data.first()[stringPreferencesKey("sync:base")]
    }

    @Test fun aNewAccountAdoptsAndKeepsTheBase() {
        signIn("reader-a")
        remoteRows = """[{"key":"reader-prefs","value":{"theme":"dark","ts":1000}}]"""
        writeLocalPrefs("""{"theme":"light","ts":500}""")

        assertTrue(runBlocking { sync().exchange() })
        assertTrue(localPrefs()!!.contains("dark"), "a new account's copy replaces the device's")
        assertTrue(storedBase()!!.contains("reader-prefs"), "and the conversation is remembered")
    }

    @Test fun aRelaunchDoesNotEatTheFreshMark() {
        signIn("reader-a")
        remoteRows = """[{"key":"reader-prefs","value":{"theme":"dark","ts":1000}}]"""
        assertTrue(runBlocking { sync().exchange() })

        /* THE REPORTED SECOND: the reader changes a setting, and
           the write queues an exchange in a FRESH process (a new
           Sync instance), remote still holding the old copy. */
        writeLocalPrefs("""{"theme":"light","ts":2000}""")
        pushes.clear()
        assertTrue(runBlocking { sync().exchange() })

        assertTrue(
            localPrefs()!!.contains("light"),
            "the mark made a second ago must survive the exchange: " +
                "'settings and cards rearranging are NOT working' was it not surviving",
        )
        assertTrue(pushes.single().contains("light"), "and it goes up to the account")
    }

    @Test fun somebodyElsesBaseIsNotInherited() {
        signIn("reader-a")
        remoteRows = """[{"key":"reader-prefs","value":{"theme":"dark","ts":1000}}]"""
        assertTrue(runBlocking { sync().exchange() })

        /* A different person signs in on this handset. Their
           account holds nothing: the device must ADOPT (which
           empties the mirror), not reconcile against reader-a's
           remembered conversation. */
        signIn("reader-b")
        remoteRows = "[]"
        writeLocalPrefs("""{"theme":"light","ts":9000}""")
        assertTrue(runBlocking { sync().exchange() })
        assertEquals(null, localPrefs(), "reader-b's empty account adopts as empty")
    }

    @Test fun aFailedPushKeepsTheBase() {
        signIn("reader-a")
        remoteRows = """[{"key":"reader-prefs","value":{"theme":"dark","ts":1000}}]"""
        assertTrue(runBlocking { sync().exchange() })
        val goodBase = storedBase()

        writeLocalPrefs("""{"theme":"light","ts":2000}""")
        pushAnswer = HttpStatusCode.BadRequest
        assertEquals(false, runBlocking { sync().exchange() })
        assertEquals(goodBase, storedBase(), "a failed push must not move the base")

        /* The wire recovers; the same mark goes up. */
        pushAnswer = HttpStatusCode.Created
        pushes.clear()
        assertTrue(runBlocking { sync().exchange() })
        assertTrue(pushes.single().contains("light"))
        assertTrue(localPrefs()!!.contains("light"))
    }
}
