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
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun LinkedEssence(
    essence: Essence,
    isLastViewed: Boolean,
    isRestricted: Boolean,
    onEssenceClick: (Essence) -> Unit
) {
    Text(
        text = essence.name,
        modifier = Modifier
            .clickable { onEssenceClick(essence) }
            .background(essenceHighlight(isRestricted = isRestricted))
            .border(1.dp, if (isLastViewed) Color.Cyan else Color.DarkGray)
            .padding(8.dp),
    )
}
