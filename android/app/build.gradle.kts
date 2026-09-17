import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
}

// Shared OAuth credentials are injected locally/through CI, never checked into source control.
// They are bundled in the APK and must not be treated as confidential once distributed.
val oauthProperties =
    Properties().apply {
        val configFile = rootProject.file("oauth.properties")
        if (configFile.exists()) configFile.inputStream().use { load(it) }
    }

fun oauthField(name: String, fallback: String? = null): String {
    val value =
        (providers.environmentVariable(name).orNull
                ?: oauthProperties.getProperty(name)
                ?: fallback)
            ?.trim()
            .orEmpty()
    if (value.isBlank() || value.any { it.isISOControl() }) {
        throw GradleException(
            "Configure $name in android/oauth.properties or the build environment. See android/README.md."
        )
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.anirust.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anirust.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SHIKIMORI_CLIENT_ID",
            oauthField("SHIKIMORI_CLIENT_ID", "683qMX6Xae_qJNo41rBB-mvjlsN5xaCR5VhK1XFOGJg"),
        )
        buildConfigField("String", "SHIKIMORI_CLIENT_SECRET", oauthField("SHIKIMORI_CLIENT_SECRET"))
        buildConfigField(
            "String",
            "SHIKIMORI_APP_NAME",
            oauthField("SHIKIMORI_APP_NAME", "AniRust"),
        )
    }

    // Preserve upgrade compatibility with locally built APKs, while allowing
    // fresh checkouts to use the standard generated debug key.
    val localDebugKeystore = rootProject.file("debug.keystore")
    if (localDebugKeystore.exists()) {
        signingConfigs.getByName("debug").storeFile = localDebugKeystore
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Installable optimized build for device testing; same local key as debug, not a
        // distribution key.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Room Local Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    // Networking & Serialization
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)

    // Image Loading
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
