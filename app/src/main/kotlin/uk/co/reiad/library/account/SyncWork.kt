package uk.co.reiad.library.account

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uk.co.reiad.library.data.Reiad

/* ============================================================
   A tick made on a train lands later, without the app being open.

   This is the half a browser cannot have. On the site, a tick
   made offline sits in localStorage until the reader opens a page
   again with a connection; there is no other moment for it to
   happen in. An app has one: the system will run this when a
   network comes back, whether or not anybody has opened anything.

   ---- why it is one-time and not periodic ----

   A periodic worker asks the system to wake the app on a
   schedule, which costs battery on a device that may have nothing
   to say. This is queued when something CHANGES and constrained
   on a network, so a phone with no signal in a pocket does
   nothing at all until there is a point.

   `KEEP` rather than `REPLACE`: several ticks in a row are one
   exchange, and replacing the queued work each time would push
   the exchange further away with every tick a reader makes.
   ============================================================ */

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reiad = Reiad(applicationContext)
        val account = Account(applicationContext)
        if (account.token() == null) {
            /* Signed out. Not a failure and not worth retrying:
               there is nothing to exchange and nobody to
               exchange it with. */
            return Result.success()
        }
        val sync = Sync(applicationContext, account, reiad.store)
        /* Retried rather than failed, because the common reason
           is a network that came back and went again. */
        return if (sync.exchange()) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "reiad-sync"

        /** Ask for an exchange when there is a network for it. */
        fun soon(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    NAME,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
            }
        }
    }
}
