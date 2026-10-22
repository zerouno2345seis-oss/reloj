// Our own watch face, in Watch Face Format: a code-free package (since
// January 2026 the only kind Wear OS installs). It draws the face and shows
// the app's complications, which carry the data and the taps into the app.
plugins {
    id("com.android.application")
}

val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1

android {
    namespace = "com.quran.watch8.watchface"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quran.watch8.watchface"
        minSdk = 33
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// No build of a face that fails the official schema: an invalid face once
// sent the watch into a reboot loop.
val validateWatchFace by tasks.registering(Exec::class) {
    commandLine(rootProject.file("scripts/validate-watchface.sh").path)
    inputs.file("src/main/res/raw/watchface.xml")
    outputs.upToDateWhen { false }
}
tasks.named("preBuild") { dependsOn(validateWatchFace) }
