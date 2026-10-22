// Our own watch face (mihrab style), in Watch Face Format: a code-free package (since
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
    namespace = "com.quran.watch8.watchface.mihrab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.quran.watch8.watchface.mihrab"
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
