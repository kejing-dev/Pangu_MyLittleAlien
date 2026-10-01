import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version from the Gradle properties panguVersionCode / panguVersionName (gradle.properties, or -P on
// the command line). Same rules as VersionProps in :game-logic: missing takes the default, malformed
// fails the build.
val panguVersionCode: Int = (findProperty("panguVersionCode") as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let {
    val n = it.toIntOrNull()
    require(n != null && n in 1..2_100_000_000) { "panguVersionCode must be a whole number from 1 to 2100000000, got \"$it\"" }
    n
} ?: 1
val panguVersionName: String = (findProperty("panguVersionName") as String?)?.trim()?.takeIf { it.isNotEmpty() }?.also {
    require(Regex("""\d+(\.\d+){0,3}([-+][0-9A-Za-z.-]+)?""").matches(it)) { "panguVersionName must look like 1.2.3 (optionally -suffix), got \"$it\"" }
} ?: "1.0.0"

// Release signing from keystore.properties (gitignored) when it exists; otherwise release builds
// are unsigned.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

android {
    namespace = "com.technomancer.pangu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.technomancer.pangu"
        minSdk = 35
        targetSdk = 35
        versionCode = panguVersionCode
        versionName = panguVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Not debuggable, so debug_config.json, the Emulator assist and the performance probe
            // stay off (the probe is replaced by the no-op in src/release).
            isDebuggable = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Procedural sound clips rendered by :game-logic:renderAudio.
    sourceSets["main"].assets.srcDir("build/generated/pangu/assets")

    androidResources {
        noCompress += listOf(".bundle", ".glb")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.spatial.bom))
    implementation(libs.core)
    implementation(libs.platform)
    implementation(libs.foundation)
    implementation(libs.design)
    implementation(libs.sense)
    implementation(libs.tracking)
    implementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(project(":editor-asset"))
    implementation(project(":game-logic"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}

tasks.named("preBuild") {
    dependsOn(":game-logic:renderAudio")
}
