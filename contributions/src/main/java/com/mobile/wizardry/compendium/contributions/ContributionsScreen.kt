package com.mobile.wizardry.compendium.contributions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity

@Composable
fun ContributionsScreen(viewModel: ContributionsViewModel = hiltViewModel()) {
    val availableManifestations by viewModel.availableManifestations.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Essence") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Confluence") },
            )
        }

        when (selectedTab) {
            0 -> ManifestationForm(
                saveState = saveState,
                onSave = { name, rarity, description, isRestricted ->
                    viewModel.saveManifestation(name, rarity, description, isRestricted)
                },
                onClearState = viewModel::clearSaveState,
            )
            1 -> ConfluenceForm(
                availableManifestations = availableManifestations,
                saveState = saveState,
                onSave = { name, m1, m2, m3, isRestricted ->
                    viewModel.saveConfluence(name, m1, m2, m3, isRestricted)
                },
                onClearState = viewModel::clearSaveState,
            )
        }
    }
}

@Composable
private fun ManifestationForm(
    saveState: ContributionsViewModel.SaveState,
    onSave: (name: String, rarity: Rarity, description: String, isRestricted: Boolean) -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rarity by remember { mutableStateOf(Rarity.Common) }
    var description by remember { mutableStateOf("") }
    var isRestricted by remember { mutableStateOf(false) }

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

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Restricted")
            Switch(checked = isRestricted, onCheckedChange = { isRestricted = it })
        }

        SaveFeedback(saveState = saveState, onClearState = onClearState)

        Button(
            onClick = { onSave(name, rarity, description, isRestricted) },
            modifier = Modifier.fillMaxWidth(),
            enabled = saveState !is ContributionsViewModel.SaveState.Saving,
        ) {
            Text("Save Essence")
        }
    }
}

@Composable
private fun ConfluenceForm(
    availableManifestations: List<Essence.Manifestation>,
    saveState: ContributionsViewModel.SaveState,
    onSave: (name: String, m1: Essence.Manifestation, m2: Essence.Manifestation, m3: Essence.Manifestation, isRestricted: Boolean) -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isRestricted by remember { mutableStateOf(false) }
    var manifestation1 by remember { mutableStateOf<Essence.Manifestation?>(null) }
    var manifestation2 by remember { mutableStateOf<Essence.Manifestation?>(null) }
    var manifestation3 by remember { mutableStateOf<Essence.Manifestation?>(null) }

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
            label = { Text("Confluence Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        ManifestationDropdown(
            label = "Essence 1",
            selected = manifestation1,
            options = availableManifestations,
            onSelected = { manifestation1 = it },
        )

        ManifestationDropdown(
            label = "Essence 2",
            selected = manifestation2,
            options = availableManifestations,
            onSelected = { manifestation2 = it },
        )

        ManifestationDropdown(
            label = "Essence 3",
            selected = manifestation3,
            options = availableManifestations,
            onSelected = { manifestation3 = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Restricted")
            Switch(checked = isRestricted, onCheckedChange = { isRestricted = it })
        }

        SaveFeedback(saveState = saveState, onClearState = onClearState)

        val canSave = manifestation1 != null && manifestation2 != null && manifestation3 != null
                && saveState !is ContributionsViewModel.SaveState.Saving
        Button(
            onClick = {
                onSave(name, manifestation1!!, manifestation2!!, manifestation3!!, isRestricted)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text("Save Confluence")
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
            Rarity.entries.forEach { rarity ->
                DropdownMenuItem(onClick = {
                    onSelected(rarity)
                    expanded = false
                }) {
                    Text(rarity.name)
                }
            }
        }
    }
}

@Composable
private fun ManifestationDropdown(
    label: String,
    selected: Essence.Manifestation?,
    options: List<Essence.Manifestation>,
    onSelected: (Essence.Manifestation) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected?.name ?: label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            options.forEach { manifestation ->
                DropdownMenuItem(onClick = {
                    onSelected(manifestation)
                    expanded = false
                }) {
                    Text(manifestation.name)
                }
            }
        }
    }
}

@Composable
private fun SaveFeedback(
    saveState: ContributionsViewModel.SaveState,
    onClearState: () -> Unit,
) {
    when (saveState) {
        is ContributionsViewModel.SaveState.Success -> {
            LaunchedEffect(saveState) {
                kotlinx.coroutines.delay(2000)
                onClearState()
            }
            Text(
                text = "Saved successfully",
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.body2,
            )
        }
        is ContributionsViewModel.SaveState.Error -> {
            Text(
                text = saveState.message,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.body2,
            )
        }
        else -> {}
    }
}

// region Previews

private val previewManifestations = listOf(
    Essence.of("Wind", "A flowing breath of air", Rarity.Common, false),
    Essence.of("Blood", "The vital essence of life", Rarity.Uncommon, false),
    Essence.of("Sin", "Manifested essence of transgression", Rarity.Legendary, false),
    Essence.of("Dark", "A creeping, consuming darkness", Rarity.Rare, false),
)

@Preview(showBackground = true)
@Composable
private fun ManifestationFormIdlePreview() {
    ManifestationForm(
        saveState = ContributionsViewModel.SaveState.Idle,
        onSave = { _, _, _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ManifestationFormSuccessPreview() {
    ManifestationForm(
        saveState = ContributionsViewModel.SaveState.Success,
        onSave = { _, _, _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ManifestationFormErrorPreview() {
    ManifestationForm(
        saveState = ContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onSave = { _, _, _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceFormEmptyPreview() {
    ConfluenceForm(
        availableManifestations = previewManifestations,
        saveState = ContributionsViewModel.SaveState.Idle,
        onSave = { _, _, _, _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceFormFilledPreview() {
    ConfluenceForm(
        availableManifestations = previewManifestations,
        saveState = ContributionsViewModel.SaveState.Idle,
        onSave = { _, _, _, _, _ -> },
        onClearState = {},
    )
}

// endregion
