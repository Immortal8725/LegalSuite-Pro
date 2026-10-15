import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Release signing. Values come from android/key.properties (never committed) or the environment:
// OWLAW_KEYSTORE_PATH, OWLAW_KEYSTORE_PASSWORD, OWLAW_KEY_ALIAS, OWLAW_KEY_PASSWORD. See mobile/README.md.
val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    (keyProps.getProperty(prop) ?: System.getenv(env))?.takeIf { it.isNotBlank() }
val releaseStore = signingValue("storeFile", "OWLAW_KEYSTORE_PATH")
val releaseSigningReady = releaseStore != null &&
    signingValue("storePassword", "OWLAW_KEYSTORE_PASSWORD") != null &&
    signingValue("keyAlias", "OWLAW_KEY_ALIAS") != null

// Flutter passes --dart-define values to Gradle as base64 in "dart-defines".
val dartDefines: Map<String, String> = (project.findProperty("dart-defines") as String? ?: "")
    .split(",").filter { it.isNotBlank() }
    .mapNotNull { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }
    .mapNotNull { d -> d.indexOf('=').takeIf { it > 0 }?.let { d.substring(0, it) to d.substring(it + 1) } }
    .toMap()

android {
    namespace = "za.co.owlaw.owlaw_mobile"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "za.co.owlaw.owlaw_mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(releaseStore!!)
                storePassword = signingValue("storePassword", "OWLAW_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "OWLAW_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "OWLAW_KEY_PASSWORD")
                    ?: signingValue("storePassword", "OWLAW_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

flutter {
    source = "../.."
}

// Stop a release build that would ship unsigned, debug-signed or pointed at a plain-HTTP API.
gradle.taskGraph.whenReady {
    val releaseTask = allTasks.any { it.project == project && it.name.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package")) }
    if (!releaseTask) return@whenReady
    if (!releaseSigningReady) {
        throw GradleException("Release signing is not set up. Create android/key.properties or set OWLAW_KEYSTORE_PATH, OWLAW_KEYSTORE_PASSWORD, OWLAW_KEY_ALIAS and OWLAW_KEY_PASSWORD (see mobile/README.md).")
    }
    val api = dartDefines["API_BASE_URL"]
    // No API_BASE_URL means the app's release default, https://owlaw-api.onrender.com.
    if (!api.isNullOrBlank() && !api.startsWith("https://")) {
        throw GradleException("API_BASE_URL for a release build must start with https:// (got $api).")
    }
}
