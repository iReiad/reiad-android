package uk.co.reiad.library

import kotlin.test.Test
import kotlin.test.assertTrue
import uk.co.reiad.library.core.Supabase
import uk.co.reiad.library.core.encodeComponent
import java.io.File

/* ============================================================
   Both ways in, and the two things that made neither work.

   Sign-in failed on a real phone with every check passing, and
   the two reasons are the two this file pins. Both are the same
   shape: a thing that LOOKS right, returns success, and does the
   wrong thing silently.

   ---- one: the redirect was in the body ----

   `POST /auth/v1/otp` takes `redirect_to` as a QUERY parameter.
   The app sent `options.email_redirect_to` in the body, which is
   the JS client library's shape rather than this API's. GoTrue
   ignores a field it does not know, so the request returned 200,
   the screen said the link had been sent, the email arrived, and
   the link opened the website instead of the app.

   `aab/src/account.ts` on the site does it the right way and has
   since it was written. The site's magic link worked and the
   app's did not, over one difference.

   ---- two: `onNewIntent` was never called ----

   `MainActivity` reads the redirect there, and its own comment
   says "onNewIntent is how a redirect arrives when the app is
   ALREADY open, which is the usual case". With the default
   `standard` launch mode that is never true: Android builds a
   second MainActivity on top of the running one instead.

   Both are asserted against the SOURCE, because neither can be
   reached from a unit test: one is a live HTTP call and the
   other is the platform's own launch behaviour. What a test can
   do is fail when the line that decides it changes back.
   ============================================================ */
class SignInTest {

    /**
     * A file's CODE, with its comments taken out.
     *
     * Every assertion below is "this string is present" or "this
     * string is absent", and the absent ones are exactly the
     * strings the fix's own comment has to name in order to
     * explain itself. Grepping the whole file makes writing down
     * why something was wrong the thing that fails the test.
     */
    private fun source(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "$path is missing; this test names a real file")
        return file.readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""(?<!:)//[^\n]*"""), " ")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")
    }

    /**
     * The magic link's redirect goes in the query string.
     *
     * Asserted as "the URL is built with it" rather than by
     * driving a request, because the failure was never in the
     * response: the request succeeded either way.
     */
    @Test fun theMagicLinkCarriesItsRedirectInTheQuery() {
        val account = source("src/main/kotlin/uk/co/reiad/library/account/Account.kt")

        assertTrue(
            "/otp?redirect_to=" in account,
            "the OTP request must carry redirect_to in the query string: " +
                "GoTrue reads it there and ignores an unknown body field, " +
                "so the link goes to SITE_URL and the app never sees it",
        )
        assertTrue(
            "email_redirect_to" !in account,
            "`options.email_redirect_to` is the JS client library's shape, " +
                "not this API's. It returns 200 and does nothing.",
        )
        assertTrue(
            "answer.status.isSuccess()" in account,
            "the answer has to be READ: runCatching around a call that does not " +
                "throw on a 400 turns a rate limit, a bad address and email " +
                "sign-in being switched off into \"we have sent you a link\"",
        )
    }

    /** And Google's, which was always right and is pinned so the
        two stay the same shape. */
    @Test fun googleCarriesItsRedirectToo() {
        val url = uk.co.reiad.library.core.authorizeUrl("google")
        assertTrue("redirect_to=${encodeComponent(Supabase.REDIRECT)}" in url, url)
    }

    /**
     * The activity is `singleTop`, so the redirect reaches the
     * app that is already running.
     *
     * Not `singleTask`: that would also clear the back stack on
     * every deep link, which is a different behaviour bought to
     * fix this one.
     */
    @Test fun theActivityReceivesTheRedirectRatherThanBeingDuplicated() {
        val manifest = source("src/main/AndroidManifest.xml")
        assertTrue(
            "android:launchMode=\"singleTop\"" in manifest,
            "MainActivity reads the sign-in redirect in onNewIntent, which the " +
                "default `standard` launch mode never calls: Android builds a " +
                "second activity on top of the running one instead",
        )
    }

    /** The scheme the manifest listens on and the one the request
        asks for are the same string. Two places, one value, and a
        typo in either is a sign-in that goes nowhere. */
    @Test fun theSchemeMatchesTheManifest() {
        val manifest = source("src/main/AndroidManifest.xml")
        val scheme = Supabase.REDIRECT.substringBefore("://")
        val host = Supabase.REDIRECT.substringAfter("://").substringBefore("/")
        assertTrue("android:scheme=\"$scheme\"" in manifest, "no filter for $scheme")
        assertTrue("android:host=\"$host\"" in manifest, "no filter for host $host")
    }
}
