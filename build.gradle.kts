// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "9.2.0" apply false
    // org.jetbrains.kotlin.android is no longer applied (or needed) since AGP 9.0 — Kotlin
    // support is built into com.android.application now. compose/serialization are separate
    // Kotlin compiler plugins, unaffected by that change, and still applied explicitly below.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
