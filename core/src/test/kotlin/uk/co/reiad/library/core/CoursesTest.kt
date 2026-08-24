package uk.co.reiad.library.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/* ============================================================
   The course catalogue, and the ONE fixture in this tree that
   was not captured from the live API.

   Every other file under `fixtures/` is a real answer, because a
   fixture kinder than the thing it stands in for is not a test.
   These two cannot be, and the reason is the whole point of the
   section they describe.

   `/api/courses` answers 200 to one admin and 401 to everybody
   else. Its payload is a catalogue of somebody else's material in
   one person's private Drive folder: 8 courses, 43 modules, 794
   lessons and the Drive id behind each one. **This repository is
   public.** Committing a captured answer would publish, in a
   git history nobody can take it out of, exactly the thing the
   endpoint exists to keep unpublished. And the website's own
   rule is that nothing course-shaped goes into a client bundle at
   all.

   ---- so the values are invented and the SHAPE is not ----

   `courses.json` and `course-first.json` were produced by running
   the website's own `listForBrowser()` and `forBrowser()`, the
   two functions that actually answer these routes, over a
   catalogue of four made-up lessons. Every key in them is the
   emitter's, in the emitter's order, with `null` where the
   emitter writes `null`. Only the strings are invented, and they
   are invented visibly: a real Drive id is thirty-three
   characters of base64 and these say `drive-video-1`.

   That locks the thing most likely to drift, which is the field
   set, and `noFieldOfALessonIsSilentlyDropped` below is what
   holds it. It does not lock the data, and nothing here pretends
   it does.

   ---- what the four lessons are for ----

   One of every kind the catalogue has, because each takes a
   different path through the screen: a video with captions, a
   transcript and an attached file; a reading; a quiz; a lesson
   with NO file against it at all, which is a real row and used to
   be the one that drew a blank page. Plus a module marked
   `pending`, and a second course, so the shelf's arithmetic has
   more than one of anything to add up.
   ============================================================ */

private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

private fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/fixtures/$name.json")) {
        "missing fixture $name.json"
    }.readBytes().decodeToString()

private val catalogue: CatalogueResponse =
    json.decodeFromString(CatalogueResponse.serializer(), fixture("courses"))

private val answer: CourseResponse =
    json.decodeFromString(CourseResponse.serializer(), fixture("course-first"))

class CoursesTest {

    private val programme = catalogue.courses.single()
    private val course = answer.course

    /* ---------- the payload decodes as the site emits it ---------- */

    @Test
    fun `the shelf's rows are programmes, under a key called courses`() {
        /* Not a mistake, and not one to tidy up: `listForBrowser`
           spreads `programmeCounts()`, whose own `courses` is a
           NUMBER, and writes the array over it. Renaming the field
           here would make this app the only reader of a key that
           does not exist. */
        assertEquals(1, catalogue.courses.size)
        assertEquals("sample-certificate", programme.slug)
        assertEquals(2, programme.courses.size)
    }

    @Test
    fun `a programme's totals are its courses' totals added up`() {
        assertEquals(programme.courses.sumOf { it.modules }, programme.modules)
        assertEquals(programme.courses.sumOf { it.lessons }, programme.lessons)
        assertEquals(programme.courses.sumOf { it.videos }, programme.videos)
        assertEquals(programme.courses.sumOf { it.pending }, programme.pending)
    }

    @Test
    fun `the course answer carries the certificate that holds it`() {
        /* The only place a page below the shelf can learn the
           certificate's name without a second request. */
        assertEquals("Sample Certificate", answer.programme?.title)
        assertEquals("sample-certificate", answer.programme?.slug)
    }

    @Test
    fun `counting the course itself agrees with the shelf's row for it`() {
        val row = programme.courses.first { it.slug == course.slug }
        assertEquals(row, countsOf(course).copy(title = row.title))
    }

    /* ---------- the tick's id ---------- */

