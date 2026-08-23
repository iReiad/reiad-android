package uk.co.reiad.library

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import kotlinx.serialization.json.Json
import java.io.File

/* ============================================================
   A way to LOOK at the app.

   The app is drawn rather than described, and for eleven blocks
   nothing in this repository could see it. Every check asserted
   arithmetic, keys and structure, all of which passed while the
   stock check opened on an empty box and the home page was a
   title, a line of grey and a stack of cards.

   Paparazzi renders a composable on the JVM with no emulator and
   no device, which makes a screen something a check can look at.
   `./gradlew :app:recordPaparazziDebug` writes every screen in
   this directory to `app/src/test/snapshots/images/`.

   ---- what these are NOT ----

   They are not golden-image tests and `verifyPaparazzi` is not in
   `check-all`. A design that cannot be changed without a hundred
   image diffs is a design nobody changes. These exist so that a
   person, or a model, can SEE what a change did.
   ============================================================ */

/** A handset, the size the site's own screenshots were taken at. */
val HANDSET: DeviceConfig = DeviceConfig.PIXEL_5.copy(
    screenWidth = 412 * 3,
    screenHeight = 915 * 3,
    nightMode = NightMode.NOTNIGHT,
)

val DARK: DeviceConfig = HANDSET.copy(nightMode = NightMode.NIGHT)

/** The same handset, three screens tall.

    Paparazzi renders one frame, so a screen longer than 915dp is
    a screen whose picture only ever shows its head. The account
    is nine sections and a form; snapshotting it at handset height
    was a picture of the sign-in panel, over and over, while
    everything below it could rot quietly.

    The WIDTH is unchanged, which is the point: every line breaks
    where it breaks on a real phone. Only the frame is longer. */
val TALL: DeviceConfig = HANDSET.copy(screenHeight = 915 * 3 * 3)

fun paparazzi(device: DeviceConfig = HANDSET): Paparazzi = Paparazzi(
    deviceConfig = device,
    /* The real faces are in `app/src/main/res/font`, so the render
       has to see the app's resources rather than a stub set. */
    theme = "android:Theme.Material.Light.NoActionBar",
    showSystemUi = false,
    maxPercentDifference = 0.0,
)

internal val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

/** The same fixtures `core` asserts against, so a screen is drawn
    with the site's real words rather than with lorem. A drawing
    made from invented content is a drawing that cannot show you
    that a real title is three lines long. */
fun <T> fixture(name: String, serializer: kotlinx.serialization.DeserializationStrategy<T>): T {
    val file = File("../core/src/test/resources/fixtures/$name")
    return json.decodeFromString(serializer, file.readText())
}

/** One field of a fixture, for an endpoint that wraps what a
    screen needs inside a bigger answer. */
fun <T> fixtureField(
    name: String,
    field: String,
    serializer: kotlinx.serialization.DeserializationStrategy<T>,
): T {
    val file = File("../core/src/test/resources/fixtures/$name")
    val root = json.parseToJsonElement(file.readText())
    val inner = root.let { it as kotlinx.serialization.json.JsonObject }[field]
        ?: error("$name has no $field")
    return json.decodeFromJsonElement(serializer, inner)
}
