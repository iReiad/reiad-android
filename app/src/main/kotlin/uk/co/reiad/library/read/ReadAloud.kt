package uk.co.reiad.library.read

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.reiad.library.MainActivity
import uk.co.reiad.library.R
import uk.co.reiad.library.core.Pace
import uk.co.reiad.library.core.Utterance
import uk.co.reiad.library.core.languageOf
import uk.co.reiad.library.core.voiceFor
import java.util.Locale

/* ============================================================
   Reading a piece out loud, and carrying on when the screen is
   off.

   The site has a read-aloud button and it stops the moment the
   tab is hidden, because that is what a browser does to
   `speechSynthesis`. This is the same feature with the one thing
   a browser cannot give it: a FOREGROUND SERVICE, so a reader can
   put the phone in a pocket, walk to the bus stop and still be
   hearing the piece, with a Stop they can reach from the
   notification shade and the lock screen.

   ---- what this is NOT, and why ----

   It is not a `MediaSession`. A media session in media3 is a
   wrapper round a `Player`, and there is no player here: a
   synthesiser has no timeline, no duration and no seek. Writing a
   fake `Player` over `TextToSpeech` to earn a nicer notification
   would be several hundred lines pretending to answer questions
   it cannot, and the first thing to break would be scrubbing,
   which is exactly what a reader would expect a media
   notification to do.

   So: a foreground service with a plain notification carrying
   Stop. Real background playback, real controls outside the app,
   and no promise the thing cannot keep.

   ---- what is read is core's decision ----

   `speakable()` decides and is tested. This says the lines and
   reports which one it is on, so a screen can mark the place.
   ============================================================ */

/** Where the reading has got to. */
data class Speaking(
    val on: Boolean = false,
    /** Which utterance is being said, or -1. */
    val at: Int = -1,
    /** Which BLOCK it came from, so a screen can mark the
        paragraph rather than hunt for the sentence. */
    val block: Int = -1,
    /** How many lines there are, so a controller can say
        "12 of 40" and draw the groove. */
    val total: Int = 0,
    /** Held, with its place kept. A synthesiser has no real
        pause, so this is a stop that remembers: the queue and
        the index stay, and play says the same line again. */
    val paused: Boolean = false,
    /** How fast, so the control can show which step is on. */
    val pace: Pace = Pace.NORMAL,
    /** No synthesiser on this device, or it would not start.
        Shown rather than swallowed: a button that does nothing is
        worse than a button that says why. */
    val unavailable: Boolean = false,
)

/** The one reader, shared by every screen.

    A single object rather than one per screen because a phone has
    one voice: two pieces reading at once is not a state anything
    should be able to reach, and making it impossible is cheaper
    than coordinating it. */
object Reader {
    private val _state = MutableStateFlow(Speaking())
    val state: StateFlow<Speaking> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var lines: List<Utterance> = emptyList()
    private var index = 0
    private var pace: Pace = Pace.NORMAL

    /** What is being read, for the notification. */
    var title: String = ""
        private set

    /** And WHERE it came from, as the site's own href, so the
        card on the board can take the reader back to it through
        the same resolver a shared link uses. The notification
        has an intent for the app; this is the only handle
        anything else has on where the voice came from. Blank
        when a caller had none to give, and the card then simply
        does not offer to open anything. */
    var from: String = ""
        private set

    /** Every press claims a number, and a callback whose number is
        no longer the current one is ignored.

        Not decoration: the synthesiser calls back on its own
        thread after a stop, so without this a Stop followed
        quickly by a Play gets the old run's "done" and skips a
        line. The site's own module carries the same token for the
        same reason. */
    private var run = 0

