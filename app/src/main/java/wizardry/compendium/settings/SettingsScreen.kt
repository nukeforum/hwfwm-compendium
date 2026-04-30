package wizardry.compendium.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val essenceContributionsEnabled by viewModel.essenceContributionsEnabled.collectAsState(initial = false)
    val awakeningStoneContributionsEnabled by viewModel.awakeningStoneContributionsEnabled.collectAsState(initial = false)
    val abilityListingContributionsEnabled by viewModel.abilityListingContributionsEnabled.collectAsState(initial = false)
    SettingsContent(
        essenceContributionsEnabled = essenceContributionsEnabled,
        onEssenceContributionsToggled = viewModel::setEssenceContributionsEnabled,
        awakeningStoneContributionsEnabled = awakeningStoneContributionsEnabled,
        onAwakeningStoneContributionsToggled = viewModel::setAwakeningStoneContributionsEnabled,
        abilityListingContributionsEnabled = abilityListingContributionsEnabled,
        onAbilityListingContributionsToggled = viewModel::setAbilityListingContributionsEnabled,
    )
}

@Composable
fun SettingsContent(
    essenceContributionsEnabled: Boolean,
    onEssenceContributionsToggled: (Boolean) -> Unit,
    awakeningStoneContributionsEnabled: Boolean,
    onAwakeningStoneContributionsToggled: (Boolean) -> Unit,
    abilityListingContributionsEnabled: Boolean,
    onAbilityListingContributionsToggled: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToggleRow(
            title = "My Essences",
            subtitle = "Include your submitted essences",
            checked = essenceContributionsEnabled,
            onCheckedChange = onEssenceContributionsToggled,
        )
        ToggleRow(
            title = "My Awakening Stones",
            subtitle = "Include your submitted awakening stones",
            checked = awakeningStoneContributionsEnabled,
            onCheckedChange = onAwakeningStoneContributionsToggled,
        )
        ToggleRow(
            title = "My Ability Listings",
            subtitle = "Include your submitted ability listings",
            checked = abilityListingContributionsEnabled,
            onCheckedChange = onAbilityListingContributionsToggled,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentOffPreview() {
    SettingsContent(
        essenceContributionsEnabled = false,
        onEssenceContributionsToggled = {},
        awakeningStoneContributionsEnabled = false,
        onAwakeningStoneContributionsToggled = {},
        abilityListingContributionsEnabled = false,
        onAbilityListingContributionsToggled = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentMixedPreview() {
    SettingsContent(
        essenceContributionsEnabled = true,
        onEssenceContributionsToggled = {},
        awakeningStoneContributionsEnabled = false,
        onAwakeningStoneContributionsToggled = {},
        abilityListingContributionsEnabled = true,
        onAbilityListingContributionsToggled = {},
    )
}
