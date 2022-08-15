package com.mobile.wizardry.compendium.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)

val RestrictedRed = Color.Red.copy(alpha = 0.5f)

@Composable
fun essenceHighlight(isRestricted: Boolean): Color {
    return if(isRestricted) RestrictedRed else Color.DarkGray
}
