import java.util.Properties

plugins {
    // AGP 9 has built-in Kotlin support: no org.jetbrains.kotlin.android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The release key comes from keystore.properties at the project root (kept out
// of git) or, on a build server, from SOKOBAN_* environment variables. Without
// either, release builds are signed with the debug key: enough to test R8 and
// resource shrinking, and refused by Google Play.
val releaseKey: Map<String, String>? = run {
    val names = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val file = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
    val values = if (file != null) {
        val properties = Properties().apply { load(file.reader()) }
        names.mapNotNull { name -> properties.getProperty(name)?.let { name to it } }.toMap()
    } else {
        val variables = listOf("SOKOBAN_KEYSTORE", "SOKOBAN_KEYSTORE_PASSWORD", "SOKOBAN_KEY_ALIAS", "SOKOBAN_KEY_PASSWORD")
        names.zip(variables).mapNotNull { (name, variable) -> providers.environmentVariable(variable).orNull?.let { name to it } }.toMap()
    }
    when {
        values.isEmpty() -> null
        values.size < names.size -> error("Release signing is incomplete, missing: ${(names - values.keys).joinToString()}")
        else -> values
    }
}

android {
    namespace = "dev.stefan.sokoban"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "dev.stefan.sokoban"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = rootProject.file(releaseKey.getValue("storeFile"))
                storePassword = releaseKey.getValue("storePassword")
                keyAlias = releaseKey.getValue("keyAlias")
                keyPassword = releaseKey.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // Android framework stubs (Log, Bundle) answer with defaults in JVM tests.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // The board and the D-pad are drawn by hand; lint cannot see their
        // semantics, which are set explicitly in code.
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlin.stdlib) {
        version { strictly(libs.versions.kotlin.get()) }
    }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}
