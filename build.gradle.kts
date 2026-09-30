// Root build file: declares plugins without applying them.
plugins {
    // AGP 9 ships built-in Kotlin support for Android modules, so
    // org.jetbrains.kotlin.android must NOT be applied.
    alias(libs.plugins.android.application) apply false
    // :core is a pure JVM module (the game engine), compiled with the same
    // Kotlin version as the compiler embedded in AGP.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
