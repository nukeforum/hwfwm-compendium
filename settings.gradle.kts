
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Compendium"
include(":ability-preview")
include(":app")
include(":dataloader")
include(":design")
include(":essence-details")
include(":essence-search")
include(":essences")
include(":model-core")
include(":persistence")
include(":randomizer")
