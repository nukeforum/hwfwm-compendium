package wizardry.compendium.characterbuild.contributions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.EssenceChangePrompt
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.Mode
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.SaveState
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.Slot
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.DeleteContributionButton
import wizardry.compendium.ui.EditPreviewToggle
import wizardry.compendium.ui.SearchableSelectionSheet

@Composable
fun CharacterBuildContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    viewModel: CharacterBuildContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val form by viewModel.formState.collectAsState()
    val prompt by viewModel.essenceChangePrompt.collectAsState()
    val setPrompt by viewModel.confluenceSetPrompt.collectAsState()
    val savePrompt by viewModel.saveCombinationPrompt.collectAsState()
    val availableEssences by viewModel.availableEssences.collectAsState()
    val availableConfluences by viewModel.availableConfluences.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            SaveState.Deleted -> onContributionDeleted()
            SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    when (val current = mode) {
        Mode.Create -> Form(
            isEdit = false,
            startCollapsed = true,
            form = form,
            availableEssences = availableEssences,
            availableConfluences = availableConfluences,
            saveState = saveState,
            viewModel = viewModel,
        )
        Mode.Edit.Loading -> CenteredText("Loading")
        Mode.Edit.NotFound -> CenteredText("This build is not a user contribution and cannot be edited.")
        is Mode.Edit.Ready -> Form(
            isEdit = true,
            startCollapsed = true,
            form = form,
            availableEssences = availableEssences,
            availableConfluences = availableConfluences,
            saveState = saveState,
            viewModel = viewModel,
        )
    }

    prompt?.let { EssencePromptDialog(it, viewModel) }
    setPrompt?.let { ConfluenceSetPickerDialog(it, viewModel) }
    savePrompt?.let { SaveCombinationDialog(it, viewModel) }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Form(
    isEdit: Boolean,
    startCollapsed: Boolean,
    form: CharacterBuildContributionsViewModel.FormState,
    availableEssences: List<Essence.Manifestation>,
    availableConfluences: List<Essence.Confluence>,
    saveState: SaveState,
    viewModel: CharacterBuildContributionsViewModel,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    val saving = saveState is SaveState.Saving
    val canSave = form.name.isNotBlank() && form.race.isNotBlank() && !saving

    var racialPickerOpen by remember { mutableStateOf(false) }
    var essencePickerSlot by remember { mutableStateOf<Slot?>(null) }
    var abilityPickerSlot by remember { mutableStateOf<Slot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditPreviewToggle(isPreview = preview, onChange = { preview = it })

        if (preview) {
            Text("Preview: ${form.name.ifBlank { "(unnamed)" }} — ${form.race.ifBlank { "(no race)" }}")
        } else {
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::setName,
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = isEdit,
            )
            OutlinedTextField(
                value = form.race,
                onValueChange = viewModel::setRace,
                label = { Text("Race *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            CollapsibleCard(
                title = "Racial abilities (${form.racialAbilities.size}/6)",
                startExpanded = !startCollapsed,
            ) {
                ChipFlow(
                    items = form.racialAbilities,
                    label = { it.name },
                    onRemove = { viewModel.removeRacialAbility(it.name) },
                )
                OutlinedButton(
                    onClick = { racialPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = form.racialAbilities.size < 6,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(text = "  Add racial ability")
                }
            }

            Slot.entries.forEach { slot ->
                val state = form.attributes[slot] ?: return@forEach
                val essenceLabel = state.essence?.name ?: "(no essence)"
                CollapsibleCard(
                    title = "$slot — $essenceLabel${if (state.essence != null) " (${state.abilities.size}/5)" else ""}",
                    startExpanded = !startCollapsed,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Essence: $essenceLabel",
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { essencePickerSlot = slot }) { Text("Change") }
                    }
                    if (state.essence != null) {
                        Text("Abilities (${state.abilities.size}/5)")
                        ChipFlow(
                            items = state.abilities,
                            label = { it.name },
                            onRemove = { viewModel.removeAbilityFromSlot(slot, it.name) },
                        )
                        OutlinedButton(
                            onClick = { abilityPickerSlot = slot },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.abilities.size < 5,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(text = "  Add ability")
                        }
                    }
                }
            }
        }

        ContributionErrorFeedback(
            error = (saveState as? SaveState.Error)?.message,
        )

        Button(
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Build" else "Save Build")
        }

        if (isEdit) {
            DeleteContributionButton(
                name = form.name,
                enabled = !saving,
                onDelete = viewModel::deleteContribution,
            )
        }
    }

    if (racialPickerOpen) {
        SearchableSelectionSheet(
            title = "Racial abilities",
            options = viewModel.racialAbilityCandidates() - form.racialAbilities.toSet(),
            initiallySelected = emptySet(),
            multiSelect = true,
            maxSelections = 6 - form.racialAbilities.size,
            labelOf = { it.name },
            onDismiss = { racialPickerOpen = false },
            onConfirm = { picks ->
                picks.forEach { viewModel.addRacialAbility(it) }
                racialPickerOpen = false
            },
        )
    }

    essencePickerSlot?.let { slot ->
        val rows = viewModel.confluencePickerRowsFor(slot)
        val subtitleByName = rows.associate { it.confluence.name to it.matchedEssences }
        val sortedConfluences = rows.map { it.confluence }
        EssencePickerSheet(
            title = "$slot essence",
            manifestations = availableEssences,
            confluences = sortedConfluences,
            initiallySelected = form.attributes[slot]?.essence,
            showSegmentedControl = true,
            onDismiss = { essencePickerSlot = null },
            onConfirm = { pick ->
                when (pick) {
                    is Essence.Confluence -> viewModel.requestConfluencePick(slot, pick)
                    else -> viewModel.requestEssenceChange(slot, pick)
                }
                essencePickerSlot = null
            },
            subtitleOf = { option ->
                val matched = subtitleByName[option.name].orEmpty()
                if (matched.isEmpty()) null
                else "Includes ${matched.joinToString(", ") { it.name }}"
            },
        )
    }

    abilityPickerSlot?.let { slot ->
        val current = form.attributes[slot]?.abilities.orEmpty().toSet()
        SearchableSelectionSheet(
            title = "$slot abilities",
            options = viewModel.slotAbilityCandidates() - current,
            initiallySelected = emptySet(),
            multiSelect = true,
            maxSelections = 5 - current.size,
            labelOf = { it.name },
            onDismiss = { abilityPickerSlot = null },
            onConfirm = { picks ->
                picks.forEach { viewModel.addAbilityToSlot(slot, it) }
                abilityPickerSlot = null
            },
        )
    }
}

