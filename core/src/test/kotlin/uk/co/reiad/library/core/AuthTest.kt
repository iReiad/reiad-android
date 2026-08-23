package uk.co.reiad.library.core

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/* ============================================================
   Coming back from a sign-in.

   Every case here is a shape that has actually arrived on this
   redirect somewhere: the fragment, the query string, a provider
   error, a cancelled sign-in, and a token whose claims are the
   only thing that knows the reader's name.
   ============================================================ */
@OptIn(ExperimentalEncodingApi::class)
class AuthTest {

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** A real-shaped JWT, signed with nothing.

        Nothing here verifies a signature and nothing needs to:
        this is the device reading a token it was just handed, and
        everything the token is used FOR goes back to Supabase,
        which does verify it. */
    private fun jwt(payload: String): String =
        "header." + base64.encode(payload.encodeToByteArray()) + ".signature"

    private fun token(
        exp: Long = 2_000_000_000L,
        sub: String = "abc-123",
        email: String = "reader@example.com",
        metadata: String = """{"name":"A Reader","picture":"https://x/y.jpg"}""",
    ) = jwt("""{"exp":$exp,"sub":"$sub","email":"$email","user_metadata":$metadata}""")

    /* ---------- where a sign-in goes ---------- */

    @Test
    fun `the redirect is the app's own scheme, not an app link`() {
        assertEquals("uk.co.reiad.library://auth", Supabase.REDIRECT)
        val url = authorizeUrl("google")
        assertTrue(url.startsWith("https://wvjarqnnmkkuxyrndtya.supabase.co/auth/v1/authorize"))
        assertTrue("provider=google" in url)
        /* Encoded, or Supabase reads the scheme's own colon and
           slashes as part of the query. */
        assertTrue("redirect_to=uk.co.reiad.library%3A%2F%2Fauth" in url, url)
    }

    /** A space is `%20` and not `+`. Java's `URLEncoder` is
        form-encoding and gets this wrong, which is why there is a
        hand-written encoder at all. */
    @Test
    fun `a component is url encoded and not form encoded`() {
        assertEquals("a%20b", encodeComponent("a b"))
        assertEquals("a-b_c.d~e", encodeComponent("a-b_c.d~e"))
        assertEquals("%E0%A6%9F", encodeComponent("ট"))
    }

    /* ---------- what comes back ---------- */

    @Test
    fun `tokens in the fragment are a sign-in`() {
        val arrival = arrivalOf(
            "uk.co.reiad.library://auth#access_token=${token()}&refresh_token=r1&expires_in=3600",
            now = 1_000_000L,
        )
        val signed = assertIs<Arrival.SignedIn>(arrival)
        assertEquals("r1", signed.session.refreshToken)
        assertEquals("abc-123", signed.session.reader?.id)
        assertEquals("A Reader", signed.session.reader?.name)
        assertEquals("https://x/y.jpg", signed.session.reader?.picture)
    }

    /** Some providers answer on the query string instead. Reading
        only the fragment is a sign-in that works for one and
        silently does nothing for another. */
    @Test
    fun `tokens in the query string are a sign-in too`() {
        val arrival = arrivalOf(
            "uk.co.reiad.library://auth?access_token=${token()}&refresh_token=r2",
            now = 1_000_000L,
        )
        assertEquals("r2", assertIs<Arrival.SignedIn>(arrival).session.refreshToken)
    }

    /** The TOKEN's own expiry wins over the response's, because
        the token is the thing that actually stops working. */
    @Test
    fun `expiry comes from the token rather than the response`() {
        val arrival = arrivalOf(
            "x://auth#access_token=${token(exp = 1_700_000_000L)}" +
                "&refresh_token=r&expires_in=999999",
            now = 0L,
        )
        assertEquals(1_700_000_000_000L, assertIs<Arrival.SignedIn>(arrival).session.expiresAt)
    }

    /** And a token nothing can read still signs the reader in,
        with the response's expiry, because a session that works
        is better than a refusal over a claim nobody needed. */
    @Test
    fun `an unreadable token falls back to the response's expiry`() {
        val arrival = arrivalOf(
            "x://auth#access_token=not-a-jwt&refresh_token=r&expires_in=60",
            now = 1_000L,
        )
        val signed = assertIs<Arrival.SignedIn>(arrival)
        assertEquals(61_000L, signed.session.expiresAt)
        assertEquals(null, signed.session.reader)
    }

    /* ---------- and what goes wrong ---------- */

    @Test
    fun `a refused sign-in says why`() {
        val arrival = arrivalOf(
            "x://auth#error=access_denied&error_description=The+user+said+no",
            now = 0L,
        )
        assertEquals("The user said no", assertIs<Arrival.Failed>(arrival).reason)
    }

