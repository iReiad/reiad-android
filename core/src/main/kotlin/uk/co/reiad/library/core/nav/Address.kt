package uk.co.reiad.library.core.nav

import uk.co.reiad.library.core.CourseWhere
import uk.co.reiad.library.core.SiteManifest
import uk.co.reiad.library.core.courseWhere

/* ============================================================
   One address vocabulary, across the site and the app.

   The app registers `https://reiad.co.uk` as an app link, which
   means a shared lesson, a shared article and a shared calculator
   all OPEN THE APP. Until this file existed they all opened it on
   the front page, which is worse than not registering at all: a
   reader who taps a link to a specific lesson and lands on a
   home screen has been sent somewhere they did not ask to go,
   with the page they wanted now one back-press and a browser
   away.

   ---- read from the manifest, not from a list here ----

   Which prefixes are schools and which are reading sections
   arrives in `/api/site`, so a fifth school is routed with no app
   release. The only shapes written out below are the ones that
   are shapes rather than names: an article's `.html` suffix and a
   lesson's three segments.

   ---- and `.html` is part of a slug here ----

   CLAUDE.md's "An address has no .html" has two exceptions and
   both are addresses this has to parse: an article is
   `/insights/<slug>.html` and a school lesson is
   `/<school>/<stage>/<lesson>.html`, where the suffix is part of
   the slug rather than part of a route. It is in the rows, in
   every link inside every lesson body, and in the `library` row
   of everybody who has saved a piece, so it is kept rather than
   stripped: a slug with it removed matches nothing.
   ============================================================ */

/** Somewhere a link can land. */
sealed interface Destination {
    data object Home : Destination

    /** A school's ladder. */
    data class School(val key: String) : Destination

    /** One lesson of one stage of one school. */
    data class Lesson(val school: String, val stage: String, val slug: String) : Destination

    /** A reading hub: insights, cooking, travel. */
    data class Hub(val section: String) : Destination

    /** One written piece. */
    data class Piece(val section: String, val slug: String) : Destination

    /** A tool screen, by the key the nav table gives it. */
    data class Tool(val key: String) : Destination

    /**
     * Somewhere in the admin's own course section.
     *
     * The one destination whose reader may not be allowed to see
     * it, and that is deliberate: the app cannot tell from an
     * address whether the person holding it is the admin, and it
     * must not try. The screen asks the endpoint, which is the
     * only thing that knows, and says either "signed out" or
     * "not yours", which are the two sentences the site gives.
     *
     * It used to fall through to `Elsewhere`, which opened a
     * browser, which is precisely the hand-off that could never
     * work: the site's session lives in the browser's storage and
     * the app's lives in the app.
     */
    data class Courses(val where: CourseWhere) : Destination

    data object Account : Destination

    /**
     * Anything this app does not draw, which opens on the site.
     *
     * NOT a silent fall back to Home, which is what the app did
     * with every deep link for eleven blocks. A reader who
     * followed a link to a case study should get the case study
     * in a browser, not the app's front page.
     */
    data class Elsewhere(val url: String) : Destination
}

/** The two hubs that are a list of things the manifest already
    carries: what this site teaches, and the work it shows. */
const val SKILLS_KEY = "skills"
const val PORTFOLIO_KEY = "portfolio"

/** The tool keys, which are the nav table's own. */
const val STOCK_KEY = "stock"
const val TOOLS_KEY = "tools"
const val LIVE_KEY = "live"
const val ROUTINE_KEY = "routine"
const val DIET_KEY = "diet"

/**
 * Where a site address goes.
 *
 * `site` may be null, which is a real state rather than a guard:
 * a link tapped before the first manifest fetch has landed. The
 * shapes that do not need the manifest still resolve, and the
 * rest open on the site, which is right rather than merely safe.
 */
fun destinationOf(url: String, site: SiteManifest?): Destination {
    val path = pathOf(url) ?: return Destination.Elsewhere(url)
    if (path.isEmpty() || path == "/") return Destination.Home

    val parts = path.trim('/').split("/").filter { it.isNotEmpty() }
    if (parts.isEmpty()) return Destination.Home

    /* Tools first, because `/tools` is a hub of its own and
       `/tools/stock` is a screen: a school named `tools` would
       be ambiguous and there is not one. */
    if (parts[0] == "tools") {
        return when (parts.getOrNull(1)) {
            null -> Destination.Tool(TOOLS_KEY)
            "stock" -> Destination.Tool(STOCK_KEY)
            "live" -> Destination.Tool(LIVE_KEY)
            "routine" -> Destination.Tool(ROUTINE_KEY)
            /* `/tools/diet` is today's log, which the app draws.
               Its other thirteen pages are the site's, so
               `/tools/diet/trend` opens there: a deep link into a
               page this app does not have must not land on the
               nearest one it does. */
            "diet" -> if (parts.size == 2) {
                Destination.Tool(DIET_KEY)
            } else {
                Destination.Elsewhere(url)
            }
            else -> Destination.Elsewhere(url)
        }
    }
    if (parts[0] == "account") return Destination.Account

    /* The admin's course section, which the app draws itself.

       Before `/skills`, because `skills` is a hub key and
       `/skills/courses` is five views underneath it that the hub
       knows nothing about. Read by `courseWhere`, which is the
       same function the section's own navigation uses, so a
       shared link and a tap on a card land on one screen. */
    courseWhere(path)?.let { return Destination.Courses(it) }

    /* A school, by the keys the manifest names. */
    val school = site?.ladders?.firstOrNull { it.key == parts[0] }
    if (school != null) {
        /* `/money` is the ladder. `/money/basics-1/bo-account.html`
           is a lesson. `/money/terms/<slug>.html` is a lesson too:
           a stage's `base` decides where its pages go and
           `basics-1` writes its eighteen term pages under
           `/money/terms/`, which is a fact about an address rather
           than about a route. Both are three segments and both are
           handled by the same branch, because the middle one is
           the stage as far as this is concerned and the endpoint
           resolves it. */
        return when (parts.size) {
            1 -> Destination.School(school.key)
            3 -> Destination.Lesson(school.key, parts[1], parts[2])
            else -> Destination.Elsewhere(url)
        }
    }

    /* A reading section, by its own `id`. */
    val section = site?.sections?.firstOrNull { it.id == parts[0] }
    if (section != null) {
        return when (parts.size) {
            1 -> Destination.Hub(section.id)
            2 -> Destination.Piece(section.id, parts[1])
            else -> Destination.Elsewhere(url)
        }
    }

    return Destination.Elsewhere(url)
}

/**
 * The path of an address on THIS site, or null for anywhere else.
 *
 * The host check is the whole of the security here and it is not
 * decoration: this decides what the app renders as its own, and
 * an address on another host that reached the lesson branch would
 * be this app fetching somebody else's page and drawing it as a
 * lesson of the site.
 */
private fun pathOf(url: String): String? {
    val trimmed = url.trim()
    /* A bare path, which is what an internal link is. */
    if (trimmed.startsWith("/")) return trimmed.substringBefore('#').substringBefore('?')

    val scheme = trimmed.substringBefore("://", "")
    if (scheme != "https" && scheme != "http") return null
    val rest = trimmed.substringAfter("://")
    val host = rest.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
    if (host != "reiad.co.uk" && host != "www.reiad.co.uk") return null
    val path = rest.substringAfter('/', "")
    return "/" + path.substringBefore('#').substringBefore('?')
}