    @Test
    fun `a tick names the course, the module and the lesson, and not the programme`() {
        /* These strings are in real accounts under `courses-read`.
           Adding the programme segment would not move somebody's
           ticks, it would lose them. */
        assertEquals(
            "first-course/01_beginnings/01_a-welcome",
            courseLessonId("first-course", "01_beginnings", "01_a-welcome"),
        )
        val rung = laddered("sample-certificate", course).first()
        assertEquals("first-course/01_beginnings/01_a-welcome", rung.id)
        assertTrue(
            "sample-certificate" !in rung.id,
            "the programme reached the tick id: ${rung.id}",
        )
    }

    @Test
    fun `an address has the programme in it and the id does not`() {
        val rung = laddered("sample-certificate", course).first()
        assertEquals(
            "/skills/courses/sample-certificate/first-course/01_beginnings/01_a-welcome",
            rung.url,
        )
    }

    @Test
    fun `an answer is a tick id with the question and the option on the end`() {
        assertEquals(
            "first-course/01_beginnings/03_a-few-questions#2#1",
            courseAnswerId("first-course/01_beginnings/03_a-few-questions", 2, 1),
        )
    }

    /* ---------- the ladder ---------- */

    @Test
    fun `the ladder is every lesson of every module, in order`() {
        val rungs = laddered("sample-certificate", course)
        assertEquals(6, rungs.size)
        assertEquals(
            listOf("A welcome", "A page to read", "A few questions", "Nothing behind it",
                "The long one", "The challenge"),
            rungs.map { it.title },
        )
        /* A pending module contributes nothing, which is what
           makes it visible rather than a course with fewer
           weeks in it. */
        assertTrue(rungs.none { it.module == "03_not-imported" })
    }

    @Test
    fun `the ladder carries the module a lesson is in, for the rail to head`() {
        val rungs = laddered("sample-certificate", course)
        assertEquals("Beginnings", rungs.first().moduleTitle)
        assertEquals(2, rungs.last().moduleNumber)
    }

    /* ---------- where to go next ---------- */

    @Test
    fun `with nothing read, the way in is the first lesson`() {
        val rungs = laddered("sample-certificate", course)
        assertEquals(rungs.first().id, nextUp(rungs, emptySet(), null)?.id)
    }

    @Test
    fun `carry on is the first UNREAD lesson at or after the bookmark`() {
        val rungs = laddered("sample-certificate", course)
        /* The bookmark is where the reader WAS. They finished it,
           so what they want is the one after, not the one they
           are standing on. */
        val was = rungs[1]
        val next = nextUp(rungs, setOf(rungs[0].id, was.id), was.id)
        assertEquals(rungs[2].id, next?.id)
    }

    @Test
    fun `a bookmark on an unfinished lesson goes back to that lesson`() {
        val rungs = laddered("sample-certificate", course)
        assertEquals(rungs[3].id, nextUp(rungs, setOf(rungs[0].id), rungs[3].id)?.id)
    }

    @Test
    fun `a bookmark past the last gap still finds the gap`() {
        val rungs = laddered("sample-certificate", course)
        /* Everything from the bookmark on is done and something
           earlier is not: falling back to the first unread is
           what stops "carry on" saying the course is finished
           when it is not. */
        val read = rungs.drop(2).map { it.id }.toSet()
        assertEquals(rungs[0].id, nextUp(rungs, read, rungs.last().id)?.id)
    }

    @Test
    fun `a finished course has no next, and says so rather than starting over`() {
        val rungs = laddered("sample-certificate", course)
        assertNull(nextUp(rungs, rungs.map { it.id }.toSet(), rungs.first().id))
    }

    @Test
    fun `a bookmark from another course is ignored rather than obeyed`() {
        val rungs = laddered("sample-certificate", course)
        assertEquals(
            rungs.first().id,
            nextUp(rungs, emptySet(), "second-course/01_only-module/01_one-lesson")?.id,
        )
    }

    /* ---------- counting a reader's own ticks ---------- */

    @Test
    fun `a programme is counted by the slugs of the courses in it`() {
        /* A tick carries no programme, so this is the only way to
           count one. A tick belonging to a course that is not in
           this programme must not be counted towards it. */
        val read = setOf(
            "first-course/01_beginnings/01_a-welcome",
            "first-course/01_beginnings/02_a-page-to-read",
            "second-course/01_only-module/01_one-lesson",
            "a-course-somewhere-else/01_x/01_y",
        )
        assertEquals(3, doneIn(read, programme.courses))
        assertEquals(2, doneIn(read, programme.courses.filter { it.slug == "first-course" }))
    }

