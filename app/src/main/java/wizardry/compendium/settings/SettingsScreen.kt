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
    val essenceConflictCount by viewModel.essenceConflictCount.collectAsState(initial = 0)
    val awakeningStoneConflictCount by viewModel.awakeningStoneConflictCount.collectAsState(initial = 0)
    val abilityListingConflictCount by viewModel.abilityListingConflictCount.collectAsState(initial = 0)
    SettingsContent(
        essenceContributionsEnabled = essenceContributionsEnabled,
        essenceConflictCount = essenceConflictCount,
        onEssenceContributionsToggled = viewModel::setEssenceContributionsEnabled,
        awakeningStoneContributionsEnabled = awakeningStoneContributionsEnabled,
        awakeningStoneConflictCount = awakeningStoneConflictCount,
        onAwakeningStoneContributionsToggled = viewModel::setAwakeningStoneContributionsEnabled,
        abilityListingContributionsEnabled = abilityListingContributionsEnabled,
        abilityListingConflictCount = abilityListingConflictCount,
        onAbilityListingContributionsToggled = viewModel::setAbilityListingContributionsEnabled,
    )
}

@Composable
fun SettingsContent(
    essenceContributionsEnabled: Boolean,
    essenceConflictCount: Int,
    onEssenceContributionsToggled: (Boolean) -> Unit,
    awakeningStoneContributionsEnabled: Boolean,
    awakeningStoneConflictCount: Int,
    onAwakeningStoneContributionsToggled: (Boolean) -> Unit,
    abilityListingContributionsEnabled: Boolean,
    abilityListingConflictCount: Int,
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
            conflictCount = essenceConflictCount,
            onCheckedChange = onEssenceContributionsToggled,
        )
        ToggleRow(
            title = "My Awakening Stones",
            subtitle = "Include your submitted awakening stones",
            checked = awakeningStoneContributionsEnabled,
            conflictCount = awakeningStoneConflictCount,
            onCheckedChange = onAwakeningStoneContributionsToggled,
        )
        ToggleRow(
            title = "My Ability Listings",
            subtitle = "Include your submitted ability listings",
            checked = abilityListingContributionsEnabled,
            conflictCount = abilityListingConflictCount,
            onCheckedChange = onAbilityListingContributionsToggled,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    conflictCount: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val locked = conflictCount > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            if (locked) {
                Text(
                    text = "Resolve $conflictCount conflict${if (conflictCount == 1) "" else "s"} to enable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Switch(
            checked = checked && !locked,
            onCheckedChange = onCheckedChange,
            enabled = !locked,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentOffPreview() {
    SettingsContent(
        essenceContributionsEnabled = false,
        essenceConflictCount = 0,
        onEssenceContributionsToggled = {},
        awakeningStoneContributionsEnabled = false,
        awakeningStoneConflictCount = 0,
        onAwakeningStoneContributionsToggled = {},
        abilityListingContributionsEnabled = false,
        abilityListingConflictCount = 0,
        onAbilityListingContributionsToggled = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentConflictPreview() {
    SettingsContent(
        essenceContributionsEnabled = true,
        essenceConflictCount = 2,
        onEssenceContributionsToggled = {},
        awakeningStoneContributionsEnabled = false,
        awakeningStoneConflictCount = 0,
        onAwakeningStoneContributionsToggled = {},
        abilityListingContributionsEnabled = true,
        abilityListingConflictCount = 1,
        onAbilityListingContributionsToggled = {},
    )
}
