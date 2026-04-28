package com.mobile.wizardry.compendium.settings

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
    val contributionsEnabled by viewModel.contributionsEnabled.collectAsState(initial = false)
    SettingsContent(
        contributionsEnabled = contributionsEnabled,
        onContributionsToggled = viewModel::setContributionsEnabled,
    )
}

@Composable
fun SettingsContent(
    contributionsEnabled: Boolean,
    onContributionsToggled: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Contributions",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Include your submitted essences",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = contributionsEnabled,
                onCheckedChange = onContributionsToggled,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentOffPreview() {
    SettingsContent(contributionsEnabled = false, onContributionsToggled = {})
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentOnPreview() {
    SettingsContent(contributionsEnabled = true, onContributionsToggled = {})
}
