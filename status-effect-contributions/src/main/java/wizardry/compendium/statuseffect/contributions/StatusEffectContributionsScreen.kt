package wizardry.compendium.statuseffect.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.DeleteContributionButton

// ──────────────────────────────────────────────────────────────────────────────
// File-scope constants
// ──────────────────────────────────────────────────────────────────────────────

private val AfflictionSubtypes: List<StatusType.Affliction> = listOf(
    StatusType.Affliction.Curse,
    StatusType.Affliction.Disease,
    StatusType.Affliction.Elemental,
    StatusType.Affliction.Holy,
    StatusType.Affliction.Magic,
    StatusType.Affliction.Poison,
    StatusType.Affliction.Unholy,
    StatusType.Affliction.Wound,
    StatusType.Affliction.UnTyped,
)

private val BoonSubtypes: List<StatusType.Boon> = listOf(
    StatusType.Boon.Holy,
    StatusType.Boon.Magic,
    StatusType.Boon.Unholy,
    StatusType.Boon.UnTyped,
)

private val AllProperties: List<Property> by lazy {
    val klass: kotlin.reflect.KClass<Property> = Property::class
    klass.sealedSubclasses.mapNotNull { it.objectInstance }
}

private fun subtypeLabel(t: StatusType): String = when (t) {
    StatusType.Affliction.Curse -> "Curse"
    StatusType.Affliction.Disease -> "Disease"
    StatusType.Affliction.Elemental -> "Elemental"
    StatusType.Affliction.Holy, StatusType.Boon.Holy -> "Holy"
    StatusType.Affliction.Magic, StatusType.Boon.Magic -> "Magic"
    StatusType.Affliction.Poison -> "Poison"
    StatusType.Affliction.Unholy, StatusType.Boon.Unholy -> "Unholy"
    StatusType.Affliction.Wound -> "Wound"
    StatusType.Affliction.UnTyped, StatusType.Boon.UnTyped -> "Untyped"
}

private enum class TopLevel { Affliction, Boon }

