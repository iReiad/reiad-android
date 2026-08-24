package uk.co.reiad.library.courses

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.shape.RoundedCornerShape
import uk.co.reiad.library.ui.Corner
import uk.co.reiad.library.core.SITE_ORIGIN
import uk.co.reiad.library.core.courseCaptionsPath
import uk.co.reiad.library.core.ticketPass

/* ============================================================
   The video, over a pass, from this site's own origin.

   ---- why the bytes come from here and not from Drive ----

   A private Drive file cannot be fetched by a client that is not
   signed into Drive, and no player on a phone is. The website hit
   the same wall from the other side: a `/preview` iframe of a
   private file answers "Unable to load video", because Drive sees
   an anonymous request for something that is not public. The
   mechanism only ever worked for files shared by link, and these
   deliberately are not.

   So the Worker holds the one credential and streams the bytes
   from `reiad.co.uk`. It forwards `Range` both ways, which is the
   whole of what makes a video scrubbable, and answers `HEAD`.
   That is exactly the contract ExoPlayer's default HTTP source
   wants, so there is no custom DataSource here and there should
   not be one.

   ---- the pass, and what happens when it runs out ----

   `<video src>` in a browser sends no `Authorization` header, and
   neither does a media player: it is a socket fetching a URL. So
   the permission travels IN the URL as a signed ticket that names
   one file and lasts half an hour.

   Half an hour is shorter than some lessons plus the time
   somebody leaves one paused, so it WILL expire mid-sitting. When
   it does the source fails, and this asks for a new pass and
   resumes at the same second rather than showing an error that a
   reader would answer by starting the lesson again.

   ---- and nothing here ever ticks a lesson ----

   ExoPlayer would happily report `STATE_ENDED`. Using it would
   still be guessing that somebody who left a video running has
   learnt something, so this player reports nothing to anything.
   A lesson is finished when the reader presses the button, which
   is the same rule the six schools live by.
   ============================================================ */

/** What the player is doing, as far as the screen needs to know. */
sealed interface Reel {
    data object Waiting : Reel
    data class Ready(val url: String, val captions: String?) : Reel
    data class Broke(val why: String) : Reel
}

/**
 * Mint the passes for one lesson's video.
 *
 * Two passes, not one, and not one reused: a ticket names ONE
 * file, which is the property that makes it safe to put in a URL,
 * so the captions need their own rather than a wider one.
 *
 * The captions' pass is asked for second and its failure is not
 * the video's: a lesson whose subtitles could not be minted
 * should play without subtitles, not refuse to play.
 */
suspend fun reelFor(
    source: LessonSource,
    video: String,
    captions: String?,
): Reel = when (val pass = source.ticket(video)) {
    is Answer.Got -> Reel.Ready(
        url = SITE_ORIGIN + pass.value.url,
        captions = captions?.let { drive ->
            /* The ticket route answers with the FILE address. The
               pass is lifted out of it and put on the captions
               path, because that route is where SubRip becomes
               WebVTT: same file, different thing done to it on
               the way through. */
            (source.ticket(drive) as? Answer.Got)?.value?.url
                ?.let(::ticketPass)
                ?.let { "$SITE_ORIGIN${courseCaptionsPath(drive)}?t=$it" }
        },
    )

    /* The server's own reason, never a general one. This endpoint
       answers 503 naming the secret that is not set, and "that
       video could not be opened, try reloading" instead of it is
       how the website's own player spent a week recommending a
       reload for something a reload could never fix. */
    else -> Reel.Broke(pass.problem ?: "That video could not be opened.")
}

/**
 * The player itself.
 *
 * 16:9 and not the video's own ratio, deliberately: the intrinsic
 * size is not known until the first frame is decoded, so laying
 * out to it means the whole page under the player jumps once the
 * video loads. A lesson video from this catalogue is 16:9.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun Reel(
    reel: Reel.Ready,
    /** Asked for a fresh pass when this one expires mid-sitting.
        Returns the new reel, or null when it could not be got,
        in which case the player keeps the error rather than
        looping on a mint that is not going to work. */
    renew: suspend () -> Reel.Ready?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(reel)

    /* Where the reader had got to, kept OUTSIDE the player so it
       survives the player being torn down and built again for a
       renewed pass. Without this a ticket expiring at minute
       twenty-nine puts somebody back at the beginning, which is
       worse than the error it replaced. */
    var at by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var url by remember(reel.url) { mutableStateOf(reel.url) }
    var captions by remember(reel.url) { mutableStateOf(reel.captions) }
    var expired by remember { mutableStateOf(false) }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            /* Metadata rather than the whole file: a lesson video
               is tens of megabytes and a reader who opened the
               page for the transcript should not pay for all of
               it. */
            playWhenReady = false
        }
    }

    LaunchedEffect(url, captions) {
        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                captions?.let { track ->
                    setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(track))
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage("en")
                                .setLabel("English")
                                /* On by default, because this
                                   section has one reader and they
                                   asked for captions. The way to
                                   turn them off is in the player,
                                   where somebody would look. */
                                .setSelectionFlags(
                                    androidx.media3.common.C.SELECTION_FLAG_DEFAULT,
                                )
                                .build(),
                        ),
                    )
                }
            }
            .build()

        player.setMediaItem(item, at)
        player.prepare()
        player.playWhenReady = playing
    }

    /* Awake while it plays and not a second longer. The player
       view does not do this for itself, and a lesson video that
       lets the screen time out at minute four is a lesson nobody
       watches to the end. */
    val view = androidx.compose.ui.platform.LocalView.current

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                /* Remember where they were before anything else,
                   because the position is gone the moment the
                   player is re-prepared. */
                at = player.currentPosition.coerceAtLeast(0L)
                playing = true
                expired = true
            }

            /* Told rather than polled. A one-second loop asking
               the same question is a wake-up per second for an
               answer that changes twice a sitting. */
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                view.keepScreenOn = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            /* Never left on. The flag is the WINDOW's, so a
               player torn down mid-play would otherwise hold the
               screen awake on whatever page came next. */
            view.keepScreenOn = false
        }
    }

    /* One renewal per failure, not a loop. A pass that will not
       mint is a permission problem or a Drive problem, and asking
       again every few seconds would hammer the endpoint while
       showing the reader nothing. */
    LaunchedEffect(expired) {
        if (!expired) return@LaunchedEffect
        expired = false
        val fresh = renew() ?: return@LaunchedEffect
        url = fresh.url
        captions = fresh.captions ?: latest.captions
    }

    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Corner.card))) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    /* Whatever was on screen stays there while
                       the player is torn down and rebuilt for a
                       renewed pass, so a ticket expiring does not
                       flash a black rectangle at the reader. */
                    setKeepContentOnPlayerReset(true)
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
