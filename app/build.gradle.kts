plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * The app version comes from git tags: RELEASE_VERSION (set by the release workflow from a tag
 * like v1.2.0), else the latest tag in this checkout, else 0.0.0-dev.
 */
fun appVersion(): String {
    System.getenv("RELEASE_VERSION")?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() }?.let { return it }
    val tag = runCatching {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    }.getOrNull()
    return tag?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: "0.0.0-dev"
}

/**
 * major*10000 + minor*100 + patch. A pre-release (1.3.0-beta.2) gets the release's code minus
 * (100 - 2), so every beta installs as older than the final 1.3.0.
 */
fun versionCodeOf(version: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?""").find(version) ?: return 1
    val (major, minor, patch, pre) = match.destructured
    val code = major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    if (pre.isEmpty()) return code
    val n = Regex("""(\d+)$""").find(pre)?.value?.toIntOrNull()?.coerceIn(0, 99) ?: 0
    return (code - (100 - n)).coerceAtLeast(1)
}

/** A signing setting from the environment (CI) or ~/.gradle/gradle.properties (your machine). */
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }

val releaseVersion = appVersion()
val keystorePath = signingValue("NYUMBANI_KEYSTORE_FILE")
val hasReleaseKey = keystorePath != null && listOf("NYUMBANI_KEYSTORE_PASSWORD", "NYUMBANI_KEY_ALIAS", "NYUMBANI_KEY_PASSWORD")
    .all { signingValue(it) != null }

android {
    namespace = "dev.kharioki.nyumbani"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kharioki.nyumbani"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeOf(releaseVersion)
        versionName = releaseVersion

        // Pre-fills the original owner's name/greeting/city on first run. Off for public builds;
        // turn on with -PnyumbaniSeedOwner=true or in ~/.gradle/gradle.properties.
        val seedOwner = providers.gradleProperty("nyumbaniSeedOwner").orNull.toBoolean()
        buildConfigField("boolean", "DEBUG_SEED_OWNER", seedOwner.toString())
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = signingValue("NYUMBANI_KEYSTORE_PASSWORD")
                keyAlias = signingValue("NYUMBANI_KEY_ALIAS")
                keyPassword = signingValue("NYUMBANI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Minified release build runs much smoother on TV hardware than a debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without the release key (local builds, pull requests) fall back to the debug key, so
            // the APK still installs. It can't update a release-signed install, or vice versa.
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("Nyumbani: NYUMBANI_KEYSTORE_* not set; the release APK is signed with the debug key.")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.tv:tv-material:1.0.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.zxing:core:3.5.3")
}
