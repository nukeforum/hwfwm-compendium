package wizardry.compendium.essence.contributions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.ContributionDropdown
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.ContributionReportCard
import wizardry.compendium.ui.DeleteContributionButton
import wizardry.compendium.ui.EditPreviewToggle
import kotlinx.coroutines.launch

@Composable
fun EssenceContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    /**
     * Decode a paste-buffer into a single manifestation, or surface an
     * error message. Tab-aware: only fires on the Manifestation tab in
     * Create mode; Confluence import is deferred (the manifestation
     * resolution problem is out of scope for the contribute pre-fill).
     */
    onPasteImport: (text: String) -> Pair<Essence.Manifestation?, String?> = { null to null },
    viewModel: EssenceContributionsViewModel = hiltViewModel(),
) {
    val availableManifestations by viewModel.availableManifestations.collectAsState()
    val availableConfluences by viewModel.availableConfluences.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            is EssenceContributionsViewModel.SaveState.Deleted -> onContributionDeleted()
            is EssenceContributionsViewModel.SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    var importedManifestation by remember { mutableStateOf<Essence.Manifestation?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var pasteText by remember { mutableStateOf("") }

    when (val current = mode) {
        EssenceContributionsViewModel.Mode.Edit.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("Loading") }
        EssenceContributionsViewModel.Mode.Edit.NotFound -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("This essence is not a user contribution and cannot be edited.") }
        is EssenceContributionsViewModel.Mode.Edit.ManifestationReady -> ManifestationForm(
            initial = current.manifestation,
            isEdit = true,
            saveState = saveState,
            onSave = { name, rarity, description, isRestricted ->
                viewModel.saveManifestation(name, rarity, description, isRestricted)
            },
            onDelete = viewModel::deleteContribution,
            onImportClick = null,
        )
        is EssenceContributionsViewModel.Mode.Edit.ConfluenceReady -> ConfluenceEditForm(
            initial = current.confluence,
            saveState = saveState,
            onSave = { name, isRestricted -> viewModel.updateConfluence(name, isRestricted) },
            onDelete = viewModel::deleteContribution,
        )
        EssenceContributionsViewModel.Mode.Create -> CreateContributions(
            availableManifestations = availableManifestations,
            availableConfluences = availableConfluences,
            saveState = saveState,
            manifestationInitial = importedManifestation,
            onSaveManifestation = { name, rarity, description, isRestricted ->
                viewModel.saveManifestation(name, rarity, description, isRestricted)
            },
            onSaveNewConfluence = { name, m1, m2, m3, isRestricted ->
                viewModel.saveConfluence(name, m1, m2, m3, isRestricted)
            },
            onAddCombination = { target, m1, m2, m3, isRestricted ->
                viewModel.addCombinationToConfluence(target, m1, m2, m3, isRestricted)
            },
            onClearState = viewModel::clearSaveState,
            onManifestationImportClick = {
                pasteText = ""
                showImportDialog = true
            },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Essence") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("Paste a single-essence share") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val (manifestation, error) = onPasteImport(pasteText)
                    showImportDialog = false
                    if (manifestation != null) {
                        importedManifestation = manifestation
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
private fun CreateContributions(
    availableManifestations: List<Essence.Manifestation>,
    availableConfluences: List<Essence.Confluence>,
    saveState: EssenceContributionsViewModel.SaveState,
    manifestationInitial: Essence.Manifestation?,
    onSaveManifestation: (String, Rarity, String, Boolean) -> Unit,
    onSaveNewConfluence: (String, Essence.Manifestation, Essence.Manifestation, Essence.Manifestation, Boolean) -> Unit,
    onAddCombination: (Essence.Confluence, Essence.Manifestation, Essence.Manifestation, Essence.Manifestation, Boolean) -> Unit,
    onClearState: () -> Unit,
    onManifestationImportClick: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
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
                initial = manifestationInitial,
                isEdit = false,
                saveState = saveState,
                onSave = onSaveManifestation,
                onDelete = {},
                onImportClick = onManifestationImportClick,
            )
            1 -> ConfluenceForm(
                availableManifestations = availableManifestations,
                availableConfluences = availableConfluences,
                saveState = saveState,
                onSaveNew = onSaveNewConfluence,
                onAddCombination = onAddCombination,
                onClearState = onClearState,
            )
        }
    }
}

