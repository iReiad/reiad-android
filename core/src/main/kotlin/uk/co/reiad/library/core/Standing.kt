package uk.co.reiad.library.core

/* ============================================================
   Where a reader stands in one school.

   The site's `next/components/account/standing.ts`, ported, and
   in `core` rather than beside the screen for the reason
   everything here is: it is arithmetic, and arithmetic is what a
   test can hold without a phone.

   ---- the ladder is the server's and the ticks are ours ----

   Both come in as arguments. Nothing in this file reads storage
   or fetches anything, so a check can seed it and a screen can
   seed it and both get the same answer. That is the rule
   `next/lib/progress.ts` states, and the version of this the site
   replaced got it wrong: it imported all four schools'
   `curriculum.js` in the browser, 150 KB of them, to find out
   what a bar's denominator was.
   ============================================================ */

/** One lesson, flattened out of the stages, in reading order. */
data class Rung(
    /** The progress id, which is what a tick is filed under:
        `<stage>/<lesson>`, or a bare slug for the money school's
        eighteen term pages. */
    val id: String,
    val title: String,
    val stage: String,
    val slug: String,
)

data class Standing(
    val done: Int,
    val total: Int,
    val pct: Double,
    /** Where to send them, or null once every written lesson is
        ticked. */
    val next: Rung?,
    /** Whether this school has been opened at all, which decides
        "Carry on" against "Start" and the order the bars sit in. */
    val touched: Boolean,
    /** Checkpoints ticked inside those lessons, and in how many
        of them. A checkpoint is NOT a lesson and is never counted
        towards the bar: this is a second sentence under it. */
    val checksDone: Int,
    val checksLessons: Int,
)

/**
 * Every live lesson of a school, in order, flattened.
 *
 * Only what is WRITTEN, because a promised-and-unwritten lesson
 * in the denominator is a bar that can never fill. The site's
 * ladder table is built the same way.
 */
fun rungsOf(stages: List<Stage>): List<Rung> = buildList {
    for (stage in stages) {
        for (lesson in stage.lessons) {
            if (!lesson.isWritten || lesson.isSoon) continue
            add(
                Rung(
                    id = lessonId(stage.slug, lesson.slug),
                    title = lesson.bn.ifBlank { lesson.en.orEmpty() },
                    stage = stage.slug,
                    slug = lesson.slug,
                ),
            )
        }
    }
}

/**
 * @param ladder the school's live lessons in order.
 * @param read what this reader has ticked, by progress id.
 * @param last where they left off, or null.
 * @param checks the checkpoint ids ticked in this school, each
 *        `<lesson id>#<n>`.
 */
fun standingOf(
    ladder: List<Rung>,
    read: Set<String>,
    last: String? = null,
    checks: Set<String> = emptySet(),
): Standing {
    val done = ladder.count { it.id in read }

    /* Where they were, and where to go, which are the same thing
       only until the lesson they were on is finished. Same rule
       the school hubs use, and for the same reason: a resume card
       that sends you back to something already ticked is a card
       nobody presses twice. */
    val at = if (last.isNullOrBlank()) -1 else ladder.indexOfFirst { it.id == last }
    val next = if (at == -1) {
        ladder.firstOrNull { it.id !in read }
    } else {
        ladder.drop(at).firstOrNull { it.id !in read } ?: ladder.firstOrNull { it.id !in read }
    }

    /* A checkpoint is `<lesson id>#<n>`, so the lesson it belongs
       to is everything before the first hash. Counted here rather
       than by the caller because the SHAPE of the id is this
       library's, and a screen splitting on a hash would be a
       second place that knows it. */
    val lessonsWithChecks = checks.mapNotNull { it.substringBefore('#').ifBlank { null } }.toSet()

    return Standing(
        done = done,
        total = ladder.size,
        pct = if (ladder.isEmpty()) 0.0 else done.toDouble() / ladder.size * 100,
        next = next,
        touched = done > 0 || !last.isNullOrBlank() || checks.isNotEmpty(),
        checksDone = checks.size,
        checksLessons = lessonsWithChecks.size,
    )
}
