package wizardry.compendium.ability.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import wizardry.compendium.ability.Report
import wizardry.compendium.essences.model.Ability

@Composable
fun AbilityPreview(ability: Ability, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Report(ability)
    }
}
