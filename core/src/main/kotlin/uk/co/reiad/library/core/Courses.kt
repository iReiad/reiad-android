package uk.co.reiad.library.core

import kotlinx.serialization.Serializable

/* ============================================================
   The third-party course catalogue, as the API hands it over.

   ---- what this is, and what it deliberately is not ----

   The six schools are the site's own writing: prose in a
   database, a ladder off `/api/schools/<school>`, a lesson
   rendered from the row. This is the other thing. A third-party
   course is somebody else's material sitting in one person's
   private Drive folder, and what travels is a CATALOGUE of it:
   which courses, which modules, which lessons, and the Drive id
   of the file behind each one.

   **Nothing course-shaped is in this file.** No slug, no title,
   no Drive id, and none in the binary either. The catalogue
   arrives one way: a signed-in admin asks `/api/courses` with
   their own token and the Worker answers. That is the same rule
   `scripts/check-courses.ts` enforces on the website's own
   bundle, and it matters more here, because this repository is
   public.

   ---- why the app draws it at all ----

   Because a browser hand-off cannot work for this section, and
   the reason is structural rather than a bug somebody can fix.
   The site's reader session is a bearer token in the BROWSER's
   own storage; this app's session is its own. A Custom Tab
   therefore opens a page that checks a session the app has no
   way to put there, and the reader gets the shell's "you are
   either signed out or this is not your library" while the same
   address, in the browser they actually signed in on, works.
   That was reported as "the course is not opening on the app,
   but it definitely opens on the website".

   There is no version of handing this to a browser that fixes
   it. So the app asks the endpoint itself, with the token it
   already holds. It is the same token whose answer to
   `/api/courses` decides whether the card is drawn at all.

   ---- and the two rules that come with it ----

   THE TICK HAS NO PROGRAMME IN IT. See `courseLessonId`.

   NO PLAYER EVENT EVER MARKS A LESSON. A lesson is finished when
   the reader says it is, which is the same rule the six schools
   already live by: opening is not finishing.
   ============================================================ */

/* ---------- what the wire carries ----------

   The shape of `forBrowser()` and `listForBrowser()` in the
   website's `shared/courses.ts`, written out because there is no
   package to import. Every field is defaulted and unknown keys
   are ignored by the client's parser, so a field added on the
   site is a field this app has not read yet rather than a screen
   that fails to load. `CoursesSurfaceTest` is what makes that
   visible rather than silent. */

/** A file hanging off a lesson: a template, a dataset, a slide
    deck. Not the lesson itself. */
@Serializable
data class CourseFile(
    val name: String = "",
    /** The extension, lower case, which is all the chip says. */
    val ext: String = "",
    val drive: String = "",
)

@Serializable
data class CourseLesson(
    val slug: String = "",
    val title: String = "",
    /** `video`, `reading`, `quiz`, `exam` or `file`. A string
        rather than an enum: this is somebody else's export and a
        sixth kind arriving should draw a lesson with an unfamiliar
        word on it, not fail to parse the course. */
    val kind: String = "",
    /** Which group of the module this came from. A heading, never
        part of an address: a lesson that moves between groups
        keeps its id and its tick. */
    val section: String = "",
    /** Where it sits in the module, counted across all groups. */
    val position: Int = 0,
    val video: String? = null,
    val reading: String? = null,
    val quiz: String? = null,
    val exam: String? = null,
    /** The `.en.txt`: prose, offered as a file, for reading
        rather than watching. */
    val transcript: String? = null,
    /** The `.en.srt`, served as WebVTT because nothing reads
        SubRip. Two files and two jobs, not one thing twice. */
    val captions: String? = null,
    val files: List<CourseFile> = emptyList(),
) {
    /** Whether this lesson has anything behind it at all. A
        catalogue row with no file against it is a real state and
        the screen says so rather than drawing an empty page. */
    val empty: Boolean
        get() = video == null && reading == null && quiz == null && exam == null
}

@Serializable
data class CourseModule(
    val slug: String = "",
    val n: Int = 0,
    val title: String = "",
    /** No lessons yet: either the Drive folder is empty or the
        import has not reached it. Said out loud rather than
        hidden, because a module missing from a ladder looks like
        a course with fewer weeks in it. */
    val pending: Boolean = false,
    val lessons: List<CourseLesson> = emptyList(),
)

/** One course, with its modules and their lessons. What
    `/api/courses/<programme>/<course>` answers with, and the only
    shape that carries Drive ids. */
@Serializable
data class Course(
    val slug: String = "",
    val n: Int = 0,
    val title: String = "",
    val modules: List<CourseModule> = emptyList(),
)

/** A course on a list: its counts, and no Drive id anywhere,
    because a list draws none. */
