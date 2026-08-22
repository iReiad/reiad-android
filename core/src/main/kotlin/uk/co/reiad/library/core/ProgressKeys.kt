package uk.co.reiad.library.core

/* ============================================================
   The storage keys, spelled the way real accounts spell them.

   These strings are a contract with `public.progress` in
   Supabase, where the row key IS the browser's localStorage key,
   and with every device already carrying them. Renaming one does
   not move somebody's ticks: it loses them.

   Three of them look wrong and are not:

     - the money school files under `learn-*`. It lived at /learn/
       until 17 August 2026 and the keys deliberately did not move
       with the address.
     - Qur'anic Arabic is `quran-done`, not `quran-read`.
     - the English practice book counts under `english-day`, while
       German counts under `deutsch-tag`. Not a typo, and the two
       shapes differ further down in `dayId`.

   `SyncKeys.ALL` is asserted by name in the tests for the same
   reason the web asserts all ten of its own: a rename should be
   a failing test rather than a support message.
   ============================================================ */

/** The four schools, by the id the API and the site use. */
enum class School(val id: String) {
    MONEY("money"),
    DEUTSCH("deutsch"),
    QURAN("quran"),
    ENGLISH("english");

    companion object {
        fun of(id: String): School? = entries.firstOrNull { it.id == id }
    }
}

/** How a key is reconciled when two devices disagree. The web
    calls these `set`, `mark` and `count` and the arithmetic is in
    `SyncRules`. */
enum class MergeRule { SET, MARK, COUNT }

object ProgressKeys {

    /** Which lessons have been ticked. */
    fun read(school: School): String = when (school) {
        School.MONEY -> "learn-read"
        School.DEUTSCH -> "deutsch-read"
        School.QURAN -> "quran-done"
        School.ENGLISH -> "english-read"
    }

    /** Where the reader left off. Holds an object, not a set. */
    fun last(school: School): String = when (school) {
        School.MONEY -> "learn-last"
        School.DEUTSCH -> "deutsch-last"
        School.QURAN -> "quran-last"
        School.ENGLISH -> "english-last"
    }

    /** Checkpoints inside a lesson body, filed `<lesson id>#<n>`.
        Never counted towards a ladder: a checkpoint is not a
        lesson. */
    fun checks(school: School): String = "${prefix(school)}-checks"

    /** Practice-book days that have been ticked. Only two schools
        have a book, so this is null for the other two. */
    fun days(school: School): String? = when (school) {
        School.DEUTSCH -> "deutsch-days"
        School.ENGLISH -> "english-days"
        else -> null
    }

    /** The last book day reached, as a number. `english-day` and
        `deutsch-tag`: the asymmetry is real and is load bearing. */
    fun dayCount(school: School): String? = when (school) {
        School.DEUTSCH -> "deutsch-tag"
        School.ENGLISH -> "english-day"
        else -> null
    }

    /** The school's own half of a key name. Money is `learn`. */
    private fun prefix(school: School): String =
        if (school == School.MONEY) "learn" else school.id

    /* The keys that belong to no single school. */
    const val COURSES_READ = "courses-read"
    const val COURSES_LAST = "courses-last"
    const val COURSES_ANSWERS = "courses-answers"
    const val DAYS_ACTIVE = "days-active"
    const val READER_PREFS = "reader-prefs"
}

/** Every key that travels to the account, with the rule that
    reconciles it. The browser's own table is `KEYS` in
    `aab/src/sync.ts`; this is the same list and must stay the
    same list. */
object SyncKeys {

    val ALL: Map<String, MergeRule> = buildMap {
        for (school in School.entries) {
            put(ProgressKeys.read(school), MergeRule.SET)
            put(ProgressKeys.last(school), MergeRule.MARK)
            put(ProgressKeys.checks(school), MergeRule.SET)
            ProgressKeys.days(school)?.let { put(it, MergeRule.SET) }
            ProgressKeys.dayCount(school)?.let { put(it, MergeRule.COUNT) }
        }
        put(ProgressKeys.COURSES_READ, MergeRule.SET)
        put(ProgressKeys.COURSES_LAST, MergeRule.MARK)
        put(ProgressKeys.COURSES_ANSWERS, MergeRule.SET)
        put(ProgressKeys.DAYS_ACTIVE, MergeRule.SET)
        put(ProgressKeys.READER_PREFS, MergeRule.MARK)
    }

    fun ruleOf(key: String): MergeRule? = ALL[key]
}

/* ---------- what an id looks like ---------- */

/** A lesson's progress id.

    `basics-1` is the exception and it is deliberate: those
    eighteen term pages were the money school's glossary before
    the school had a builder, and they are ticked under a bare
    slug. Everything else is `<stage>/<lesson>`. The same rule is
    written twice on the web, in `shared/schools.ts` and in the
    money curriculum, so it is written once here. */
fun lessonId(stage: String, lesson: String): String =
    if (stage == "basics-1") lesson else "$stage/$lesson"

/** A checkpoint inside a lesson body: position, never text.
    Prose gets edited, and a checkpoint that forgot itself over a
    fixed typo is worse than one that stays put. */
fun checkpointId(lessonId: String, index: Int): String = "$lessonId#$index"

/** A practice-book day. German counts in `tag`, English in `day`,
    and the shared engine on the web takes this as an argument
    because building the German shape for both silently lost every
    English tick. */
fun dayId(school: School, stage: String, day: Int): String = when (school) {
    School.DEUTSCH -> "$stage/tag-$day"
    School.ENGLISH -> "$stage/day-$day"
    else -> "$stage/day-$day"
}
