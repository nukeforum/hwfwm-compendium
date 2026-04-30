package wizardry.compendium

import android.net.Uri
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Essence

sealed class Nav(val route: String) {
    object Landing : Nav("landing")
    object EssenceSearch : Nav("search")
    object AwakeningStoneSearch : Nav("stoneSearch")
    object AbilityListingSearch : Nav("abilityListingSearch")
    object EssenceRandomizer : Nav("randomizer")
    object Settings : Nav("settings")
    object Contributions : Nav("contributions")
    object AwakeningStoneContributions : Nav("stoneContributions")
    object AbilityListingContributions : Nav("abilityListingContributions")
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
}
