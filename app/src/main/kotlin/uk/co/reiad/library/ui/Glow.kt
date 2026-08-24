package uk.co.reiad.library.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import uk.co.reiad.library.core.Motion
import uk.co.reiad.library.core.Tilt
import uk.co.reiad.library.core.leanFor
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/* ============================================================
   The light comes up in 190ms and goes out in 820ms.

   A filament does not switch off, and the asymmetry is most of
   what makes a surface read as a material rather than as a press
   state. One animation does both, because it reads its duration
   from the state it is going TO.

   ---- what a finger is, that a pointer is not ----

   The site's light follows a pointer across a surface, and a
   pointer is present without being pressed. A handset has no such
   thing: the light comes up where the finger goes DOWN, follows
   it while it moves, and lingers out from wherever it was lifted.
   That is the same material answering a different input, not a
   smaller version of it.

   It watches on the INITIAL pass and consumes nothing, so a card
   that is also a link still opens.
   ============================================================ */

/** How lit one surface is, and where the light is on it. */
@Stable
class Glow internal constructor(private val instant: Boolean) {
    private val amount = Animatable(0f)
    internal var nx by mutableFloatStateOf(0f)
    internal var ny by mutableFloatStateOf(0f)

    /** Read this inside a draw block, never in composition: it
        changes every frame while the light is moving, and a
        composition that reads it recomposes every frame with it. */
    val lit: Lit get() = Lit(amount.value, nx, ny)

    internal suspend fun light() {
        if (instant) amount.snapTo(1f)
        else amount.animateTo(1f, tween(Motion.IN_MS))
    }

    internal suspend fun fade() {
        if (instant) amount.snapTo(0f)
        else amount.animateTo(0f, tween(Motion.OUT_MS))
    }
}

/** Somebody who has told their operating system to turn
    animation off has told this site something real, and the site
    is mostly long-form prose. The light still arrives, it just
    arrives at once. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Whether this composition has no clock: a Paparazzi render, a
    preview. One frame is all there will ever be, so anything that
    ARRIVES (a sheet rising, a body settling) has to be drawn
    already arrived or the picture is of the moment before it.

    A local the render tests SET, because there is nothing
    reliable to sniff: Paparazzi installs neither
    `LocalInspectionMode` nor an edit-mode view, which was
    measured the direct way: the settings sheet recorded as an
    empty page, at 1.8KB, twice, under both detections. A test
    that states its own condition beats a heuristic that lies
    quietly. */
val LocalStill = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun rememberStill(): Boolean {
    val inspecting = androidx.compose.ui.platform.LocalInspectionMode.current
    return LocalStill.current || inspecting
}

@Composable
fun rememberGlow(): Glow {
    val reduced = rememberReducedMotion()
    return remember(reduced) { Glow(instant = reduced) }
}

/** Installs the light on a surface. Pair it with the same `Glow`
    handed to `material(lit = { glow.lit })`. */
fun Modifier.follows(glow: Glow): Modifier = pointerInput(glow) {
    /* `awaitPointerEventScope` is a RESTRICTED suspending scope:
       nothing can be launched from inside it. So the animation
       runs in the outer scope and the loop only feeds it. */
    coroutineScope {
        val outer = this
        var down = false
        awaitPointerEventScope {
            while (true) {
                /* INITIAL, so this observes rather than competes:
                   a card that is also a link, a chip that latches
                   and a row that navigates all still do their own
                   job, because nothing here consumes. */
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val touch = event.changes.firstOrNull() ?: continue
                val width = size.width.toFloat().coerceAtLeast(1f)
                val height = size.height.toFloat().coerceAtLeast(1f)
                glow.nx = ((touch.position.x / width) * 2f - 1f).coerceIn(-1f, 1f)
                glow.ny = ((touch.position.y / height) * 2f - 1f).coerceIn(-1f, 1f)

                /* Only on the CHANGE. Starting an animation on
                   every move event restarts the 190ms curve sixty
                   times a second, which is a light that never
                   finishes arriving. */
                val pressed = event.changes.any { it.pressed }
                if (pressed != down) {
                    down = pressed
                    outer.launch { if (pressed) glow.light() else glow.fade() }
                }
            }
        }
    }
}

