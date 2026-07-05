package wizardry.compendium

import android.net.Uri
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.domain.model.StatusEffect

sealed class Nav(val route: String) {
    object Landing : Nav("landing")
    object EssenceSearch : Nav("essenceSearch")
    object AwakeningStoneSearch : Nav("stoneSearch")
    object AbilitySearch : Nav("abilityListingSearch")
    object EssenceRandomizer : Nav("essenceRandomizer")
    object Settings : Nav("settings")
    object About : Nav("about")
    object Conflicts : Nav("conflicts")
    object EssenceContributions : Nav("essenceContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "essenceContributions"
        fun buildEditRoute(essence: Essence) = buildEditRouteByName(essence.name)
        fun buildEditRouteByName(name: String) = "essenceContributions?name=${Uri.encode(name)}"
    }
    object AwakeningStoneContributions : Nav("stoneContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "stoneContributions"
        fun buildEditRoute(stone: AwakeningStone) = buildEditRouteByName(stone.name)
        fun buildEditRouteByName(name: String) = "stoneContributions?name=${Uri.encode(name)}"
    }
    object AbilityContributions : Nav("abilityListingContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "abilityListingContributions"
        fun buildEditRoute(listing: Ability.Listing) = buildEditRouteByName(listing.name)
        fun buildEditRouteByName(name: String) = "abilityListingContributions?name=${Uri.encode(name)}"
    }
    object EssenceDetail : Nav("essenceDetail/{essenceName}") {
        const val ARG_NAME = "essenceName"
        fun buildRoute(essence: Essence) = "essenceDetail/${Uri.encode(essence.name)}"
    }
    object AwakeningStoneDetail : Nav("stoneDetail/{stoneName}") {
        const val ARG_NAME = "stoneName"
        fun buildRoute(stone: AwakeningStone) = "stoneDetail/${Uri.encode(stone.name)}"
    }
    object AbilityDetail : Nav("abilityListingDetail/{listingName}") {
        const val ARG_NAME = "listingName"
        fun buildRoute(listing: Ability.Listing) = "abilityListingDetail/${Uri.encode(listing.name)}"
    }
    object StatusEffectSearch : Nav("statusEffectSearch")
    object StatusEffectContributions : Nav("statusEffectContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "statusEffectContributions"
        fun buildEditRoute(effect: StatusEffect) = buildEditRouteByName(effect.name)
        fun buildEditRouteByName(name: String) = "statusEffectContributions?name=${Uri.encode(name)}"
    }
    object StatusEffectDetail : Nav("statusEffectDetail/{statusEffectName}") {
        const val ARG_NAME = "statusEffectName"
        fun buildRoute(effect: StatusEffect) = "statusEffectDetail/${Uri.encode(effect.name)}"
    }
    object CharacterBuildSearch : Nav("characterBuildSearch")
    object CharacterBuildContributions : Nav("characterBuildContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "characterBuildContributions"
        fun buildEditRoute(build: CharacterBuild) = "characterBuildContributions?name=${Uri.encode(build.name)}"
    }
    object CharacterBuildDetail : Nav("characterBuildDetail/{buildName}") {
        const val ARG_NAME = "buildName"
        fun buildRoute(build: CharacterBuild) = "characterBuildDetail/${Uri.encode(build.name)}"
    }
    object RaceTemplateSearch : Nav("raceTemplateSearch")
    object RaceTemplateContributions : Nav("raceTemplateContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "raceTemplateContributions"
        fun buildEditRoute(template: RaceTemplate) = buildEditRouteByName(template.name)
        fun buildEditRouteByName(name: String) = "raceTemplateContributions?name=${Uri.encode(name)}"
    }
}
