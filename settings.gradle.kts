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
        // piper-phonemize (giọng đọc Piper) phát hành qua JitPack.
        maven("https://jitpack.io")
    }
}
rootProject.name = "vietnote-asr-android"
include(":app")
