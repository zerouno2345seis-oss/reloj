pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QuranWatch8"
include(":app")
// Every watch face module: the four designed ones and each face made in the
// face editor (scripts/wff/make_custom_face.py adds a watchface-c-<id> folder).
rootDir.listFiles { f -> f.isDirectory && f.name.startsWith("watchface") && java.io.File(f, "build.gradle.kts").exists() }
    ?.sortedBy { it.name }
    ?.forEach { include(":${it.name}") }
