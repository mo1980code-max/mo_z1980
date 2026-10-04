/**
 * Pure-Kotlin clock engine.
 *
 * Deliberately a `java-library` with **no Android dependency**: all clock geometry, tick cadence,
 * burn-in displacement, timer and stopwatch state live here, so they can be unit-tested on the JVM
 * in milliseconds and reused identically by the Compose UI and by the Canvas widget renderer.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