@Composable
private fun ConfluenceEditForm(
    initial: Essence.Confluence,
    saveState: EssenceContributionsViewModel.SaveState,
    onSave: (name: String, isRestricted: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val name = initial.name
    var isRestricted by remember(initial) { mutableStateOf(initial.isRestricted) }

    val saving = saveState is EssenceContributionsViewModel.SaveState.Saving

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {},
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Restricted")
            Switch(checked = isRestricted, onCheckedChange = { isRestricted = it })
        }

        Text("Combinations", style = MaterialTheme.typography.titleMedium)
        initial.confluenceSets.forEach { set ->
            Text("• ${set.set.joinToString(" + ") { it.name }}${if (set.isRestricted) " (restricted)" else ""}")
        }

        ContributionErrorFeedback(
            error = (saveState as? EssenceContributionsViewModel.SaveState.Error)?.message,
        )

        Button(
            onClick = { onSave(name, isRestricted) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
        ) { Text("Update Confluence") }

        DeleteContributionButton(
            name = name,
            enabled = !saving,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ManifestationForm(
    initial: Essence.Manifestation?,
    isEdit: Boolean,
    saveState: EssenceContributionsViewModel.SaveState,
    onSave: (name: String, rarity: Rarity, description: String, isRestricted: Boolean) -> Unit,
    onDelete: () -> Unit,
    onImportClick: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var rarity by remember(initial) { mutableStateOf(initial?.rarity ?: Rarity.Common) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var isRestricted by remember(initial) { mutableStateOf(initial?.isRestricted ?: false) }
    var preview by rememberSaveable { mutableStateOf(false) }

    val saving = saveState is EssenceContributionsViewModel.SaveState.Saving
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
        } else {
            ContributionReportCard(
                text = manifestationPreviewText(name, rarity, description, isRestricted),
            )
        }

        ContributionErrorFeedback(
            error = (saveState as? EssenceContributionsViewModel.SaveState.Error)?.message,
        )

        Button(
            onClick = { onSave(name, rarity, description, isRestricted) },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Essence" else "Save Essence")
        }

        if (isEdit) {
            DeleteContributionButton(
                name = name,
                enabled = !saving,
                onDelete = onDelete,
            )
        }

        if (!isEdit && onImportClick != null) {
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import from Share") }
        }
    }
}

private enum class ConfluenceMode { New, AddCombination }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfluenceForm(
    availableManifestations: List<Essence.Manifestation>,
    availableConfluences: List<Essence.Confluence>,
    saveState: EssenceContributionsViewModel.SaveState,
    onSaveNew: (name: String, m1: Essence.Manifestation, m2: Essence.Manifestation, m3: Essence.Manifestation, isRestricted: Boolean) -> Unit,
    onAddCombination: (target: Essence.Confluence, m1: Essence.Manifestation, m2: Essence.Manifestation, m3: Essence.Manifestation, isRestricted: Boolean) -> Unit,
    onClearState: () -> Unit,
) {
    var mode by remember { mutableStateOf(ConfluenceMode.New) }
    var name by remember { mutableStateOf("") }
    var targetConfluence by remember { mutableStateOf<Essence.Confluence?>(null) }
    var isRestricted by remember { mutableStateOf(false) }
    var manifestation1 by remember { mutableStateOf<Essence.Manifestation?>(null) }
    var manifestation2 by remember { mutableStateOf<Essence.Manifestation?>(null) }
    var manifestation3 by remember { mutableStateOf<Essence.Manifestation?>(null) }

    var activeSlot by remember { mutableIntStateOf(0) }
    var filterQuery by remember { mutableStateOf("") }
    var showManifestationSheet by remember { mutableStateOf(false) }
    var showConfluenceSheet by remember { mutableStateOf(false) }
    val manifestationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val confluenceSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun openManifestationSheet(slot: Int) {
        activeSlot = slot
        filterQuery = ""
        showManifestationSheet = true
    }

    fun openConfluenceSheet() {
        filterQuery = ""
        showConfluenceSheet = true
    }

    fun selectManifestation(manifestation: Essence.Manifestation) {
        when (activeSlot) {
            1 -> manifestation1 = manifestation
            2 -> manifestation2 = manifestation
            3 -> manifestation3 = manifestation
        }
        scope.launch { manifestationSheetState.hide() }
            .invokeOnCompletion { showManifestationSheet = false }
    }

    fun selectConfluence(confluence: Essence.Confluence) {
        targetConfluence = confluence
        scope.launch { confluenceSheetState.hide() }
            .invokeOnCompletion { showConfluenceSheet = false }
    }

    if (showManifestationSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManifestationSheet = false },
            sheetState = manifestationSheetState,
        ) {
            ManifestationPickerSheet(
                query = filterQuery,
                onQueryChange = { filterQuery = it },
                options = availableManifestations,
                onSelected = { selectManifestation(it) },
            )
        }
    }

    if (showConfluenceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConfluenceSheet = false },
            sheetState = confluenceSheetState,
        ) {
            ConfluencePickerSheet(
                query = filterQuery,
                onQueryChange = { filterQuery = it },
                options = availableConfluences,
                onSelected = { selectConfluence(it) },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModeSelector(
            mode = mode,
            onModeChange = {
                mode = it
                onClearState()
            },
        )

        when (mode) {
            ConfluenceMode.New -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Confluence Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            ConfluenceMode.AddCombination -> {
                OutlinedButton(
                    onClick = { openConfluenceSheet() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(targetConfluence?.name ?: "Select Confluence")
                }
            }
        }

        ManifestationButton(
            label = "Essence 1",
            selected = manifestation1,
            onClick = { openManifestationSheet(1) },
        )

        ManifestationButton(
            label = "Essence 2",
            selected = manifestation2,
            onClick = { openManifestationSheet(2) },
        )

        ManifestationButton(
            label = "Essence 3",
            selected = manifestation3,
            onClick = { openManifestationSheet(3) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Restricted")
            Switch(checked = isRestricted, onCheckedChange = { isRestricted = it })
        }

        ContributionErrorFeedback(
            error = (saveState as? EssenceContributionsViewModel.SaveState.Error)?.message,
        )

        val m1 = manifestation1
        val m2 = manifestation2
        val m3 = manifestation3
        val manifestationsValid = m1 != null && m2 != null && m3 != null &&
            m1.name != m2.name && m1.name != m3.name && m2.name != m3.name
        val notSaving = saveState !is EssenceContributionsViewModel.SaveState.Saving
        val canSave = manifestationsValid && notSaving && when (mode) {
            ConfluenceMode.New -> name.isNotBlank()
            ConfluenceMode.AddCombination -> targetConfluence != null
        }
        Button(
            onClick = {
                when (mode) {
                    ConfluenceMode.New -> onSaveNew(
                        name,
                        manifestation1!!,
                        manifestation2!!,
                        manifestation3!!,
                        isRestricted,
                    )
                    ConfluenceMode.AddCombination -> onAddCombination(
                        targetConfluence!!,
                        manifestation1!!,
                        manifestation2!!,
                        manifestation3!!,
                        isRestricted,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(
                when (mode) {
                    ConfluenceMode.New -> "Save Confluence"
                    ConfluenceMode.AddCombination -> "Add Combination"
                }
            )
        }
    }
}

@Composable
private fun ModeSelector(
    mode: ConfluenceMode,
    onModeChange: (ConfluenceMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ConfluenceMode.New,
            onClick = { onModeChange(ConfluenceMode.New) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("New Confluence")
        }
        SegmentedButton(
            selected = mode == ConfluenceMode.AddCombination,
            onClick = { onModeChange(ConfluenceMode.AddCombination) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Add Combination")
        }
    }
}

@Composable
private fun ManifestationButton(
    label: String,
    selected: Essence.Manifestation?,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(selected?.name ?: label)
    }
}

@Composable
private fun ManifestationPickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<Essence.Manifestation>,
    onSelected: (Essence.Manifestation) -> Unit,
) {
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Filter") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            items(filtered) { manifestation ->
                DropdownMenuItem(
                    text = { Text(manifestation.name) },
                    onClick = { onSelected(manifestation) },
                )
            }
        }
    }
}

@Composable
private fun ConfluencePickerSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    options: List<Essence.Confluence>,
    onSelected: (Essence.Confluence) -> Unit,
) {
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else options.filter { it.name.contains(query, ignoreCase = true) }
    }
    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Filter") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
            items(filtered) { confluence ->
                DropdownMenuItem(
                    text = { Text(confluence.name) },
                    onClick = { onSelected(confluence) },
                )
            }
        }
    }
}