    @Test
    fun `a module is counted against its own lessons' ids`() {
        val mod = course.modules.first()
        assertEquals(
            listOf(
                "first-course/01_beginnings/01_a-welcome",
                "first-course/01_beginnings/02_a-page-to-read",
                "first-course/01_beginnings/03_a-few-questions",
                "first-course/01_beginnings/04_nothing-behind-it",
            ),
            moduleIds(course.slug, mod),
        )
    }

    /* ---------- a lesson with nothing behind it ---------- */

    @Test
    fun `a catalogue row with no file against it is a state, not a blank page`() {
        val bare = course.modules.first().lessons.first { it.slug == "04_nothing-behind-it" }
        assertTrue(bare.empty)
        assertTrue(course.modules.first().lessons.first().empty.not())
    }

    /* ---------- the words ---------- */

    @Test
    fun `a kind prints as a word, and an unfamiliar one prints itself`() {
        assertEquals("Challenge", courseKindWord("exam"))
        assertEquals("Reading", courseKindWord("reading"))
        /* Somebody else's export, so a sixth kind should draw a
           lesson with an unfamiliar word on it rather than an
           empty chip. */
        assertEquals("workshop", courseKindWord("workshop"))
    }

    @Test
    fun `a total is the row's own numbers, pluralised`() {
        val row = programme.courses.first { it.slug == "second-course" }
        assertEquals("1 module, 1 lesson, 1 with video", courseTotals(row))
        assertEquals(
            "2 courses, 4 modules, 7 lessons, 3 with video. 1 not imported yet",
            courseTotals(programme),
        )
    }

    /* ---------- the pass ---------- */

    @Test
    fun `the pass is lifted out of the ticket's own address`() {
        assertEquals("abc.sig", ticketPass("/api/courses/file/xyz?t=abc.sig"))
        assertEquals("abc", ticketPass("/api/courses/file/xyz?a=1&t=abc"))
        assertNull(ticketPass("/api/courses/file/xyz"))
        assertNull(ticketPass("/api/courses/file/xyz?t="))
    }

    @Test
    fun `bytes and captions are two paths, because one is rewritten`() {
        assertEquals("/api/courses/file/d1", courseFilePath("d1"))
        assertEquals("/api/courses/captions/d1", courseCaptionsPath("d1"))
    }

    /* ---------- which of the five an address names ---------- */

    @Test
    fun `the segment count decides which view an address is`() {
        assertEquals(CourseWhere.Shelf, courseWhere("/skills/courses"))
        assertEquals(CourseWhere.Shelf, courseWhere("/skills/courses/"))
        assertEquals(CourseWhere.Programme("p"), courseWhere("/skills/courses/p"))
        assertEquals(CourseWhere.One("p", "c"), courseWhere("/skills/courses/p/c"))
        assertEquals(CourseWhere.Module("p", "c", "m"), courseWhere("/skills/courses/p/c/m"))
        assertEquals(
            CourseWhere.Lesson("p", "c", "m", "l"),
            courseWhere("/skills/courses/p/c/m/l"),
        )
    }

    @Test
    fun `an address from before the suffix went is still read`() {
        /* 845 addresses generated out of a Drive folder cannot be
           one redirect rule each, and the only holder of an old
           one is the person who bookmarked it. */
        assertEquals(CourseWhere.Shelf, courseWhere("/skills/courses/index.html"))
        assertEquals(CourseWhere.Programme("p"), courseWhere("/skills/courses/p/index.html"))
        assertEquals(
            CourseWhere.Lesson("p", "c", "m", "l"),
            courseWhere("/skills/courses/p/c/m/l.html"),
        )
    }

    @Test
    fun `a query or an anchor is not a segment`() {
        assertEquals(CourseWhere.One("p", "c"), courseWhere("/skills/courses/p/c?from=x"))
        assertEquals(CourseWhere.One("p", "c"), courseWhere("/skills/courses/p/c#top"))
    }

