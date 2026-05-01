
pluginManagement {
    includeBuild("composite")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Compendium"
include(":ability-listing-contributions")
include(":ability-listing-details")
include(":ability-listing-search")
include(":ability-preview")
include(":app")
include(":awakening-stone-contributions")
include(":awakening-stone-details")
include(":awakening-stone-search")
include(":dataloader")
include(":design")
include(":essence-contributions")
include(":essence-details")
include(":essence-search")
include(":essences")
include(":model-core")
include(":persistence")
include(":randomizer")
include(":wire")
include(":wire-annotations")
include(":wire-ksp")
