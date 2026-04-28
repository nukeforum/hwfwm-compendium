package com.mobile.wizardry.compendium

import android.net.Uri
import com.mobile.wizardry.compendium.essences.model.Essence

sealed class Nav(val route: String) {
    object EssenceSearch : Nav("search")
    object EssenceRandomizer : Nav("randomizer")
    object Settings : Nav("settings")
    object Contributions : Nav("contributions")
    object EssenceDetail : Nav("detail/{essenceName}") {
        const val ARG_NAME = "essenceName"
        fun buildRoute(essence: Essence) = "detail/${Uri.encode(essence.name)}"
    }
}