    @Test
    fun `an address outside the section, or deeper than a lesson, is not read here`() {
        assertNull(courseWhere("/skills"))
        assertNull(courseWhere("/skills/coursesomething"))
        assertNull(courseWhere("/money/basics-1/x.html"))
        /* Five segments is a typo, not a deeper page, and drawing
           the nearest thing would be this app guessing. */
        assertNull(courseWhere("/skills/courses/p/c/m/l/extra"))
    }
}

/* ============================================================
   Everything the endpoint sends, held to what this app reads.

   The same question `FoodSurfaceTest` asks of `/api/foods`, and
   it is worth asking twice: a field arrives, decodes, and is
   never drawn, so the screen renders perfectly with something
   missing that nobody can see is missing.

   Because the fixture is emitted by the site's own
   `forBrowser()`, the field set here IS the site's field set on
   the day it was generated. Regenerating it against a newer
   website checkout is what makes this fail when the emitter grows
   a field.
   ============================================================ */
class CoursesSurfaceTest {

    private val course: JsonObject = (
        Json.parseToJsonElement(fixture("course-first")) as JsonObject
        )["course"] as JsonObject

    private val shelf: JsonObject =
        ((Json.parseToJsonElement(fixture("courses")) as JsonObject)["courses"]
            as JsonArray).first() as JsonObject

    @Test fun `no field of a lesson is silently dropped`() {
        val lessons = (course["modules"] as JsonArray)
            .flatMap { ((it as JsonObject)["lessons"] as JsonArray).map { l -> l as JsonObject } }
        val fields = lessons.flatMap { it.keys }.toSet()

        val carried = setOf(
            "slug", "title", "kind", "section", "position",
            "video", "reading", "quiz", "exam", "transcript", "captions", "files",
        )
        assertEquals(
            emptySet(), fields - carried,
            "the endpoint sends ${fields - carried} on a lesson and nothing here reads it.",
        )
        assertEquals(
            emptySet(), carried - fields,
            "${carried - fields} is read here and the endpoint no longer sends it.",
        )
    }

    @Test fun `no field of a module or a course is silently dropped`() {
        assertEquals(setOf("slug", "n", "title", "modules"), course.keys)
        val mod = (course["modules"] as JsonArray).first() as JsonObject
        assertEquals(setOf("slug", "n", "title", "pending", "lessons"), mod.keys)
    }

    @Test fun `no field of a shelf row is silently dropped`() {
        assertEquals(
            setOf("slug", "n", "title", "modules", "lessons", "videos", "pending", "courses"),
            shelf.keys,
        )
        val row = (shelf["courses"] as JsonArray).first() as JsonObject
        assertEquals(
            setOf("slug", "n", "title", "modules", "lessons", "videos", "pending"),
            row.keys,
        )
    }

    @Test fun `an attached file is a name, an extension and an id`() {
        val files = (course["modules"] as JsonArray)
            .flatMap { ((it as JsonObject)["lessons"] as JsonArray) }
            .flatMap { ((it as JsonObject)["files"] as JsonArray) }
            .map { it as JsonObject }
        assertTrue(files.isNotEmpty(), "the fixture has no attached file to check")
        for (file in files) assertEquals(setOf("name", "ext", "drive"), file.keys)
    }

    /** And the fixture is the synthetic one it says it is.

        A guard rather than a formality: the way this test stops
        being honest is somebody regenerating the fixture against
        the real catalogue to "make it a proper fixture", and
        publishing 794 lessons of somebody else's course into a
        public repository in the process. */
    @Test fun `the fixture carries no real Drive id`() {
        val ids = Regex(""""(drive|video|reading|quiz|exam|transcript|captions)"\s*:\s*"([^"]+)"""")
            .findAll(fixture("course-first"))
            .map { it.groupValues[2] }
            .toList()
        assertTrue(ids.isNotEmpty())
        for (id in ids) {
            assertTrue(
                id.startsWith("drive-"),
                "`$id` is not one of this fixture's invented ids. If the real catalogue " +
                    "has been captured into this repository, take it out: it is somebody " +
                    "else's material in a private Drive folder and this repository is " +
                    "public. Regenerate against a made-up catalogue instead.",
            )
        }
    }
}
