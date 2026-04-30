package wizardry.compendium.awakeningstone.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity

@Composable
fun AwakeningStoneContributionsScreen(
    onContributionDeleted: () -> Unit = {},
    viewModel: AwakeningStoneContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is AwakeningStoneContributionsViewModel.SaveState.Deleted) {
            onContributionDeleted()
        }
    }

    when (val current = mode) {
        AwakeningStoneContributionsViewModel.Mode.Create -> {
            AwakeningStoneForm(
                initial = null,
                isEdit = false,
                saveState = saveState,
                onSave = { name, rarity -> viewModel.saveAwakeningStone(name, rarity) },
                onDelete = {},
                onClearState = viewModel::clearSaveState,
            )
        }
        AwakeningStoneContributionsViewModel.Mode.Edit.Loading -> Loading()
        AwakeningStoneContributionsViewModel.Mode.Edit.NotFound -> NotFound()
        is AwakeningStoneContributionsViewModel.Mode.Edit.Ready -> {
            AwakeningStoneForm(
                initial = current.stone,
                isEdit = true,
                saveState = saveState,
                onSave = { name, rarity -> viewModel.saveAwakeningStone(name, rarity) },
                onDelete = viewModel::deleteContribution,
                onClearState = viewModel::clearSaveState,
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading")
    }
}

@Composable
private fun NotFound() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This awakening stone is not a user contribution and cannot be edited.")
    }
}

@Composable
private fun AwakeningStoneForm(
    initial: AwakeningStone?,
    isEdit: Boolean,
    saveState: AwakeningStoneContributionsViewModel.SaveState,
    onSave: (name: String, rarity: Rarity) -> Unit,
    onDelete: () -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var rarity by remember(initial) { mutableStateOf(initial?.rarity ?: Rarity.Common) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            name = initial?.name.orEmpty(),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

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
            readOnly = isEdit,
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
            Text(if (isEdit) "Update Awakening Stone" else "Save Awakening Stone")
        }

        if (isEdit) {
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = saveState !is AwakeningStoneContributionsViewModel.SaveState.Saving,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("Delete Contribution")
            }
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete contribution?") },
        text = { Text("Permanently delete \"$name\" from your contributions?") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
        initial = null,
        isEdit = false,
        saveState = AwakeningStoneContributionsViewModel.SaveState.Idle,
        onSave = { _, _ -> },
        onDelete = {},
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AwakeningStoneFormErrorPreview() {
    AwakeningStoneForm(
        initial = null,
        isEdit = false,
        saveState = AwakeningStoneContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onSave = { _, _ -> },
        onDelete = {},
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AwakeningStoneFormEditPreview() {
    AwakeningStoneForm(
        initial = AwakeningStone.of("Sample", Rarity.Rare),
        isEdit = true,
        saveState = AwakeningStoneContributionsViewModel.SaveState.Idle,
        onSave = { _, _ -> },
        onDelete = {},
        onClearState = {},
    )
}
