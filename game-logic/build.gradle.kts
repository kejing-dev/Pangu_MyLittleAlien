import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin game logic: no Android or PICO imports, host-testable with JUnit 5.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Bytecode target 11 to match :app. No jvmToolchain(11): the build runs on Android Studio's
// bundled JDK 21 and there is no toolchain resolver configured to fetch a JDK 11.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // api: :app inlines ConfigJson.load<T>(), which references kotlinx-serialization types.
    api(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.jupiter.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Renders the procedural sound clips into the app's generated assets (not committed).
val renderAudio by tasks.registering(JavaExec::class) {
    group = "pangu"
    description = "Renders procedural audio clips to WAV files for the app."
    val outDir = rootProject.layout.projectDirectory.dir("app/build/generated/pangu/assets/audio")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.technomancer.pangu.logic.audio.RenderAudioMain")
    args(outDir.asFile.absolutePath)
    inputs.files(sourceSets["main"].runtimeClasspath)
    outputs.dir(outDir)
}
