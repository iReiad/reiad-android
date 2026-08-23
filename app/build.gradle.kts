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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
}
