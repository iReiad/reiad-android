package uk.co.reiad.library.courses

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.reiad.library.core.CatalogueResponse
import uk.co.reiad.library.core.CourseResponse
import uk.co.reiad.library.core.CourseWhere

/* ============================================================
   What the five screens are looking at.

   Two fetches between them, which is the site's own arrangement
   rather than one this app invented:

     - the SHELF answers both the shelf and a programme. There is
       no endpoint for one programme, because the whole shelf is a
       title and five numbers per certificate and is smaller than
       one course's ladder.
     - ONE COURSE answers the course, a module and a lesson. A
       lesson page needs its own course's ladder for the rail and
       has no use for the other seven, so asking for all eight
       would be forty times the bytes and seven courses' worth of
       Drive ids sent to a page that draws none of them.

   ---- and nothing here is written to disk ----

   Every other fetch in this app caches its raw JSON under
   `cache:<what>` so a second launch with no network is the site
   as it last was. This one does not, deliberately: the catalogue
   is admin-only material in somebody's private Drive, the
   endpoint answers `no-store`, and a copy on the filesystem would
   outlive the session that was allowed to fetch it. So the shelf
   is held in memory for as long as the screen is, and a phone
   with no signal says so rather than showing a saved copy of
   somebody else's course.

   That is the one place this section is deliberately WORSE than a
   school, and it is the right trade.
   ============================================================ */

/**
 * The two answers, and what they were asked for.
 *
 * Null means "not asked yet", which is a third state and not the
 * same as a failure: a screen showing a retry button before it
 * has tried once is a screen that has already given up.
 */
class Desk(
    /** The client this was built with, exposed because the lesson
        screen fetches its own reading, quiz and passes with it.

        Held HERE rather than beside it on the model: two fields
        set at the same moment are two fields that can be read a
        frame apart, and the screen needs both or neither. */
    val catalogue: Catalogue,
) {

    private val _shelf = MutableStateFlow<Answer<CatalogueResponse>?>(null)
    val shelf: StateFlow<Answer<CatalogueResponse>?> = _shelf.asStateFlow()

    private val _course = MutableStateFlow<Answer<CourseResponse>?>(null)
    val course: StateFlow<Answer<CourseResponse>?> = _course.asStateFlow()

    /** Which course `_course` is holding, so opening a lesson of
        the course already on screen does not re-fetch its whole
        ladder, and opening a different one does. */
    private var holding: Pair<String, String>? = null

    /** Whether a fetch is in flight, so the two entry points below
        cannot stack requests when a reader taps twice. */
    private var askingShelf = false
    private var askingCourse: Pair<String, String>? = null

    /**
     * Fetch whatever the address needs, and nothing it does not.
     *
     * Called on every navigation inside the section, including
     * the ones that need nothing, which is why every branch here
     * is allowed to do nothing at all.
     */
    suspend fun open(where: CourseWhere) {
        when (where) {
            is CourseWhere.Shelf, is CourseWhere.Programme -> needShelf()
            is CourseWhere.One -> needCourse(where.programme, where.course)
            is CourseWhere.Module -> needCourse(where.programme, where.course)
            is CourseWhere.Lesson -> needCourse(where.programme, where.course)
        }
    }

    /** Ask again, from a retry button. Clears what is held first,
        so the screen shows it is trying rather than sitting on
        the old failure until the new answer lands. */
    suspend fun again(where: CourseWhere) {
        when (where) {
            is CourseWhere.Shelf, is CourseWhere.Programme -> {
                _shelf.value = null
                needShelf()
            }
            is CourseWhere.One -> retryCourse(where.programme, where.course)
            is CourseWhere.Module -> retryCourse(where.programme, where.course)
            is CourseWhere.Lesson -> retryCourse(where.programme, where.course)
        }
    }

    /** Signing out takes the mirror off, and this is part of the
        mirror: a catalogue left in memory after a sign-out is
        somebody else's material on a phone that is no longer
        allowed to ask for it. */
    fun forget() {
        _shelf.value = null
        _course.value = null
        holding = null
    }

    private suspend fun needShelf() {
        if (_shelf.value is Answer.Got || askingShelf) return
        askingShelf = true
        try {
            _shelf.value = catalogue.shelf()
        } finally {
            askingShelf = false
        }
    }

    private suspend fun needCourse(programme: String, course: String) {
        val want = programme to course
        if (holding == want && _course.value is Answer.Got) return
        if (askingCourse == want) return
        /* A different course than the one on screen: drop it
           before asking, or the module list of the last course
           draws under the new course's head for as long as the
           fetch takes. */
        if (holding != want) _course.value = null
        askingCourse = want
        try {
            val answer = catalogue.course(programme, course)
            _course.value = answer
            holding = if (answer is Answer.Got) want else null
        } finally {
            askingCourse = null
        }
    }

    private suspend fun retryCourse(programme: String, course: String) {
        holding = null
        _course.value = null
        needCourse(programme, course)
    }
}
