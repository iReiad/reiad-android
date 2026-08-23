package uk.co.reiad.library.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/* ============================================================
   Fetching a whole school, in the background.

   This is the half a browser cannot have, and it is the same
   argument `SyncWorker` makes one file along: a service worker
   can only cache what somebody navigates to, so "download this
   school" is not a thing a website can offer. The system will run
   this on a network the reader is not watching.

   ---- unmetered, and that is a decision ----

   A school is a megabyte of prose, which is nothing on wifi and
   is not nothing on a Bangladeshi mobile bundle. So it waits for
   an unmetered network by default: a reader who follows a school
   on the train gets it when they get home, and the shelf says
   "waiting" rather than pretending.

   ---- the ladder first, then every lesson ----

   `Reiad.ladder` and `Reiad.lesson` both write to the cache on
   the way past, so this needs no store of its own: it asks for
   things in order and the cache does the keeping.
   ============================================================ */

class SchoolWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val school = inputData.getString(SCHOOL) ?: return Result.success()
        val reiad = Reiad(applicationContext)

        /* Followed since this was queued? A reader who changed
           their mind should not have a megabyte arrive anyway. */
        if (!Shelf(applicationContext).following(school)) return Result.success()

        val ladder = reiad.ladder(school).value ?: return Result.retry()
        var failed = 0
        for (stage in ladder.stages) {
            for (lesson in stage.lessons) {
                if (reiad.lesson(school, stage.slug, lesson.slug).value == null) failed += 1
            }
            /* The practice book too, where a stage has one: a
               school kept for a plane with its books missing is
               a school that is not kept. */
            if (stage.workbook != null) reiad.book(stage.slug)
        }
        /* Retried rather than failed on a partial run, and the
           retry is cheap: everything already fetched comes back
           out of the cache on the second pass. */
        return if (failed == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val SCHOOL = "school"

        /** Ask for a whole school, when there is wifi for it. */
        fun fetch(context: Context, school: String, anyNetwork: Boolean = false) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "reiad-school-$school",
                    /* REPLACE rather than KEEP, because unlike a
                       sync exchange this is idempotent and the
                       newer request may carry a different network
                       constraint: a reader pressing "get it now"
                       is asking for the metered one. */
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<SchoolWorker>()
                        .setInputData(workDataOf(SCHOOL to school))
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(
                                    if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED,
                                )
                                .build(),
                        )
                        .build(),
                )
            }
        }

        fun stop(context: Context, school: String) {
            runCatching {
                WorkManager.getInstance(context).cancelUniqueWork("reiad-school-$school")
            }
        }
    }
}