private fun manifestationPreviewText(
    name: String,
    rarity: Rarity,
    description: String,
    isRestricted: Boolean,
): String = buildString {
    append("Item: [${name.ifBlank { "(unnamed)" }} Essence]\n")
    append("(${rarity.name.lowercase()})")
    if (isRestricted) append(" — restricted")
    append("\n\n")
    append(description.ifBlank { "(no description)" })
}

// region Previews

private val previewManifestations = listOf(
    Essence.of("Wind", "A flowing breath of air", Rarity.Common, false),
    Essence.of("Blood", "The vital essence of life", Rarity.Uncommon, false),
    Essence.of("Sin", "Manifested essence of transgression", Rarity.Legendary, false),
    Essence.of("Dark", "A creeping, consuming darkness", Rarity.Rare, false),
)

private val previewConfluences = listOf(
    Essence.of(
        name = "Doom",
        restricted = false,
        ConfluenceSet(previewManifestations[1], previewManifestations[2], previewManifestations[3]),
    ),
    Essence.of(
        name = "Tempest",
        restricted = false,
        ConfluenceSet(previewManifestations[0], previewManifestations[1], previewManifestations[3]),
    ),
)

@Preview(showBackground = true)
@Composable
private fun ManifestationFormIdlePreview() {
    ManifestationForm(
        initial = null,
        isEdit = false,
        saveState = EssenceContributionsViewModel.SaveState.Idle,
        onSave = { _, _, _, _ -> },
        onDelete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ManifestationFormErrorPreview() {
    ManifestationForm(
        initial = null,
        isEdit = false,
        saveState = EssenceContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onSave = { _, _, _, _ -> },
        onDelete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceFormNewPreview() {
    ConfluenceForm(
        availableManifestations = previewManifestations,
        availableConfluences = previewConfluences,
        saveState = EssenceContributionsViewModel.SaveState.Idle,
        onSaveNew = { _, _, _, _, _ -> },
        onAddCombination = { _, _, _, _, _ -> },
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceFormDuplicateErrorPreview() {
    ConfluenceForm(
        availableManifestations = previewManifestations,
        availableConfluences = previewConfluences,
        saveState = EssenceContributionsViewModel.SaveState.Error("That combination already produces Doom"),
        onSaveNew = { _, _, _, _, _ -> },
        onAddCombination = { _, _, _, _, _ -> },
        onClearState = {},
    )
}

// endregion
