package uk.co.reiad.library.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/* ============================================================
   What the site answers with.

   Every model here is `ignoreUnknownKeys`, and that is the whole
   arrangement rather than laziness. `/api/site` spreads the site's
   own tables instead of mapping them field by field, so a school
   gaining a field reaches this app with no release. That only
   works if the app tolerates fields it has never heard of, and
   the day it wants one, it adds a property and nothing else
   changes.

   Every type in here was checked against a captured answer
   rather than reasoned out. `written` is the one that caught it:
   the row is a SQL CASE returning 0 or 1, so this file declared
   an Int, and the API hands back a real `true`. Eight tests went
   red at once on a fixture the site had actually sent, which is
   the entire argument for testing against captured answers
   instead of hand-written ones.
   ============================================================ */

val ReiadJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

const val SITE_ORIGIN: String = "https://reiad.co.uk"

/* ---------- /api/site ---------- */

@Serializable
data class SiteManifest(
    val ok: Boolean = true,
    val site: SiteFacts = SiteFacts(),
    val nav: List<NavGroup> = emptyList(),
    val accents: Map<String, String> = emptyMap(),
    val audiences: List<Audience> = emptyList(),
    val ladders: List<LadderSchool> = emptyList(),
    val sections: List<ReadingSection> = emptyList(),
    val tools: List<Tool> = emptyList(),
    val skills: List<Skill> = emptyList(),
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class SiteFacts(
    val name: String = "",
    val tagline: String = "",
    val origin: String = SITE_ORIGIN,
    val email: String = "",
)

@Serializable
data class NavGroup(
    val id: String = "",
    val label: String = "",
    val accent: String = "",
    val items: List<NavItem> = emptyList(),
)

@Serializable
data class NavItem(
    val label: String = "",
    val sub: String? = null,
    val href: String = "",
    val icon: String = "",
    val key: String? = null,
    val ladder: Boolean = false,
    val soon: Boolean = false,
    val blurb: String? = null,
    val accent: String? = null,
)

@Serializable
data class Audience(val id: String = "", val label: String = "", val sub: String = "")

@Serializable
data class LadderSchool(
    val key: String = "",
    val bn: String = "",
    val en: String = "",
    val href: String = "",
    val accent: String = "",
    val blurb: String = "",
)

@Serializable
data class ReadingSection(
    val id: String = "",
    val en: String = "",
    val bn: String = "",
    val mount: String = "",
    val hub: String = "",
    val lang: String = "",
    val blurb: String = "",
)

@Serializable
data class Tool(val id: String = "", val bn: String = "", val en: String = "", val blurb: String = "")

@Serializable
data class Skill(
    val slug: String = "",
    val bn: String = "",
    val en: String = "",
    val icon: String = "",
    val status: String = "live",
    val blurb: String = "",
    val url: String? = null,
    val course: Boolean = false,
)

/* ---------- /api/schools/<school> ---------- */

@Serializable
data class LadderResponse(
    val ok: Boolean = true,
    val stages: List<Stage> = emptyList(),
    val counts: LadderCounts = LadderCounts(),
)

@Serializable
data class LadderCounts(val total: Int = 0, val written: Int = 0)

@Serializable
data class Stage(
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val de: String? = null,
    val kicker: String? = null,
    val icon: String? = null,
    val who: String? = null,
    val blurb: String? = null,
    val status: String = "live",
    /** Where this stage's lessons live. `basics-1` carries
        `/money/terms/` because those pages were the glossary
        before the school had a builder. Absent means the ordinary
        `/<school>/<stage>/` shape. */
    val base: String? = null,
    val sections: List<Section> = emptyList(),
) {
    /** Every lesson of the stage, in ladder order, whichever noun
        the school files them under. */
    val lessons: List<Lesson> get() = sections.flatMap { it.lessons }
}

/** A school names its lessons differently: money and Qur'anic
    Arabic say `lessons`, German says `teile`, English says
    `parts`. One shape here, three names on the wire. */
@Serializable
data class Section(
    val id: String = "",
    val bn: String = "",
    val en: String? = null,
    @SerialName("lessons") private val lessonsKey: List<Lesson> = emptyList(),
    @SerialName("teile") private val teileKey: List<Lesson> = emptyList(),
    @SerialName("parts") private val partsKey: List<Lesson> = emptyList(),
) {
    val lessons: List<Lesson>
        get() = when {
            lessonsKey.isNotEmpty() -> lessonsKey
            teileKey.isNotEmpty() -> teileKey
            else -> partsKey
        }
}

@Serializable
data class Lesson(
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val de: String? = null,
    val ar: String? = null,
    val blurb: String? = null,
    val icon: String? = null,
    val minutes: Int = 0,
    val risk: String? = null,
    val status: String = "live",
    /** Whether the lesson has prose yet. A promised-but-unwritten
        lesson is listed and answers with an empty body, so the
        ladder can show it without pretending it is readable. */
    val written: Boolean = false,
) {
    val isWritten: Boolean get() = written
    val isSoon: Boolean get() = status == "soon"
}

/* ---------- /api/schools/<school>/<stage>/<lesson> ---------- */

@Serializable
data class LessonResponse(val ok: Boolean = true, val lesson: LessonPage? = null)

@Serializable
data class LessonPage(
    val school: String = "",
    val stage: String = "",
    val slug: String = "",
    val bn: String = "",
    val en: String? = null,
    val blurb: String? = null,
    val minutes: Int = 0,
    val status: String = "live",
    val body: String = "",
)

/* ---------- /api/articles ---------- */

@Serializable
data class ArticlesResponse(val ok: Boolean = true, val articles: List<ArticleCard> = emptyList())

@Serializable
data class ArticleCard(
    val slug: String = "",
    val section: String = "insights",
    val title: String = "",
    val dek: String = "",
    val tag: String = "",
    val lang: String = "en",
    val minutes: Int = 1,
    val cover: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val topics: JsonElement? = null,
)

/* ---------- addresses ---------- */

/** Where a lesson lives on the site. Kept because it is the id in
    somebody's reading list and the target of every term link, and
    because the app's own deep links use the site's addresses
    rather than inventing a second vocabulary.

    The `.html` is part of the SLUG here, not a file extension the
    site forgot to drop. */
fun lessonUrl(school: String, stage: Stage, lessonSlug: String): String {
    val base = stage.base ?: "/$school/${stage.slug}/"
    return "$base$lessonSlug.html"
}

/** A stage's own ladder page, which carries no suffix. */
fun stageUrl(school: String, stageSlug: String): String = "/$school/$stageSlug"

/** A term link inside a lesson body is relative
    (`href="dividend.html"`), so it only means something against
    the stage it was written in. */
fun resolveHref(href: String, school: String, stage: Stage): String = when {
    href.startsWith("http://") || href.startsWith("https://") -> href
    href.startsWith("/") -> href
    href.startsWith("#") -> href
    else -> (stage.base ?: "/$school/${stage.slug}/") + href
}
