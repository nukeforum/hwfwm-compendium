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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.ContributionDropdown
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.ContributionReportCard
import wizardry.compendium.ui.DeleteContributionButton
import wizardry.compendium.ui.EditPreviewToggle

@Composable
fun AwakeningStoneContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    /**
     * Hook for the Import action shown in Create mode. Returns a tuple of
     * (stone, errorMessage) where exactly one is non-null. The screen
     * uses the stone to pre-fill the form; errorMessage drives a dialog.
     * App-side wires this to ShareViewModel.decodeSingleStone.
     */
    onPasteImport: (text: String) -> Pair<AwakeningStone?, String?> = { null to null },
    viewModel: AwakeningStoneContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            is AwakeningStoneContributionsViewModel.SaveState.Deleted -> onContributionDeleted()
            is AwakeningStoneContributionsViewModel.SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    var importedInitial by remember { mutableStateOf<AwakeningStone?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var pasteText by remember { mutableStateOf("") }

    when (val current = mode) {
        AwakeningStoneContributionsViewModel.Mode.Create -> {
            AwakeningStoneForm(
                initial = importedInitial,
                isEdit = false,
                saveState = saveState,
                onSave = { name, rarity -> viewModel.saveAwakeningStone(name, rarity) },
                onDelete = {},
                onImportClick = {
                    pasteText = ""
                    showImportDialog = true
                },
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
                // Edit mode doesn't surface an Import button — you're
                // editing an existing entry, not bringing in fresh data.
                onImportClick = null,
            )
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Awakening Stone") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("Paste a single-stone share") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val (stone, error) = onPasteImport(pasteText)
                    showImportDialog = false
                    if (stone != null) {
                        importedInitial = stone
                    } else {
                        importErrorMessage = error
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
        )
    }

    importErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importErrorMessage = null },
            title = { Text("Couldn't import") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { importErrorMessage = null }) { Text("OK") }
            },
        )
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
    onImportClick: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var rarity by remember(initial) { mutableStateOf(initial?.rarity ?: Rarity.Common) }
    var preview by rememberSaveable { mutableStateOf(false) }

    val saving = saveState is AwakeningStoneContributionsViewModel.SaveState.Saving
    val canSave = name.isNotBlank() && !saving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditPreviewToggle(isPreview = preview, onChange = { preview = it })

        if (!preview) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = isEdit,
            )

            ContributionDropdown(
                label = "Rarity",
                options = Rarity.entries,
                selected = rarity,
                optionLabel = { it.name },
                onSelected = { rarity = it },
            )
        } else {
            ContributionReportCard(
                text = awakeningStonePreviewText(name, rarity),
            )
        }

        ContributionErrorFeedback(
            error = (saveState as? AwakeningStoneContributionsViewModel.SaveState.Error)?.message,
        )

        Button(
            onClick = { onSave(name, rarity) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Awakening Stone" else "Save Awakening Stone")
        }

        if (isEdit) {
            DeleteContributionButton(
                name = initial?.name.orEmpty(),
                enabled = !saving,
                onDelete = onDelete,
            )
        }

        if (!isEdit && onImportClick != null) {
            // Below the save button so the primary action stays prominent.
            // Imports are a "I have a thing in my clipboard" power-user
            // operation, not the typical contribute flow.
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import from Share") }
        }
    }
}

private fun awakeningStonePreviewText(name: String, rarity: Rarity): String =
    """
        Item: [${name.ifBlank { "(unnamed)" }} Awakening Stone]
        (${rarity.name.lowercase()})
    """.trimIndent()

@Preview(showBackground = true)
@Composable
private fun AwakeningStoneFormIdlePreview() {
    AwakeningStoneForm(
        initial = null,
        isEdit = false,
        saveState = AwakeningStoneContributionsViewModel.SaveState.Idle,
        onSave = { _, _ -> },
        onDelete = {},
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
    )
}
