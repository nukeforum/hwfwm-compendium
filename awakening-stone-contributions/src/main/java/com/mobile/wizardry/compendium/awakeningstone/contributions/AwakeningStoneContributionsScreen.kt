package com.mobile.wizardry.compendium.awakeningstone.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mobile.wizardry.compendium.essences.model.Rarity

@Composable
fun AwakeningStoneContributionsScreen(
    viewModel: AwakeningStoneContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    AwakeningStoneForm(
        saveState = saveState,
        onSave = { name, rarity -> viewModel.saveAwakeningStone(name, rarity) },
        onClearState = viewModel::clearSaveState,
    )
}

@Composable
private fun AwakeningStoneForm(
    saveState: AwakeningStoneContributionsViewModel.SaveState,
    onSave: (name: String, rarity: Rarity) -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf(Rarity.Common) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        RarityDropdown(
            selected = rarity,
            onSelected = { rarity = it },
        )

        SaveFeedback(saveState = saveState, onClearState = onClearState)

        Button(
            onClick = { onSave(name, rarity) },
            modifier = Modifier.fillMaxWidth(),
            enabled = saveState !is AwakeningStoneContributionsViewModel.SaveState.Saving,
        ) {
            Text("Save Awakening Stone")
        }
    }
}

@Composable
private fun RarityDropdown(
    selected: Rarity,
    onSelected: (Rarity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected.name)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Rarity.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.name) },
                    onClick = {
                        onSelected(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SaveFeedback(
    saveState: AwakeningStoneContributionsViewModel.SaveState,
    onClearState: () -> Unit,
) {
    when (saveState) {
        is AwakeningStoneContributionsViewModel.SaveState.Success -> {
            LaunchedEffect(saveState) {
                kotlinx.coroutines.delay(2000)
                onClearState()
            }
            Text(
                text = "Saved successfully",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is AwakeningStoneContributionsViewModel.SaveState.Error -> {
            Text(
                text = saveState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AwakeningStoneFormIdlePreview() {
    AwakeningStoneForm(
        saveState = AwakeningStoneContributionsViewModel.SaveState.Idle,
        onSave = { _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AwakeningStoneFormErrorPreview() {
    AwakeningStoneForm(
        saveState = AwakeningStoneContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onSave = { _, _ -> },
        onClearState = {},
    )
}