    @Test
    fun `an error with no description still says something`() {
        assertIs<Arrival.Failed>(arrivalOf("x://auth#error_code=otp_expired", now = 0L))
    }

    /** Half a redirect is not a sign-in. A URI with an access
        token and no refresh token would give a session that works
        for an hour and then cannot be renewed, which is worse
        than not signing in. */
    @Test
    fun `half a redirect is not a sign-in`() {
        assertIs<Arrival.NotAnArrival>(arrivalOf("x://auth#access_token=abc", now = 0L))
        assertIs<Arrival.NotAnArrival>(arrivalOf("x://auth#refresh_token=abc", now = 0L))
        assertIs<Arrival.NotAnArrival>(arrivalOf("uk.co.reiad.library://auth", now = 0L))
        assertIs<Arrival.NotAnArrival>(arrivalOf("https://reiad.co.uk/money", now = 0L))
    }

    /* ---------- refreshing ---------- */

    /** A minute early, because a token that expires mid-request
        is a request that fails for no reason the reader could
        understand. */
    @Test
    fun `a session is refreshed before it actually expires`() {
        val session = Session("a", "r", expiresAt = 100_000L, reader = null)
        assertTrue(!needsRefresh(session, now = 30_000L))
        assertTrue(needsRefresh(session, now = 40_001L), "should refresh a minute early")
        assertTrue(needsRefresh(session, now = 100_000L))
    }

    /* ---------- the names providers disagree about ---------- */

    @Test
    fun `a name is found whatever the provider called it`() {
        for (field in listOf("name", "full_name", "user_name")) {
            val claims = readToken(
                jwt("""{"exp":1,"sub":"s","user_metadata":{"$field":"Someone"}}"""),
            )
            assertEquals("Someone", claims?.reader?.name, "missed $field")
        }
        for (field in listOf("picture", "avatar_url")) {
            val claims = readToken(
                jwt("""{"exp":1,"sub":"s","user_metadata":{"$field":"u"}}"""),
            )
            assertEquals("u", claims?.reader?.picture, "missed $field")
        }
    }

    @Test
    fun `a token with no metadata is still a reader`() {
        val claims = readToken(jwt("""{"exp":1,"sub":"s","email":"e@x"}"""))
        assertEquals("s", claims?.reader?.id)
        assertEquals("e@x", claims?.reader?.email)
        assertEquals("", claims?.reader?.name)
    }

    @Test
    fun `nonsense is null rather than a crash`() {
        assertEquals(null, readToken(""))
        assertEquals(null, readToken("a.b"))
        assertEquals(null, readToken("a.!!!.c"))
        assertEquals(null, readToken(jwt("not json")))
        assertEquals(null, readToken(jwt("""{"sub":"s"}""")), "no exp is no claims")
    }
}

/* ============================================================
   The three kinds of target, and the test a fourth has to pass.
   ============================================================ */
class TargetTest {

    @Test
    fun `a course target counts the reader's own ticks`() {
        val target = Target(kind = "course", subject = "money", target = 60.0)
        assertEquals(12.0, reachedFor(target, ticks = { if (it == "money") 12 else 0 }, daysActive = 5))
    }

    @Test
    fun `a habit target counts the days turned up`() {
        val target = Target(kind = "habit", subject = "day", target = 30.0)
        assertEquals(5.0, reachedFor(target, ticks = { 99 }, daysActive = 5))
    }

    /** The one kind that uses its stored number, because it is
        the one the site cannot see. */
    @Test
    fun `a metric target is the number the reader typed`() {
        val target = Target(kind = "metric", label = "Weight", reached = 74.5, target = 70.0)
        assertEquals(74.5, reachedFor(target, ticks = { 99 }, daysActive = 99))
    }

    /** A derived number is never stored, so a course target whose
        `reached` column says something else is still counted from
        the ticks. A stored copy of a derived number is a copy
        that goes stale. */
    @Test
    fun `a stored number does not override a derived one`() {
        val stale = Target(kind = "course", subject = "money", reached = 999.0)
        assertEquals(3.0, reachedFor(stale, ticks = { 3 }, daysActive = 0))
    }

    /** Finished is the reader's word, not the bar's. */
    @Test
    fun `done is what the reader said and not where the bar is`() {
        assertTrue(!isDone(Target(kind = "metric", reached = 100.0, target = 10.0)))
        assertTrue(isDone(Target(doneAt = "2026-08-22T00:00:00Z")))
        assertTrue(!isDone(Target(doneAt = "")))
    }
}
