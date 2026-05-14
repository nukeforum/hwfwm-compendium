package wizardry.compendium.ability.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wizardry.compendium.ability.Report
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Resource
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import kotlin.time.Duration

@Composable
fun AbilityPreview(
    ability: Ability,
    modifier: Modifier = Modifier,
    rankCeiling: Rank? = null,
) {
    Column(modifier = modifier) {
        Report(ability, rankCeiling)
    }
}

@PreviewLightDark
@Composable
private fun AbilityPreviewListingPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        AbilityPreview(
            modifier = Modifier.padding(8.dp),
            ability = Ability.Listing(
                name = "Flame Bolt",
                effects = listOf(
                    Effect.AbilityEffect(
                        rank = Rank.Iron,
                        type = AbilityType.Conjuration,
                        properties = listOf(Property.Fire, Property.Magic),
                        cost = listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
                        cooldown = Duration.ZERO,
                        description = "Hurls a bolt of flame at a target.",
                    ),
                ),
            ),
        )
    }
}
