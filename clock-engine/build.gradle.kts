/**
 * Pure-Kotlin clock engine.
 *
 * Deliberately a `java-library` with **no Android dependency**: all clock geometry, tick cadence,
 * burn-in displacement, timer and stopwatch state live here, so they can be unit-tested on the JVM
 * in milliseconds and reused identically by the Compose UI and by the Canvas widget renderer.
 * The ad-eligibility policy ([com.digitalclockpro.clockengine.AdPolicy]) lives here for the same
 * reason: ad allow/deny rules are product logic that must survive a JVM test run, not live
 * untested inside the Android layer.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Deliberately NOT jvmToolchain(17). settings.gradle.kts has no foojay toolchain
    // resolver, so if Gradle runs on anything other than a JDK 17 it fails with
    // "No matching toolchains found" before compiling a line. Pinning jvmTarget instead
    // emits 17 bytecode from whichever JDK Studio happens to launch Gradle with.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
