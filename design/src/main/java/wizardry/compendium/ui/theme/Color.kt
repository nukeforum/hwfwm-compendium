package wizardry.compendium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RestrictedRed = Color.Red.copy(alpha = 0.5f)

@Composable
fun essenceHighlight(isRestricted: Boolean): Color {
    return if (isRestricted) RestrictedRed else MaterialTheme.colorScheme.surfaceVariant
}