    fun start(
        context: Context,
        what: String,
        utterances: List<Utterance>,
        wantPace: Pace,
        fromHref: String = "",
    ) {
        val app = context.applicationContext
        stop(app)
        if (utterances.isEmpty()) return
        title = what
        from = fromHref
        lines = utterances
        index = 0
        pace = wantPace
        run += 1
        val mine = run

        val engine = TextToSpeech(app) { status ->
            if (mine != run) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                _state.value = Speaking(unavailable = true)
                return@TextToSpeech
            }
            begin(mine)
        }
        engine.setOnUtteranceProgressListener(listener(mine, app))
        engine.setSpeechRate(pace.rate)
        tts = engine
        ReadAloudService.start(app)
    }

    /** Fresh per RUN, because the token is captured: a listener
        from a stopped run answers a dead number and is ignored,
        which is what stops a quick pause-play skipping a line. */
    private fun listener(mine: Int, app: Context) = object : UtteranceProgressListener() {
        override fun onStart(id: String?) = Unit

        override fun onDone(id: String?) {
            if (mine != run) return
            index += 1
            if (index >= lines.size) stop(app) else advance(mine)
        }

        @Deprecated("The signature without an error code")
        override fun onError(id: String?) {
            if (mine == run) stop(app)
        }
    }

    /* ---------- the controls a controller needs ----------

       A synthesiser has no timeline, so every one of these is
       said in terms it CAN keep: pause is a stop that remembers
       its place, a skip is a jump to another line's start, and a
       pace change re-says the current line at the new rate. The
       run token turns over on each, so the engine's own
       callbacks from the interrupted utterance die quietly. */

    fun pause(context: Context) {
        if (!_state.value.on || _state.value.paused) return
        run += 1
        runCatching { tts?.stop() }
        _state.value = _state.value.copy(paused = true)
    }

    fun resume(context: Context) {
        val current = _state.value
        if (!current.on || !current.paused) return
        val engine = tts ?: return
        run += 1
        val mine = run
        engine.setOnUtteranceProgressListener(listener(mine, context.applicationContext))
        _state.value = current.copy(paused = false)
        advance(mine)
    }

    /** A line forwards or back. Held stays held: skipping while
        paused moves the mark and waits for play. */
    fun skip(context: Context, delta: Int) {
        val current = _state.value
        if (!current.on || lines.isEmpty()) return
        index = (index + delta).coerceIn(0, lines.size - 1)
        run += 1
        runCatching { tts?.stop() }
        if (current.paused) {
            val line = lines.getOrNull(index) ?: return
            _state.value = current.copy(at = index, block = line.block)
        } else {
            val mine = run
            tts?.setOnUtteranceProgressListener(listener(mine, context.applicationContext))
            advance(mine)
        }
    }

    fun setPace(context: Context, want: Pace) {
        pace = want
        val engine = tts ?: return
        engine.setSpeechRate(want.rate)
        val current = _state.value
        if (current.on && !current.paused) {
            /* The rate applies from the next utterance, so the
               current line is said again at the pace that was
               just asked for rather than finishing at the old
               one. */
            run += 1
            runCatching { engine.stop() }
            val mine = run
            engine.setOnUtteranceProgressListener(listener(mine, context.applicationContext))
            advance(mine)
        } else {
            _state.value = current.copy(pace = want)
        }
    }

    private fun begin(mine: Int) {
        val engine = tts ?: return
        /* The voice is chosen from what the machine actually has,
           by core's rule: the exact tag, then the language
           whatever the region, then English for a language with
           no voice at all. */
        val available = runCatching {
            engine.availableLanguages.orEmpty().map { it.toLanguageTag() }
        }.getOrDefault(emptyList())
        val want = languageOf(lines.joinToString(" ") { it.text }.take(400))
        voiceFor(available, want)?.let { tag ->
            runCatching { engine.language = Locale.forLanguageTag(tag) }
        }
        advance(mine)
    }

    private fun advance(mine: Int) {
        if (mine != run) return
        val engine = tts ?: return
        val line = lines.getOrNull(index) ?: return
        _state.value = Speaking(
            on = true, at = index, block = line.block,
            total = lines.size, pace = pace,
        )
        engine.speak(line.text, TextToSpeech.QUEUE_ADD, Bundle(), "reiad-$mine-$index")
    }

    fun stop(context: Context) {
        hush()
        ReadAloudService.stop(context.applicationContext)
    }

    /** Everything `stop` does EXCEPT telling the service, so the
        service's own teardown can call it without asking to be
        torn down again. Same silence, no re-entry. */
    fun hush() {
        run += 1
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        lines = emptyList()
        index = 0
        _state.value = Speaking()
    }
}

/** What stops Android killing the voice when the app goes away.

    It holds no state of its own: `Reader` is the reader and this
    is only the thing keeping it alive. A service that also held
    the queue would be a second copy of where the reading has got
    to. */
class ReadAloudService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** Swiping the app away stops the reading. FULL STOP.

        A foreground service outliving its app is the correct
        answer for a music player, whose whole purpose is to keep
        going while you use the phone for something else. It is
        the wrong answer here, and it was reported as wrong: a
        lesson that carries on being read after the app is gone
        is a voice the reader has to hunt through the shade to
        silence. Backgrounding still keeps the voice, because
        that is a reader putting the phone in a pocket mid
        lesson; CLOSING it does not, because that is a reader
        finished with it.

        Paired with `android:stopWithTask` in the manifest,
        which is what the system honours when it can. This is
        what happens when it calls us instead. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        Reader.stop(this)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /** And if the service is torn down any other way, the voice
        goes with it rather than being left mid sentence with
        nothing able to reach it. */
    override fun onDestroy() {
        Reader.hush()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            Reader.stop(this)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(ID, notification())
        /* NOT sticky. A restarted service would have an empty
           queue and a notification claiming something is being
           read when nothing is, which is worse than the reading
           having stopped. */
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Reading aloud",
                    /* LOW: this is a control, not an alert. It
                       belongs in the shade without a sound. */
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ReadAloudService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_read)
            .setContentTitle(Reader.title.ifBlank { "Reading aloud" })
            .setContentText("Tap to come back, or stop below.")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            /* Visible on the lock screen, which is where a reader
               with the phone in a pocket will reach for it, and
               there is nothing private in a title they chose to
               open. */
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL = "read-aloud"
        private const val ID = 1
        private const val STOP = "uk.co.reiad.library.STOP_READING"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, ReadAloudService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ReadAloudService::class.java)) }
        }
    }
}
