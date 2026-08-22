import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

/* Built on whatever JDK is to hand and emitting 17, because the
   Android module consumes this and Android does not take 21
   bytecode. Deliberately not a toolchain: pinning one makes the
   build require a JDK nobody has installed, which is a failure
   with a confusing message. */
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.test { useJUnitPlatform() }
