package wizardry.compendium.ability.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wizardry.compendium.ability.Report
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Rank

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
