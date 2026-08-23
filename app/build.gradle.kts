import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "uk.co.reiad.library"
    compileSdk = 35

    defaultConfig {
        applicationId = "uk.co.reiad.library"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    /* ============================================================
       ONE debug key, committed, so every build is the same app.

       Android generates a debug keystore per machine on first
       use. Two builds of this app from two machines are therefore
       signed by different keys, which Android reads as two
       different apps: the new APK refuses to install over the old
       one and the reader has to uninstall first, losing every
       tick that has not synced.

       It also breaks app links. `/.well-known/assetlinks.json`
       names ONE fingerprint, so links can only verify against one
       key, and a per-machine key means the shared lesson opens in
       a browser however carefully the intent filters were
       written.

       ---- and this is not a secret ----

       A debug keystore is designed to be shared: the password is
       `android`, the alias is `androiddebugkey`, and every one
       Android has ever generated uses both. It cannot sign a Play
       release and it grants nothing. Committing it is the same
       decision as committing a fixture.

       A release key, when there is one, does NOT go here: that
       one is a credential, it lives in CI as a secret, and the
       day it exists this block gains a `release` signing config
       that reads the environment.
       ============================================================ */
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            /* And the resources, which is the other half: R8
               shrinks code and leaves every drawable, string and
               layout the code no longer reaches. It is off by
               default because it is unsafe with reflection, and
               nothing here reaches a resource by name. */
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        debug {
            /* The debug build is what is INSTALLED, since there
               is no store listing yet, so it is worth it not
               being needlessly slower than the release one. What
               it keeps is the debuggable flag and the faster
               build; what it does not need is a second package
               name, which would make the two builds two apps and
               undo the whole point of the shared key. */
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    /* Robolectric needs the app's real resources: the faces, the
       colours and the strings are all resources, and a semantics
       tree built without them is a tree of blanks. */
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

/* Unit tests are the DEBUG variant's, and the release unit-test
   task is switched off rather than left to fail.

   `createComposeRule()` starts a `ComponentActivity`, registered
   by `compose.ui.test.manifest`, and that is a
   `debugImplementation` on purpose: a test activity in the
   release manifest is a test activity in the shipped APK. So
   every Robolectric test in this module fails under
   `testReleaseUnitTest` with "Unable to resolve activity", which
   is sixteen red tests for a reason that has nothing to do with
   whatever was being changed.

   CI runs `:app:testDebugUnitTest`. This makes the aggregate
   `./gradlew test` mean the same thing, rather than being a
   command nobody can run. */
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

/* The fixtures are an INPUT to these tests, and Gradle cannot
   work that out on its own.

   They live in `core`'s test resources and this module reads them
   by relative path, which is deliberate: they are one set of real
   API answers and copying them would be two. But nothing on this
   module's classpath changes when one is refreshed, so Gradle
   calls the test task up to date and the renders come back
   identical to the ones recorded against the old data.

   That is not a theory. Twenty-eight doubled percent signs were
   fixed on the site, the fixture was regenerated, the screens
   were re-recorded, and every one of them still showed
   `২৩৩.১%%`, because the task never ran. */
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("core/src/test/resources/fixtures"))
        .withPropertyName("apiFixtures")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work)
    implementation(libs.androidx.core.ktx)

    /* Applies `baseline-prof.txt` at install rather than
       leaving the first run to the interpreter. Without it
       the file in `src/main/` is packaged and ignored. */
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)

    /* Paparazzi renders a screen on the JVM, so `recordPaparazzi`
       is the only way anything here can LOOK at the app. It is
       not a golden-image gate: see `app/src/test/.../Looks.kt`. */
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)

    /* Robolectric runs the real Compose runtime on the JVM, which
       is what makes an accessibility check a CHECK: the semantics
       tree it walks is the one TalkBack would read, rather than a
       guess made from the source. */
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