@Serializable
data class CourseSummary(
    val slug: String = "",
    val n: Int = 0,
    val title: String = "",
    val modules: Int = 0,
    val lessons: Int = 0,
    val videos: Int = 0,
    val pending: Int = 0,
)

/** A certificate, a specialisation, a bundle: one folder holding
    a run of courses meant to be taken in order.

    The eight courses were never eight courses; they are the eight
    of one certificate. A shelf that listed them flat had nothing
    to say about which belonged to which. */
@Serializable
data class ProgrammeSummary(
    val slug: String = "",
    val n: Int = 0,
    val title: String = "",
    val courses: List<CourseSummary> = emptyList(),
    val modules: Int = 0,
    val lessons: Int = 0,
    val videos: Int = 0,
    val pending: Int = 0,
)

/** What the one-course answer says about the programme holding
    it. Sent beside the course rather than inside it, so nothing
    that reads a course has to know about certificates. */
@Serializable
data class ProgrammeName(val slug: String = "", val title: String = "")

/* ---------- the four answers ----------

   `ok` is on every one of them because the Worker's own `ok()`
   and `fail()` put it there, and a 200 with `ok: false` is a
   shape this app should never have to guess at. */

/** `GET /api/courses`.

    THE KEY IS `courses` AND THE ROWS ARE PROGRAMMES. That is the
    site's, not a mistake here: `listForBrowser()` spreads
    `programmeCounts()`, whose own `courses` is a NUMBER, and then
    writes the array over it. How many courses a programme has is
    `courses.size` on the row inside. Renaming it here would make
    this app the only reader of a key that does not exist. */
@Serializable
data class CatalogueResponse(
    val ok: Boolean = false,
    val courses: List<ProgrammeSummary> = emptyList(),
)

/** `GET /api/courses/<programme>/<course>`. */
@Serializable
data class CourseResponse(
    val ok: Boolean = false,
    val course: Course = Course(),
    /** Absent only from a Worker older than the programme
        segment, which is the one case a course page has no
        certificate name to print. */
    val programme: ProgrammeName? = null,
)

/** `GET /api/courses/ticket/<id>`: a pass for one file, good for
    half an hour. The whole path comes back with the pass already
    on it. */
@Serializable
data class TicketResponse(val ok: Boolean = false, val url: String = "")

/** `GET /api/courses/reading/<id>`: a saved page, sanitised by
    the Worker with the same sanitiser the site's editor runs. */
@Serializable
data class ReadingResponse(
    val ok: Boolean = false,
    val title: String = "",
    val html: String = "",
)

@Serializable
data class QuizQuestion(
    val n: Int = 0,
    /** Sanitised HTML, so it goes through the body parser like
        any other prose on this phone. */
    val prompt: String = "",
    val multiple: Boolean = false,
    val options: List<String> = emptyList(),
)

/** `GET /api/courses/quiz/<id>`.

    `parsed` is said out loud rather than inferred from an empty
    list, because "this is not a quiz" and "this quiz has no
    questions in it" are different faults with different fixes.
    When it is false, `html` is the same sanitised page a reading
    would have been and the screen draws that instead. */
@Serializable
data class QuizResponse(
    val ok: Boolean = false,
    val title: String = "",
    val questions: List<QuizQuestion> = emptyList(),
    val parsed: Boolean = false,
    val html: String = "",
)

/* ---------- where a thing lives ----------

   One function each, and every screen calls them rather than
   building a path. The rail, the route, the "mark complete and
   continue" button and the bookmark all have to agree, and the
   way they agree is by asking here. */

/** The section's front door. Written out once, here, because
    there is no manifest entry to read it from: the shelf is
    admin-only, so it is in nobody's menu. */
const val COURSES_PATH: String = "/skills/courses"

fun programmePath(programme: String): String = "$COURSES_PATH/$programme"

fun coursePath(programme: String, course: String): String =
    "$COURSES_PATH/$programme/$course"

fun courseModulePath(programme: String, course: String, mod: String): String =
    "$COURSES_PATH/$programme/$course/$mod"

fun courseLessonPath(
    programme: String,
    course: String,
    mod: String,
    lesson: String,
): String = "$COURSES_PATH/$programme/$course/$mod/$lesson"

/**
 * What a tick is filed under, and THE PROGRAMME IS NOT IN IT.
 *
 * All three of course, module and lesson, because a lesson slug
 * is only unique inside its module: `01_get-started` exists in
 * more than one course and a reader who finished one has not
 * finished the other.
 *
 * The address grew a programme segment and the tick deliberately
 * did not. `courses-read` holds these strings in real accounts
 * and `courses-answers` holds them with two more segments on the
 * end; filing a course differently is not the same as a reader
 * not having watched it, and renaming a key does not move
 * somebody's ticks, it loses them.
 *
 * The price is that a course slug has to be unique across the
 * whole catalogue. The website's `check-courses.ts` fails on a
 * collision, which is where that is enforced; this app is a
 * reader of the id, not its author.
 */
