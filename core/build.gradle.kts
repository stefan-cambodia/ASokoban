// :core — the Sokoban engine, level format, level pack and solver.
//
// Pure Kotlin/JVM module with NO Android dependency: every engine test runs on
// the development machine's JVM in seconds.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Level design tools (see LevelWorkbench): enabled only when these are passed.
val workbenchProperties = listOf("sokoban.report", "sokoban.generate", "sokoban.scramble")
    .associateWith { providers.gradleProperty(it).orNull }
    .filterValues { it != null }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxHeapSize = "4g"
    workbenchProperties.forEach { (name, value) -> systemProperty(name, value!!) }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
