package com.mobile.wizardry.compendium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobile.wizardry.compendium.LocalNavController
import com.mobile.wizardry.compendium.Nav
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun LinkedEssence(
    essence: Essence,
    isRestricted: Boolean,
) {
    val navHostController = LocalNavController.current
    Text(
        text = essence.name,
        modifier = Modifier
            .clickable { navHostController.navigate(Nav.EssenceDetail(essence).route) }
            .background(essenceHighlight(isRestricted = isRestricted))
            .border(1.dp, Color.DarkGray)
            .padding(8.dp),
    )
}
