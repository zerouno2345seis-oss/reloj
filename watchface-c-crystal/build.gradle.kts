// Our own watch face (zaher's own, from the face editor), in Watch Face Format: a code-free package (since
// January 2026 the only kind Wear OS installs). It draws the face and shows
// the app's complications, which carry the data and the taps into the app.
plugins {
    id("com.android.application")
}

// The face's own version: 1000 + the commits that touched this folder, so a
// release that leaves the face alone keeps its number and the watch's update
// button skips it. (1000 keeps it above the old whole-repo counts, ~200.)
val gitCommitCount: Int = 1000 + (providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD", "--", projectDir.absolutePath)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().toIntOrNull() ?: 1)

android {
    namespace = "com.quran.watch8.watchface.c.crystal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quran.watch8.watchface.c.crystal"
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
    commandLine(rootProject.file("scripts/validate-watchface.sh").path, file("src/main/res/raw/watchface.xml").path)
    inputs.file("src/main/res/raw/watchface.xml")
    outputs.upToDateWhen { false }
}
tasks.named("preBuild") { dependsOn(validateWatchFace) }
