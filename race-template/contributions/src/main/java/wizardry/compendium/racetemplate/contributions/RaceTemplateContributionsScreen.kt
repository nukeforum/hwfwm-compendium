package wizardry.compendium.racetemplate.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.racetemplate.contributions.RaceTemplateContributionsViewModel.FormState
import wizardry.compendium.racetemplate.contributions.RaceTemplateContributionsViewModel.Mode
import wizardry.compendium.racetemplate.contributions.RaceTemplateContributionsViewModel.SaveState
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.ui.ContributionErrorFeedback
import wizardry.compendium.ui.DeleteContributionButton
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.SearchableSelectionSheet
import wizardry.compendium.ui.theme.CompendiumTheme

@Composable
fun RaceTemplateContributionsScreen(
    onContributionSaved: () -> Unit = {},
    onContributionDeleted: () -> Unit = {},
    viewModel: RaceTemplateContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val form by viewModel.formState.collectAsState()

    LaunchedEffect(saveState) {
        when (saveState) {
            SaveState.Deleted -> onContributionDeleted()
            SaveState.Success -> onContributionSaved()
            else -> {}
        }
    }

    val callbacks = rememberFormCallbacks(viewModel)
    when (mode) {
        Mode.Create -> Form(isEdit = false, form = form, saveState = saveState, callbacks = callbacks)
        Mode.Edit.Loading -> CenteredText("Loading")
        Mode.Edit.NotFound -> CenteredText("This race template is not a user contribution and cannot be edited.")
        is Mode.Edit.Ready -> Form(isEdit = true, form = form, saveState = saveState, callbacks = callbacks)
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}

private data class FormCallbacks(
    val onNameChange: (String) -> Unit,
    val onAddRacialAbility: (Ability.Listing) -> Unit,
    val onRemoveRacialAbility: (String) -> Unit,
    val racialAbilityCandidates: () -> List<Ability.Listing>,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun rememberFormCallbacks(viewModel: RaceTemplateContributionsViewModel): FormCallbacks =
    remember(viewModel) {
        FormCallbacks(
            onNameChange = viewModel::setName,
            onAddRacialAbility = viewModel::addRacialAbility,
            onRemoveRacialAbility = viewModel::removeRacialAbility,
            racialAbilityCandidates = viewModel::racialAbilityCandidates,
            onSave = viewModel::save,
            onDelete = viewModel::deleteContribution,
        )
    }

@Composable
private fun Form(
    isEdit: Boolean,
    form: FormState,
    saveState: SaveState,
    callbacks: FormCallbacks,
) {
    val required = RaceTemplate.RACIAL_ABILITY_COUNT
    val saving = saveState is SaveState.Saving
    val canSave = form.name.isNotBlank() && form.isComplete && !saving

    var racialPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = callbacks.onNameChange,
            label = { Text("Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = isEdit,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Racial abilities (${form.racialAbilities.size}/$required)",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (form.isComplete) {
                        "Ready to save."
                    } else {
                        "Choose exactly $required racial abilities to save this template."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (form.isComplete) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                RacialAbilityChips(
                    items = form.racialAbilities,
                    onRemove = { callbacks.onRemoveRacialAbility(it.name) },
                )
                OutlinedButton(
                    onClick = { racialPickerOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = form.racialAbilities.size < required,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(text = "  Add racial ability")
                }
            }
        }

        ContributionErrorFeedback(error = (saveState as? SaveState.Error)?.message)

        Button(
            onClick = callbacks.onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave,
        ) {
            Text(if (isEdit) "Update Race Template" else "Save Race Template")
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
            maxSelections = required - form.racialAbilities.size,
            labelOf = { it.name },
            onDismiss = { racialPickerOpen = false },
            onConfirm = { picks ->
                picks.forEach { callbacks.onAddRacialAbility(it) }
                racialPickerOpen = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RacialAbilityChips(
    items: List<Ability.Listing>,
    onRemove: (Ability.Listing) -> Unit,
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
                label = { Text(item.name) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${item.name}",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        }
    }
}

private fun stubFormCallbacks(): FormCallbacks = FormCallbacks(
    onNameChange = {},
    onAddRacialAbility = {},
    onRemoveRacialAbility = {},
    racialAbilityCandidates = { emptyList() },
    onSave = {},
    onDelete = {},
)

private fun sampleForm(complete: Boolean): FormState = FormState(
    name = "Runic Golem",
    racialAbilities = (1..(if (complete) 6 else 2)).map { Ability.Listing(name = "Racial $it", effects = emptyList()) },
)

@PreviewLightDark
@Composable
private fun FormCreateIncompletePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(isEdit = false, form = sampleForm(complete = false), saveState = SaveState.Idle, callbacks = stubFormCallbacks())
    }
}

@PreviewLightDark
@Composable
private fun FormEditCompletePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(isEdit = true, form = sampleForm(complete = true), saveState = SaveState.Idle, callbacks = stubFormCallbacks())
    }
}

@PreviewLightDark
@Composable
private fun FormErrorPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Form(
            isEdit = false,
            form = sampleForm(complete = false),
            saveState = SaveState.Error("A race template needs exactly 6 racial abilities (currently 2)."),
            callbacks = stubFormCallbacks(),
        )
    }
}