fun courseLessonId(course: String, mod: String, lesson: String): String =
    "$course/$mod/$lesson"

/** One answer to one option of one question, filed beside the
    ticks: the checkpoint shape with one more segment.

    It records what was PICKED and never whether it was right. A
    Coursera export carries no answer key, so there is nothing to
    mark against and nothing here pretends otherwise. */
fun courseAnswerId(lessonId: String, question: Int, option: Int): String =
    "$lessonId#$question#$option"

/** Where the bytes come from. This site's origin, never Drive's:
    a private Drive file cannot be fetched by a client that is not
    signed into Drive, and the Worker is the one thing holding
    that credential. */
fun courseFilePath(drive: String): String = "/api/courses/file/$drive"

/** A video's captions, converted from SubRip on the way through.
    A different path from the bytes because the bytes are
    rewritten rather than passed along. */
fun courseCaptionsPath(drive: String): String = "/api/courses/captions/$drive"

/** Drive's own page, for one job only: "open the original", for
    when somebody wants the file rather than this app's rendering
    of it. Never used for playback. */
fun driveUrl(drive: String): String = "https://drive.google.com/file/d/$drive/view"

/** The pass out of a ticket's URL.

    `/ticket/<id>` answers with the whole address, pass attached,
    and captions need the same pass on a different path. Read with
    a pattern rather than a URL parser, which would need a host
    invented for it to have something to parse. */
fun ticketPass(url: String): String? =
    Regex("""[?&]t=([^&]*)""").find(url)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

/* ---------- the ladder ---------- */

/** A lesson with everything a screen needs to draw it, which is
    more than the lesson knows about itself. */
data class CourseRung(
    val lesson: CourseLesson,
    /** The course this belongs to, carried rather than read back
        out of `id`. A caller building the lesson's address needs
        it, and `id.substringBefore('/')` is the same string by
        an argument about a format rather than by a field. */
    val course: String,
    val module: String,
    val moduleTitle: String,
    val moduleNumber: Int,
    /** The tick's id, with no programme in it. */
    val id: String,
    /** The address, with the programme in it. */
    val url: String,
) {
    val title: String get() = lesson.title
    val section: String get() = lesson.section
}

/**
 * Every lesson of a course, in order, with its id and address.
 *
 * Computed rather than stored, for the reason every derived list
 * in this app is: the rail, the bars and the continue button all
 * walk this, and three computations are three chances to compute
 * it differently.
 */
fun laddered(programme: String, course: Course): List<CourseRung> =
    course.modules.flatMap { mod ->
        mod.lessons.map { lesson ->
            CourseRung(
                lesson = lesson,
                course = course.slug,
                module = mod.slug,
                moduleTitle = mod.title,
                moduleNumber = mod.n,
                id = courseLessonId(course.slug, mod.slug, lesson.slug),
                url = courseLessonPath(programme, course.slug, mod.slug, lesson.slug),
            )
        }
    }

/**
 * The first lesson with no tick, which is where "start" and
 * "carry on" both point.
 *
 * The bookmark is where the reader WAS; what they want is where
 * to go next, and those are the same only until they finish the
 * lesson they were on. So: the first unticked lesson at or after
 * the bookmark, and failing that the first unticked lesson at
 * all. Null when the course is finished, which the caller says
 * out loud rather than sending somebody back to lesson one.
 */
fun nextUp(rungs: List<CourseRung>, read: Set<String>, bookmark: String?): CourseRung? {
    val at = bookmark?.let { mark -> rungs.indexOfFirst { it.id == mark } } ?: -1
    val after = if (at == -1) emptyList() else rungs.drop(at)
    return after.firstOrNull { it.id !in read }
        ?: rungs.firstOrNull { it.id !in read }
}

/* ---------- the counting ---------- */

/** The ids of one module's lessons, which is what a per-module
    bar is counted against. */
fun moduleIds(course: String, mod: CourseModule): List<String> =
    mod.lessons.map { courseLessonId(course, mod.slug, it.slug) }

/**
 * How many of the reader's ticks belong to these courses.
 *
 * A tick is `<course>/<module>/<lesson>` and carries no
 * programme, so a programme is counted by the slugs of the
 * courses in it. That is the whole reason this function exists
 * rather than a set intersection at the call site.
 */
fun doneIn(read: Set<String>, courses: List<CourseSummary>): Int {
    val slugs = courses.map { it.slug }.toSet()
    return read.count { it.substringBefore('/') in slugs }
}

