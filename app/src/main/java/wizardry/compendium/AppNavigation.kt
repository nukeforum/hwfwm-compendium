package wizardry.compendium

import android.net.Uri
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.StatusEffect

sealed class Nav(val route: String) {
    object Landing : Nav("landing")
    object EssenceSearch : Nav("search")
    object AwakeningStoneSearch : Nav("stoneSearch")
    object AbilityListingSearch : Nav("abilityListingSearch")
    object EssenceRandomizer : Nav("randomizer")
    object Settings : Nav("settings")
    object Conflicts : Nav("conflicts")
    object Contributions : Nav("contributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "contributions"
        fun buildEditRoute(essence: Essence) = "contributions?name=${Uri.encode(essence.name)}"
    }
    object AwakeningStoneContributions : Nav("stoneContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "stoneContributions"
        fun buildEditRoute(stone: AwakeningStone) = "stoneContributions?name=${Uri.encode(stone.name)}"
    }
    object AbilityListingContributions : Nav("abilityListingContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "abilityListingContributions"
        fun buildEditRoute(listing: Ability.Listing) = "abilityListingContributions?name=${Uri.encode(listing.name)}"
    }
    object EssenceDetail : Nav("detail/{essenceName}") {
        const val ARG_NAME = "essenceName"
        fun buildRoute(essence: Essence) = "detail/${Uri.encode(essence.name)}"
    }
    object AwakeningStoneDetail : Nav("stoneDetail/{stoneName}") {
        const val ARG_NAME = "stoneName"
        fun buildRoute(stone: AwakeningStone) = "stoneDetail/${Uri.encode(stone.name)}"
    }
    object AbilityListingDetail : Nav("abilityListingDetail/{listingName}") {
        const val ARG_NAME = "listingName"
        fun buildRoute(listing: Ability.Listing) = "abilityListingDetail/${Uri.encode(listing.name)}"
    }
    object StatusEffectSearch : Nav("statusEffectSearch")
    object StatusEffectContributions : Nav("statusEffectContributions?name={name}") {
        const val ARG_NAME = "name"
        const val newRoute = "statusEffectContributions"
        fun buildEditRoute(effect: StatusEffect) = "statusEffectContributions?name=${Uri.encode(effect.name)}"
    }
    object StatusEffectDetail : Nav("statusEffectDetail/{statusEffectName}") {
        const val ARG_NAME = "statusEffectName"
        fun buildRoute(effect: StatusEffect) = "statusEffectDetail/${Uri.encode(effect.name)}"
    }
}
