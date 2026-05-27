package wizardry.compendium.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.ui.theme.essenceHighlight

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
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (isRestricted) stateDescription = "Restricted"
            }
            .clickable { onEssenceClick(essence) }
            .background(essenceHighlight(isRestricted = isRestricted))
            .border(1.dp, if (isLastViewed) Color.Cyan else Color.DarkGray)
            .defaultMinSize(minHeight = 48.dp)
            .padding(8.dp),
    )
}

@PreviewLightDark
@Composable
internal fun LinkedEssencePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        LinkedEssence(
            essence = Essence.Manifestation(
                "Light",
                Rank.Iron,
                Rarity.Uncommon,
                properties = listOf(Property.Light),
                description = "a description",
                isRestricted = false
            ),
            isLastViewed = true,
            isRestricted = false,
            onEssenceClick = {}
        )
    }
}