/** What a course adds up to, counted from the course rather than
    from the summary, for the pages that hold the whole thing. */
fun countsOf(course: Course): CourseSummary = CourseSummary(
    slug = course.slug,
    n = course.n,
    title = course.title,
    modules = course.modules.size,
    lessons = course.modules.sumOf { it.lessons.size },
    videos = course.modules.sumOf { mod -> mod.lessons.count { it.video != null } },
    pending = course.modules.count { it.pending },
)

/* ---------- the words ---------- */

/** What kind of thing a lesson is, as a word.

    Shown because a reader deciding whether they have twenty
    minutes needs to know whether this is a video or a quiz before
    they open it. An unfamiliar kind prints itself rather than
    being swallowed. */
fun courseKindWord(kind: String): String = when (kind) {
    "video" -> "Video"
    "reading" -> "Reading"
    "quiz" -> "Quiz"
    "exam" -> "Challenge"
    "file" -> "File"
    else -> kind
}

/** A number and the thing it counts, pluralised. Every number on
    a card comes through here from the row the API sent, never
    from a sentence somebody typed. */
fun counted(n: Int, thing: String): String = "$n $thing" + if (n == 1) "" else "s"

/** What a card says it holds. `lead` is the programme's course
    count, which a course row has nothing to say in place of. */
fun courseTotals(
    modules: Int,
    lessons: Int,
    videos: Int,
    pending: Int,
    lead: String? = null,
): String = buildString {
    if (lead != null) append("$lead, ")
    append(counted(modules, "module"))
    append(", ")
    append(counted(lessons, "lesson"))
    if (videos > 0) append(", $videos with video")
    if (pending > 0) append(". $pending not imported yet")
}

fun courseTotals(row: CourseSummary): String =
    courseTotals(row.modules, row.lessons, row.videos, row.pending)

fun courseTotals(row: ProgrammeSummary): String =
    courseTotals(
        row.modules,
        row.lessons,
        row.videos,
        row.pending,
        lead = counted(row.courses.size, "course"),
    )

/* ---------- which of the five views an address names ---------- */

/**
 * Where a course address goes.
 *
 * THE SEGMENT COUNT DECIDES, exactly as it does in the browser:
 * none is the shelf, one a programme, two a course, three a
 * module, four a lesson.
 *
 * `index.html` and a lesson's `.html` are tolerated rather than
 * redirected, and the website's own note is the argument: 845
 * addresses generated out of a Drive folder cannot be one
 * redirect rule each without going stale the first time that
 * folder changes, and the whole section is admin-only and
 * unlisted, so there is no canonical to split and no crawler to
 * confuse. The only holder of an old address is one person's
 * history, and now their phone, which is exactly why it is
 * handled here too.
 */
sealed interface CourseWhere {
    data object Shelf : CourseWhere
    data class Programme(val programme: String) : CourseWhere
    data class One(val programme: String, val course: String) : CourseWhere
    data class Module(
        val programme: String,
        val course: String,
        val module: String,
    ) : CourseWhere

    data class Lesson(
        val programme: String,
        val course: String,
        val module: String,
        val lesson: String,
    ) : CourseWhere
}

/**
 * Read a `/skills/courses/...` path into one of the five.
 *
 * Null for an address that is not in this section at all, and for
 * one with more segments than a lesson has: five segments is not
 * a deeper page, it is a typo, and drawing the nearest thing
 * would be this app guessing.
 */
fun courseWhere(path: String): CourseWhere? {
    val clean = path.substringBefore('#').substringBefore('?')
    if (clean != COURSES_PATH && !clean.startsWith("$COURSES_PATH/")) return null

    val parts = clean.removePrefix(COURSES_PATH).split("/").filter { it.isNotEmpty() }
    /* An `index.html` is the level above it, whatever level that
       is: the old spellings needed the suffix to tell a hub from
       a lesson, which were the same length. */
    val trimmed = if (parts.lastOrNull()?.equals("index.html", ignoreCase = true) == true) {
        parts.dropLast(1)
    } else {
        parts
    }

    return when (trimmed.size) {
        0 -> CourseWhere.Shelf
        1 -> CourseWhere.Programme(trimmed[0])
        2 -> CourseWhere.One(trimmed[0], trimmed[1])
        3 -> CourseWhere.Module(trimmed[0], trimmed[1], trimmed[2])
        4 -> CourseWhere.Lesson(
            trimmed[0],
            trimmed[1],
            trimmed[2],
            /* The `.html` strip is a lesson's alone. A slug does
               not carry one here, unlike an article's and unlike
               a school lesson's, where the suffix is part of the
               slug and in every stored link. */
            trimmed[3].removeSuffix(".html").removeSuffix(".HTML"),
        )
        else -> null
    }
}
