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
