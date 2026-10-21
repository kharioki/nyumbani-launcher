plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.kharioki.nyumbani"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.kharioki.nyumbani"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Pre-fills the original owner's name/greeting/city on first run. Off for public builds;
        // turn on with -PnyumbaniSeedOwner=true or in ~/.gradle/gradle.properties.
        val seedOwner = providers.gradleProperty("nyumbaniSeedOwner").orNull.toBoolean()
        buildConfigField("boolean", "DEBUG_SEED_OWNER", seedOwner.toString())
    }

    buildTypes {
        release {
            // Minified release build runs much smoother on TV hardware than a debug build.
            // Signed with the debug key so it can be sideloaded without setting up a keystore.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
