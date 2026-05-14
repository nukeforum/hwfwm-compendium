package wizardry.compendium.characterbuild.contributions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.EssenceChangePrompt
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.Mode
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.PasteImportState
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.SaveState
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.Slot
import wizardry.compendium.characterbuild.contributions.CharacterBuildContributionsViewModel.SlotState
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.DeleteContributionButton
import wizardry.compendium.ui.EditPreviewToggle
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.SearchableSelectionSheet
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.wire.share.RefResolution
import wizardry.compendium.wire.share.SlotResolution

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
    val warningSlot by viewModel.confluenceWarning.collectAsState()
    val availableEssences by viewModel.availableEssences.collectAsState()
    val pasteImportState by viewModel.pasteImportState.collectAsState()

    val context = LocalContext.current
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }
            viewModel.startImportFromText(text)
        }
    }

    var warningDialogOpen by rememberSaveable { mutableStateOf(false) }
    var essencePickerSlot by rememberSaveable { mutableStateOf<Slot?>(null) }

    LaunchedEffect(saveState) {
        when (saveState) {
            SaveState.Deleted -> onContributionDeleted()
            SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    LaunchedEffect(pasteImportState) {
        if (pasteImportState is PasteImportState.Done) {
            onContributionSaved()
            viewModel.cancelImport()
        }
    }

    val formCallbacks = rememberFormCallbacks(viewModel)
    when (val current = mode) {
        Mode.Create -> Form(
            isEdit = false,
            startCollapsed = true,
            form = form,
            availableEssences = availableEssences,
            saveState = saveState,
            warningSlot = warningSlot,
            onShowWarning = { warningDialogOpen = true },
            essencePickerSlot = essencePickerSlot,
            onEssencePickerSlotChange = { essencePickerSlot = it },
            callbacks = formCallbacks,
            onImportFromFile = { openDocumentLauncher.launch(arrayOf("text/*")) },
        )
        Mode.Edit.Loading -> CenteredText("Loading")
        Mode.Edit.NotFound -> CenteredText("This build is not a user contribution and cannot be edited.")
        is Mode.Edit.Ready -> Form(
            isEdit = true,
            startCollapsed = true,
            form = form,
            availableEssences = availableEssences,
            saveState = saveState,
            warningSlot = warningSlot,
            onShowWarning = { warningDialogOpen = true },
            essencePickerSlot = essencePickerSlot,
            onEssencePickerSlotChange = { essencePickerSlot = it },
            callbacks = formCallbacks,
            onImportFromFile = null,
        )
    }

    val reviewing = pasteImportState as? PasteImportState.Reviewing
    if (reviewing != null) {
        BuildImportPreviewSheet(
            state = reviewing,
            onNameChange = viewModel::updateImportName,
            onCancel = viewModel::cancelImport,
            onSave = viewModel::confirmImport,
        )
    }

    prompt?.let { EssencePromptDialog(it, viewModel) }
    setPrompt?.let { ConfluenceSetPickerDialog(it, viewModel) }
    savePrompt?.let { SaveCombinationDialog(it, viewModel) }

    if (warningDialogOpen) {
        val ws = warningSlot
        if (ws != null) {
            ConfluenceWarningDialog(
                slot = ws,
                form = form,
                onSaveAsCombination = {
                    viewModel.resolveWarningSaveCombination()
                    warningDialogOpen = false
                },
                onChangeConfluence = {
                    warningDialogOpen = false
                    essencePickerSlot = ws
                },
                onDismiss = { warningDialogOpen = false },
            )
        } else {
            // The warning flipped away; close the dialog.
            warningDialogOpen = false
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

private data class FormCallbacks(
    val onNameChange: (String) -> Unit,
    val onRaceChange: (String) -> Unit,
    val onAddRacialAbility: (Ability.Listing) -> Unit,
    val onRemoveRacialAbility: (String) -> Unit,
    val onAddAbilityToSlot: (Slot, Ability.Listing) -> Unit,
    val onRemoveAbilityFromSlot: (Slot, String) -> Unit,
    val onRequestConfluencePick: (Slot, Essence.Confluence) -> Unit,
    val onRequestEssenceChange: (Slot, Essence?) -> Unit,
    val racialAbilityCandidates: () -> List<Ability.Listing>,
    val slotAbilityCandidates: () -> List<Ability.Listing>,
    val confluencePickerRowsFor: (Slot) -> List<CharacterBuildContributionsViewModel.ConfluencePickerRow>,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun rememberFormCallbacks(viewModel: CharacterBuildContributionsViewModel): FormCallbacks =
    remember(viewModel) {
        FormCallbacks(
            onNameChange = viewModel::setName,
            onRaceChange = viewModel::setRace,
            onAddRacialAbility = viewModel::addRacialAbility,
            onRemoveRacialAbility = viewModel::removeRacialAbility,
            onAddAbilityToSlot = viewModel::addAbilityToSlot,
            onRemoveAbilityFromSlot = viewModel::removeAbilityFromSlot,
            onRequestConfluencePick = viewModel::requestConfluencePick,
            onRequestEssenceChange = viewModel::requestEssenceChange,
            racialAbilityCandidates = viewModel::racialAbilityCandidates,
            slotAbilityCandidates = viewModel::slotAbilityCandidates,
            confluencePickerRowsFor = viewModel::confluencePickerRowsFor,
            onSave = viewModel::save,
            onDelete = viewModel::deleteContribution,
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Form(
    isEdit: Boolean,
    startCollapsed: Boolean,
    form: CharacterBuildContributionsViewModel.FormState,
    availableEssences: List<Essence.Manifestation>,
    saveState: SaveState,
    warningSlot: Slot?,
    onShowWarning: () -> Unit,
    essencePickerSlot: Slot?,
    onEssencePickerSlotChange: (Slot?) -> Unit,
    callbacks: FormCallbacks,
    onImportFromFile: (() -> Unit)?,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    val saving = saveState is SaveState.Saving
    val canSave = form.name.isNotBlank() && form.race.isNotBlank() && !saving

    var racialPickerOpen by remember { mutableStateOf(false) }
    var abilityPickerSlot by remember { mutableStateOf<Slot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditPreviewToggle(isPreview = preview, onChange = { preview = it })

        if (!isEdit && onImportFromFile != null) {
            OutlinedButton(
                onClick = onImportFromFile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = "Import from File")
            }
        }

        if (preview) {
            Text("Preview: ${form.name.ifBlank { "(unnamed)" }} — ${form.race.ifBlank { "(no race)" }}")
        } else {
            OutlinedTextField(
                value = form.name,
                onValueChange = callbacks.onNameChange,
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = isEdit,
            )
            OutlinedTextField(
                value = form.race,
                onValueChange = callbacks.onRaceChange,
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
                    onRemove = { callbacks.onRemoveRacialAbility(it.name) },
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
                    trailing = if (slot == warningSlot) {
                        {
                            IconButton(onClick = onShowWarning) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    } else null,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Essence: $essenceLabel",
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { onEssencePickerSlotChange(slot) }) { Text("Change") }
                    }
                    if (state.essence != null) {
                        Text("Abilities (${state.abilities.size}/5)")
                        ChipFlow(
                            items = state.abilities,
                            label = { it.name },
                            onRemove = { callbacks.onRemoveAbilityFromSlot(slot, it.name) },
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
            onClick = callbacks.onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Build" else "Save Build")
        }

        if (isEdit) {
            DeleteContributionButton(
                name = form.name,
                enabled = !saving,
                onDelete = callbacks.onDelete,
            )
        }
    }

    if (racialPickerOpen) {
        SearchableSelectionSheet(
            title = "Racial abilities",
            options = callbacks.racialAbilityCandidates() - form.racialAbilities.toSet(),
            initiallySelected = emptySet(),
            multiSelect = true,
            maxSelections = 6 - form.racialAbilities.size,
            labelOf = { it.name },
            onDismiss = { racialPickerOpen = false },
            onConfirm = { picks ->
                picks.forEach { callbacks.onAddRacialAbility(it) }
                racialPickerOpen = false
            },
        )
    }

    essencePickerSlot?.let { slot ->
        val rows = callbacks.confluencePickerRowsFor(slot)
        val subtitleByName = rows.associate { it.confluence.name to it.matchedEssences }
        val sortedConfluences = rows.map { it.confluence }
        EssencePickerSheet(
            title = "$slot essence",
            manifestations = availableEssences,
            confluences = sortedConfluences,
            initiallySelected = form.attributes[slot]?.essence,
            showSegmentedControl = true,
            onDismiss = { onEssencePickerSlotChange(null) },
            onConfirm = { pick ->
                when (pick) {
                    is Essence.Confluence -> callbacks.onRequestConfluencePick(slot, pick)
                    else -> callbacks.onRequestEssenceChange(slot, pick)
                }
                onEssencePickerSlotChange(null)
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
            options = callbacks.slotAbilityCandidates() - current,
            initiallySelected = emptySet(),
            multiSelect = true,
            maxSelections = 5 - current.size,
            labelOf = { it.name },
            onDismiss = { abilityPickerSlot = null },
            onConfirm = { picks ->
                picks.forEach { callbacks.onAddAbilityToSlot(slot, it) }
                abilityPickerSlot = null
            },
        )
    }
}

private fun stubFormCallbacks(): FormCallbacks = FormCallbacks(
    onNameChange = {},
    onRaceChange = {},
    onAddRacialAbility = {},
    onRemoveRacialAbility = {},
    onAddAbilityToSlot = { _, _ -> },
    onRemoveAbilityFromSlot = { _, _ -> },
    onRequestConfluencePick = { _, _ -> },
    onRequestEssenceChange = { _, _ -> },
    racialAbilityCandidates = { emptyList() },
    slotAbilityCandidates = { emptyList() },
    confluencePickerRowsFor = { emptyList() },
    onSave = {},
    onDelete = {},
)

@Composable
private fun EssencePromptDialog(
    prompt: EssenceChangePrompt,
    viewModel: CharacterBuildContributionsViewModel,
) {
    EssencePromptDialog(
        prompt = prompt,
        onClearAbilities = viewModel::confirmEssenceChangeClearingAbilities,
        onKeepAbilities = viewModel::confirmEssenceChangeKeepingAbilities,
        onCancel = viewModel::cancelEssenceChange,
    )
}

@Composable
private fun EssencePromptDialog(
    prompt: EssenceChangePrompt,
    onClearAbilities: () -> Unit,
    onKeepAbilities: () -> Unit,
    onCancel: () -> Unit,
) {
    val targetLabel = prompt.target?.name ?: "(no essence)"
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Change essence") },
        text = {
            Text(
                "The ${prompt.slot} slot has selected abilities. " +
                    "Changing the essence to $targetLabel — do you want to also clear the selected abilities?",
            )
        },
        confirmButton = {
            Button(onClick = onClearAbilities) { Text("Yes") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onKeepAbilities) { Text("No") }
                OutlinedButton(
                    onClick = onCancel,
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
    ConfluenceSetPickerDialog(
        prompt = prompt,
        onPick = viewModel::confirmConfluenceSetPick,
        onCancel = viewModel::cancelConfluenceSetPick,
    )
}

@Composable
private fun ConfluenceSetPickerDialog(
    prompt: CharacterBuildContributionsViewModel.ConfluenceSetPrompt,
    onPick: (ConfluenceSet) -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Choose ${prompt.confluence.name} set") },
        text = {
            Column {
                Text("This Confluence has multiple known combinations. Pick one to fill the empty slots.")
                Spacer(Modifier.height(8.dp))
                prompt.sets.forEach { set ->
                    val names = set.set.map { it.name }.sorted().joinToString(" · ")
                    OutlinedButton(
                        onClick = { onPick(set) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { Text(names) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

@Composable
private fun SaveCombinationDialog(
    prompt: CharacterBuildContributionsViewModel.SaveCombinationPrompt,
    viewModel: CharacterBuildContributionsViewModel,
) {
    SaveCombinationDialog(
        prompt = prompt,
        onConfirm = viewModel::confirmSaveCombination,
        onSkip = { viewModel.dismissSaveCombinationPrompt(complete = true) },
        onCancel = { viewModel.dismissSaveCombinationPrompt(complete = false) },
    )
}

@Composable
private fun SaveCombinationDialog(
    prompt: CharacterBuildContributionsViewModel.SaveCombinationPrompt,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    val combo = prompt.combination.joinToString(" + ") { it.name }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Save new combination?") },
        text = {
            Text("$combo isn't a known set for ${prompt.confluence.name}. Save this as a new combination?")
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Yes") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onSkip) { Text("No") }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ConfluenceWarningDialog(
    slot: Slot,
    form: CharacterBuildContributionsViewModel.FormState,
    onSaveAsCombination: () -> Unit,
    onChangeConfluence: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confluenceEssence = form.attributes[slot]?.essence as? Essence.Confluence
    val others = (Slot.entries - slot)
        .mapNotNull { form.attributes[it]?.essence as? Essence.Manifestation }
        .joinToString(" + ") { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unknown combination") },
        text = {
            Text("$others isn't a known set for ${confluenceEssence?.name ?: "this Confluence"}.")
        },
        confirmButton = {
            Button(onClick = onSaveAsCombination) { Text("Save as new combination") }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onChangeConfluence) { Text("Change Confluence") }
                OutlinedButton(
                    onClick = onDismiss,
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
    trailing: @Composable (() -> Unit)? = null,
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
            trailing?.invoke()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildImportPreviewSheet(
    state: PasteImportState.Reviewing,
    onNameChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    // Non-dismissible: user must explicitly Cancel or Save. Allow the initial
    // Hidden → Expanded transition but reject the Expanded → Hidden one so
    // drag-dismiss is blocked without preventing the sheet from showing.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = { /* drag-dismiss is blocked by confirmValueChange */ },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Import character build", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // Client-side collision flag: trust the decoder's pre-check when the
            // user hasn't renamed away from the original. A rename clears the
            // visible collision; the VM re-checks server-side on confirm anyway.
            val collides = state.preview.nameCollides && state.editedName == state.preview.originalName
            val blank = state.editedName.isBlank()
            OutlinedTextField(
                value = state.editedName,
                onValueChange = onNameChange,
                label = { Text("Name") },
                isError = collides || blank,
                supportingText = {
                    when {
                        collides -> Text("A build with this name already exists — pick a different name.")
                        blank -> Text("Name is required.")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))
            Text("Race: ${state.preview.race}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))

            state.preview.attributes.forEach { attr ->
                Text(attr.slot, style = MaterialTheme.typography.titleSmall)
                when (val r = attr.resolution) {
                    is SlotResolution.Empty -> Text(
                        "(empty)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SlotResolution.Missing -> Text(
                        "${r.essenceName} (missing — slot will be empty)",
                        color = MaterialTheme.colorScheme.error,
                    )
                    is SlotResolution.Resolved -> {
                        Text("Essence: ${r.essence.name}")
                        r.abilities.forEach { ability ->
                            when (ability) {
                                is RefResolution.Resolved<*> -> Text("  • ${ability.name}")
                                is RefResolution.Missing -> Text(
                                    "  • ${ability.name} (missing — will be dropped)",
                                    color = MaterialTheme.colorScheme.error,
                                    textDecoration = TextDecoration.LineThrough,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.preview.racials.isNotEmpty()) {
                Text("Racial Abilities", style = MaterialTheme.typography.titleSmall)
                state.preview.racials.forEach { racial ->
                    when (racial) {
                        is RefResolution.Resolved<*> -> Text("  • ${racial.name}")
                        is RefResolution.Missing -> Text(
                            "  • ${racial.name} (missing — will be dropped)",
                            color = MaterialTheme.colorScheme.error,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.preview.missingCount > 0) {
                Text(
                    "${state.preview.missingCount} references missing — they will be dropped.",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = !blank && !collides,
                ) { Text("Save") }
            }
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

private fun sampleManifestation(name: String): Essence.Manifestation = Essence.Manifestation(
    name = name,
    rank = Rank.Iron,
    rarity = Rarity.Common,
    properties = listOf(Property.Magic),
    description = "Manifested essence of $name",
    isRestricted = false,
)

private fun sampleConfluence(): Essence.Confluence = Essence.of(
    "Storm",
    restricted = false,
    ConfluenceSet(
        sampleManifestation("Water"),
        sampleManifestation("Wind"),
        sampleManifestation("Lightning"),
    ),
)

private fun sampleForm(): CharacterBuildContributionsViewModel.FormState =
    CharacterBuildContributionsViewModel.FormState(
        name = "Pyro Sentinel",
        race = "Human",
        racialAbilities = emptyList(),
        attributes = mapOf(
            Slot.Power to SlotState(essence = sampleConfluence(), abilities = emptyList()),
            Slot.Speed to SlotState(essence = sampleManifestation("Fire"), abilities = emptyList()),
            Slot.Spirit to SlotState(essence = sampleManifestation("Earth"), abilities = emptyList()),
            Slot.Recovery to SlotState(essence = sampleManifestation("Stone"), abilities = emptyList()),
        ),
    )

@PreviewLightDark
@Composable
private fun CenteredTextPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        CenteredText("Loading")
    }
}

@PreviewLightDark
@Composable
private fun CollapsibleCardExpandedPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        CollapsibleCard(title = "Power — Fire (3/5)", startExpanded = true) {
            Text("Slot content goes here.")
        }
    }
}

@PreviewLightDark
@Composable
private fun CollapsibleCardCollapsedPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        CollapsibleCard(title = "Speed — (no essence)", startExpanded = false) {
            Text("Hidden content.")
        }
    }
}

@PreviewLightDark
@Composable
private fun ChipFlowPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        ChipFlow(
            items = listOf("Flame Bolt", "Cinder Trail", "Conflagration"),
            label = { it },
            onRemove = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ConfluenceWarningDialogPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        ConfluenceWarningDialog(
            slot = Slot.Power,
            form = sampleForm(),
            onSaveAsCombination = {},
            onChangeConfluence = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EssencePromptDialogPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        EssencePromptDialog(
            prompt = EssenceChangePrompt(slot = Slot.Power, target = sampleManifestation("Water")),
            onClearAbilities = {},
            onKeepAbilities = {},
            onCancel = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ConfluenceSetPickerDialogPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        ConfluenceSetPickerDialog(
            prompt = CharacterBuildContributionsViewModel.ConfluenceSetPrompt(
                slot = Slot.Recovery,
                confluence = sampleConfluence(),
                sets = listOf(
                    ConfluenceSet(
                        sampleManifestation("Water"),
                        sampleManifestation("Wind"),
                        sampleManifestation("Lightning"),
                    ),
                    ConfluenceSet(
                        sampleManifestation("Ice"),
                        sampleManifestation("Wind"),
                        sampleManifestation("Cloud"),
                    ),
                ),
            ),
            onPick = {},
            onCancel = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun SaveCombinationDialogPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SaveCombinationDialog(
            prompt = CharacterBuildContributionsViewModel.SaveCombinationPrompt(
                slot = Slot.Power,
                confluence = sampleConfluence(),
                combination = listOf(
                    sampleManifestation("Fire"),
                    sampleManifestation("Earth"),
                    sampleManifestation("Stone"),
                ),
            ),
            onConfirm = {},
            onSkip = {},
            onCancel = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun FormCreatePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(
            isEdit = false,
            startCollapsed = false,
            form = sampleForm(),
            availableEssences = listOf(
                sampleManifestation("Fire"),
                sampleManifestation("Water"),
                sampleManifestation("Wind"),
            ),
            saveState = SaveState.Idle,
            warningSlot = null,
            onShowWarning = {},
            essencePickerSlot = null,
            onEssencePickerSlotChange = {},
            callbacks = stubFormCallbacks(),
            onImportFromFile = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun FormEditWithWarningPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(
            isEdit = true,
            startCollapsed = true,
            form = sampleForm(),
            availableEssences = emptyList(),
            saveState = SaveState.Idle,
            warningSlot = Slot.Power,
            onShowWarning = {},
            essencePickerSlot = null,
            onEssencePickerSlotChange = {},
            callbacks = stubFormCallbacks(),
            onImportFromFile = null,
        )
    }
}

@PreviewLightDark
@Composable
private fun FormErrorPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(
            isEdit = false,
            startCollapsed = true,
            form = sampleForm(),
            availableEssences = emptyList(),
            saveState = SaveState.Error("A build named \"Pyro Sentinel\" already exists."),
            warningSlot = null,
            onShowWarning = {},
            essencePickerSlot = null,
            onEssencePickerSlotChange = {},
            callbacks = stubFormCallbacks(),
            onImportFromFile = {},
        )
    }
}