@Composable
private fun EssencePromptDialog(
    prompt: EssenceChangePrompt,
    viewModel: CharacterBuildContributionsViewModel,
) {
    val targetLabel = prompt.target?.name ?: "(no essence)"
    AlertDialog(
        onDismissRequest = viewModel::cancelEssenceChange,
        title = { Text("Change essence") },
        text = {
            Text(
                "The ${prompt.slot} slot has selected abilities. " +
                    "Changing the essence to $targetLabel — do you want to also clear the selected abilities?",
            )
        },
        confirmButton = {
            Button(onClick = viewModel::confirmEssenceChangeClearingAbilities) { Text("Yes") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = viewModel::confirmEssenceChangeKeepingAbilities) { Text("No") }
                OutlinedButton(
                    onClick = viewModel::cancelEssenceChange,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ConfluenceSetPickerDialog(
    prompt: CharacterBuildContributionsViewModel.ConfluenceSetPrompt,
    viewModel: CharacterBuildContributionsViewModel,
) {
    AlertDialog(
        onDismissRequest = viewModel::cancelConfluenceSetPick,
        title = { Text("Choose ${prompt.confluence.name} set") },
        text = {
            Column {
                Text("This Confluence has multiple known combinations. Pick one to fill the empty slots.")
                Spacer(Modifier.height(8.dp))
                prompt.sets.forEach { set ->
                    val names = set.set.map { it.name }.sorted().joinToString(" · ")
                    OutlinedButton(
                        onClick = { viewModel.confirmConfluenceSetPick(set) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(names) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = viewModel::cancelConfluenceSetPick) { Text("Cancel") }
        },
    )
}

@Composable
private fun SaveCombinationDialog(
    prompt: CharacterBuildContributionsViewModel.SaveCombinationPrompt,
    viewModel: CharacterBuildContributionsViewModel,
) {
    val combo = prompt.combination.joinToString(" + ") { it.name }
    AlertDialog(
        onDismissRequest = { viewModel.dismissSaveCombinationPrompt(complete = false) },
        title = { Text("Save new combination?") },
        text = {
            Text("$combo isn't a known set for ${prompt.confluence.name}. Save this as a new combination?")
        },
        confirmButton = {
            Button(onClick = viewModel::confirmSaveCombination) { Text("Yes") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { viewModel.dismissSaveCombinationPrompt(complete = true) }) {
                    Text("No")
                }
                OutlinedButton(
                    onClick = { viewModel.dismissSaveCombinationPrompt(complete = false) },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun CollapsibleCard(
    title: String,
    startExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(startExpanded) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipFlow(
    items: List<T>,
    label: (T) -> String,
    onRemove: (T) -> Unit,
) {
    if (items.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onRemove(item) },
                label = { Text(label(item)) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${label(item)}",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}