// ──────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusEffectContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    onPasteImport: (text: String) -> Pair<StatusEffect?, String?> = { null to null },
    viewModel: StatusEffectContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            is StatusEffectContributionsViewModel.SaveState.Deleted -> onContributionDeleted()
            is StatusEffectContributionsViewModel.SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    var importedInitial by remember { mutableStateOf<StatusEffect?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf<String?>(null) }
    var pasteText by remember { mutableStateOf("") }

    when (val current = mode) {
        StatusEffectContributionsViewModel.Mode.Create -> {
            StatusEffectForm(
                initial = importedInitial,
                isEdit = false,
                saveState = saveState,
                onSave = { name, type, properties, stackable, description ->
                    viewModel.save(name, type, properties, stackable, description)
                },
                onDelete = {},
                onImportClick = {
                    pasteText = ""
                    showImportDialog = true
                },
            )
        }
        StatusEffectContributionsViewModel.Mode.Edit.Loading -> Loading()
        StatusEffectContributionsViewModel.Mode.Edit.NotFound -> NotFound()
        is StatusEffectContributionsViewModel.Mode.Edit.Ready -> {
            StatusEffectForm(
                initial = current.effect,
                isEdit = true,
                saveState = saveState,
                onSave = { name, type, properties, stackable, description ->
                    viewModel.save(name, type, properties, stackable, description)
                },
                onDelete = viewModel::deleteContribution,
                onImportClick = null,
            )
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Status Effect") },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text("Paste a single-effect share") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = {
                    val (effect, error) = onPasteImport(pasteText)
                    showImportDialog = false
                    if (effect != null) {
                        importedInitial = effect
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

// ──────────────────────────────────────────────────────────────────────────────
// Slot composables
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading")
    }
}

@Composable
private fun NotFound() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("This status effect is not a user contribution and cannot be edited.")
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Form
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusEffectForm(
    initial: StatusEffect?,
    isEdit: Boolean,
    saveState: StatusEffectContributionsViewModel.SaveState,
    onSave: (name: String, type: StatusType, properties: List<Property>, stackable: Boolean, description: String) -> Unit,
    onDelete: () -> Unit,
    onImportClick: (() -> Unit)? = null,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }

    val initialTopLevel = remember(initial) {
        if (initial?.type is StatusType.Boon) TopLevel.Boon else TopLevel.Affliction
    }
    var topLevel by remember(initial) { mutableStateOf(initialTopLevel) }

    var afflictionSub by remember(initial) {
        mutableStateOf(
            (initial?.type as? StatusType.Affliction) ?: StatusType.Affliction.Curse
        )
    }
    var boonSub by remember(initial) {
        mutableStateOf(
            (initial?.type as? StatusType.Boon) ?: StatusType.Boon.Holy
        )
    }

    var stackable by remember(initial) { mutableStateOf(initial?.stackable ?: false) }
    var description by remember(initial) { mutableStateOf(initial?.description.orEmpty()) }
    var properties by remember(initial) {
        mutableStateOf(initial?.properties?.toSet() ?: emptySet())
    }

    val saving = saveState is StatusEffectContributionsViewModel.SaveState.Saving
    val canSave = name.isNotBlank() && !saving

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
            label = { Text("Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isEdit,
        )

        TypePicker(
            topLevel = topLevel,
            onTopLevelChange = { topLevel = it },
            afflictionSub = afflictionSub,
            onAfflictionSubChange = { afflictionSub = it },
            boonSub = boonSub,
            onBoonSubChange = { boonSub = it },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Stackable", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = stackable, onCheckedChange = { stackable = it })
        }

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        PropertiesPicker(
            selected = properties,
            onToggle = { property ->
                properties = if (property in properties) properties - property
                else properties + property
            },
        )

        ContributionErrorFeedback(
            error = (saveState as? StatusEffectContributionsViewModel.SaveState.Error)?.message,
        )

        Button(
            onClick = {
                val type = when (topLevel) {
                    TopLevel.Affliction -> afflictionSub
                    TopLevel.Boon -> boonSub
                }
                onSave(name, type, properties.toList(), stackable, description)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Status Effect" else "Save Status Effect")
        }

        if (isEdit) {
            DeleteContributionButton(
                name = initial?.name.orEmpty(),
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

// ──────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ──────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypePicker(
    topLevel: TopLevel,
    onTopLevelChange: (TopLevel) -> Unit,
    afflictionSub: StatusType.Affliction,
    onAfflictionSubChange: (StatusType.Affliction) -> Unit,
    boonSub: StatusType.Boon,
    onBoonSubChange: (StatusType.Boon) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Type", style = MaterialTheme.typography.labelLarge)

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = topLevel == TopLevel.Affliction,
                onClick = { onTopLevelChange(TopLevel.Affliction) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Affliction") }
            SegmentedButton(
                selected = topLevel == TopLevel.Boon,
                onClick = { onTopLevelChange(TopLevel.Boon) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Boon") }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (topLevel) {
                TopLevel.Affliction -> AfflictionSubtypes.forEach { sub ->
                    FilterChip(
                        selected = afflictionSub == sub,
                        onClick = { onAfflictionSubChange(sub) },
                        label = { Text(subtypeLabel(sub)) },
                    )
                }
                TopLevel.Boon -> BoonSubtypes.forEach { sub ->
                    FilterChip(
                        selected = boonSub == sub,
                        onClick = { onBoonSubChange(sub) },
                        label = { Text(subtypeLabel(sub)) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesPicker(
    selected: Set<Property>,
    onToggle: (Property) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Properties", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AllProperties.forEach { property ->
                FilterChip(
                    selected = property in selected,
                    onClick = { onToggle(property) },
                    label = { Text(property.toString()) },
                )
            }
        }
    }
}