/* ============================================================
   And the lean, which is the handset's own movement.

   1.4 degrees over 26 degrees of handset travel: half the angle a
   pointer gets, and for a reason. A pointer tilt answers a
   deliberate movement over one card. This answers the ordinary
   sway of holding a phone, and the same angle that reads as a
   lean under a cursor reads as a wobble in the hand.

   Three things guard it, all of them the site's:

   1. Reduced motion turns it off entirely.
   2. Where the handset was at the first reading IS level.
      Somebody reading in bed holds a phone at sixty degrees and
      is not tilting it.
   3. No reading within three seconds and the listener comes off.
      A tablet with no gyroscope, a device that refuses, an
      emulator: the sensor is unregistered rather than left
      waiting for an event that will never arrive.

   And one that is this platform's rather than the site's: the
   sensor fires at up to 50Hz and a state write per event is an
   invalidation per event. The reading is stored in a plain field
   and snapped to state once per frame, which is what the site's
   own module does with a requestAnimationFrame.
   ============================================================ */

/** The handset's sway, as two numbers from -1 to 1. */
@Stable
class Sway internal constructor() {
    internal var nx by mutableFloatStateOf(0f)
    internal var ny by mutableFloatStateOf(0f)
    val x: Float get() = nx
    val y: Float get() = ny
}

private val Still = Sway()

@Composable
fun rememberSway(): Sway {
    val context = LocalContext.current
    val reduced = rememberReducedMotion()
    if (reduced) return Still

    val sway = remember { Sway() }
    val sensors = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    /* Not filled from the state above, so a sensor firing at 50Hz
       costs no invalidation until a frame asks for it. */
    val latest = remember { FloatArray(2) }
    val rest = remember { FloatArray(2) }
    val heard = remember { booleanArrayOf(false) }

    DisposableEffect(sensors) {
        val manager = sensors
        /* The GAME rotation vector rather than the plain one: it
           leaves the magnetometer out, so a lean does not drift
           when somebody reads next to a speaker or a laptop. What
           is wanted here is relative movement, and absolute north
           is exactly the part that misbehaves indoors. */
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                // angles: azimuth, pitch, roll. Pitch is the site's
                // beta (front to back) and roll is its gamma.
                val beta = Math.toDegrees(angles[1].toDouble()).toFloat()
                val gamma = Math.toDegrees(angles[2].toDouble()).toFloat()
                if (!heard[0]) {
                    heard[0] = true
                    rest[0] = gamma
                    rest[1] = beta
                }
                latest[0] = Tilt.normalise(gamma.toDouble(), rest[0].toDouble()).toFloat()
                latest[1] = Tilt.normalise(beta.toDouble(), rest[1].toDouble()).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }

    LaunchedEffect(sway) {
        val startedAt = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            if (!heard[0]) {
                /* Nothing has answered. A tablet with no
                   gyroscope, a device that refuses, an emulator:
                   stop asking rather than waking on every frame
                   for the life of the screen. */
                if ((now - startedAt) / 1_000_000 > Tilt.GIVE_UP_MS) return@LaunchedEffect
                continue
            }
            /* Only when it has actually MOVED. A state write per
               frame is a redraw of every leaning surface per
               frame, and a phone lying still on a table would
               have paid for that for as long as the screen was
               on. The threshold is a fiftieth of the full lean,
               which is three hundredths of a degree: below what
               anybody can see and above what a sensor's own noise
               produces. */
            if (abs(latest[0] - sway.nx) > STILL || abs(latest[1] - sway.ny) > STILL) {
                sway.nx = latest[0]
                sway.ny = latest[1]
            }
        }
    }
    return sway
}

/** A surface leaning towards where the handset is pointed.

    The perspective is the site's own 1100, because a lean with no
    perspective is an affine squash rather than a rotation, and
    two cards in a row leaning under different perspectives read
    as two separate scenes. */
fun Modifier.leaning(sway: Sway, degrees: Double = Tilt.HANDSET_DEGREES): Modifier =
    graphicsLayer {
        val lean = leanFor(sway.nx.toDouble(), sway.ny.toDouble(), degrees) ?: return@graphicsLayer
        cameraDistance = PERSPECTIVE
        rotationX = (lean.x * lean.degrees).toFloat()
        rotationY = (lean.y * lean.degrees).toFloat()
    }

private const val PERSPECTIVE = 1100f

/** Movement below this is the sensor talking to itself. */
private const val STILL = 0.02f
