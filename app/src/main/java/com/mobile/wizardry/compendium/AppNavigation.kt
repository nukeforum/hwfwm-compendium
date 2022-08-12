package com.mobile.wizardry.compendium

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import com.mobile.wizardry.compendium.essences.model.Essence

val LocalNavController = compositionLocalOf<NavHostController> {
    throw IllegalStateException("No NavController provided in this scope")
}

sealed class Nav(val route: String) {
    object EssenceSearch : Nav("essencesearch")
    class EssenceDetail(
        essence: Essence? = null
    ) : Nav("essencedetail/${essence?.hashCode() ?: "{essenceHash}"}")
}
