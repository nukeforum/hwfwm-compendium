package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.model.Essence

sealed class Nav(val route: String) {
    object EssenceSearch : Nav("search")
    object EssenceRandomizer : Nav("randomizer")
    object Settings : Nav("settings")
    object Contributions : Nav("contributions")
    object EssenceDetail : Nav("{essenceHash}/detail") {
        fun buildRoute(essence: Essence) = "${essence.hashCode()}/detail"
    }
}
