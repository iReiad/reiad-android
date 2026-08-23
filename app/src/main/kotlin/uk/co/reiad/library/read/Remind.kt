package uk.co.reiad.library.read

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import uk.co.reiad.library.core.School
import uk.co.reiad.library.data.Reiad
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/* ============================================================
   One reminder a day, and it has something to say or it says
   nothing.

   ---- what it is NOT ----

   It is not a streak, it is not a nudge, and it never mentions a
   day that was missed. `ROUTINE.md`'s own rule holds here and it
   is stronger than "no streaks" because it says why: a number
   that can go down is a number that punishes somebody for
   living, and a notification that says "you haven't read today"
   is that number in a sentence.

   So this carries ONE fact, and it is a fact about the reading
   rather than about the reader: the lesson you were in the
   middle of, by name. Tapping it opens that lesson.

   **And with no bookmark it posts nothing at all.** A reminder
   with nothing to remind is a notification for its own sake,
   which is how an app earns being turned off. A reader who has
   never opened a lesson does not need to be told so daily.

   ---- and it does not travel ----

   `remind-at` is a LOCAL key and is deliberately not in
   `SyncKeys`. A reminder is a fact about one handset: the site
   cannot post one, a second phone should not inherit one, and a
   field the browser carries and can never act on is the "carried
   and never drawn" failure `ManifestSurfaceTest` exists for,
   pointed the other way.
   ============================================================ */

class RemindWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reiad = Reiad(applicationContext)

        /* The most recent bookmark across every school, which is
           the same question the home screen widget asks and the
           same answer: a reader mid-way through two schools wants
           the one they touched last. */
        val latest = School.entries
            .mapNotNull { school -> reiad.bookmark(school).first()?.let { school to it } }
            .filter { (_, mark) -> mark.title.isNotBlank() }
            .maxByOrNull { (_, mark) -> mark.ts }
            ?: return Result.success()

        val (school, mark) = latest
        val name = reiad.cachedManifest()?.ladders
            ?.firstOrNull { it.key == school.id }?.bn ?: school.id

        post(applicationContext, title = mark.title, school = name, url = mark.url)
        return Result.success()
    }

    companion object {
        private const val NAME = "reiad-remind"
        private const val CHANNEL = "reiad-remind"
        private const val ID = 4201

        /** Ask for one a day at `at`, or stop asking.

            `ExistingPeriodicWorkPolicy.UPDATE` rather than KEEP,
            because the whole point of calling this again is a
            reader who changed the time: KEEP would leave the old
            schedule in place and the setting would look saved and
            do nothing. */
        fun at(context: Context, time: LocalTime?) {
            runCatching {
                val work = WorkManager.getInstance(context)
                if (time == null) {
                    work.cancelUniqueWork(NAME)
                    return@runCatching
                }
                work.enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<RemindWorker>(Duration.ofDays(1))
                        .setInitialDelay(untilNext(time))
                        .build(),
                )
            }
        }

        /** How long until the next `time`, today or tomorrow.

            Local time on purpose, and re-read on every schedule
            rather than stored as an instant: a reader who flies
            to Dhaka wants nine in the evening THERE, and an
            instant computed in Brighton would arrive at three in
            the morning. */
        internal fun untilNext(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): Duration {
            var next = now.with(time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        /** `"21:00"` as a time, or null.

            Null for anything unreadable rather than a default,
            because a default here is a notification somebody did
            not ask for. */
        fun parse(value: String?): LocalTime? {
            if (value.isNullOrBlank()) return null
            return runCatching { LocalTime.parse(value) }.getOrNull()
        }

        private fun post(context: Context, title: String, school: String, url: String?) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Your lesson",
                    /* DEFAULT rather than HIGH. This is a
                       reminder, not an alert: it belongs in the
                       shade, not over whatever somebody is
                       doing. */
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "One a day, naming the lesson you were in the middle of."
                },
            )

            /* A VIEW on the site's own address, so this goes
               through the same `Address.kt` a shared link does:
               the tap and the link cannot disagree about where a
               lesson is. */
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url ?: "https://reiad.co.uk/")
                    setClassName("uk.co.reiad.library", "uk.co.reiad.library.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val note = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(uk.co.reiad.library.R.drawable.ic_read)
                .setContentTitle(title)
                .setContentText(school)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            /* Silently where the permission was refused. A reader
               who said no to notifications has said no, and an
               app that threw there would be crashing in a
               background worker for a setting somebody turned
               off. */
            runCatching { NotificationManagerCompat.from(context).notify(ID, note) }
        }
    }
}

/** The times offered, and `null` is off.

    Three rather than a picker, and the three are the shape of a
    day rather than arbitrary: before work, after lunch, after
    dinner. A minute-accurate picker would be a more precise
    answer to a question nobody asks precisely. */
val REMIND_TIMES: List<Pair<String, LocalTime?>> = listOf(
    "বন্ধ · Off" to null,
    "সকাল ৮টা · 8am" to LocalTime.of(8, 0),
    "দুপুর ২টা · 2pm" to LocalTime.of(14, 0),
    "রাত ৯টা · 9pm" to LocalTime.of(21, 0),
)
