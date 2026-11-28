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
        maven("https://jitpack.io")
    }
}

rootProject.name = "Photon"
include(":app")
// Legacy baseline-profile journeys target the previous app and are not part of this replacement.

// Original Photon, maintained independently of the supplied Firebase application.
include(":old-photon")
