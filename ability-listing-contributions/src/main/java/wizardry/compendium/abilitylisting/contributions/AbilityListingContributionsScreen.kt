package wizardry.compendium.abilitylisting.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import wizardry.compendium.ability.preview.AbilityPreview
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.DescriptionSegment
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Resource
import wizardry.compendium.essences.model.parseDescription
import kotlin.time.Duration

@Composable
fun AbilityListingContributionsScreen(
    onContributionDeleted: () -> Unit = {},
    viewModel: AbilityListingContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    val effects by viewModel.effects.collectAsState()
    val mode by viewModel.mode.collectAsState()

    LaunchedEffect(saveState) {
        if (saveState is AbilityListingContributionsViewModel.SaveState.Deleted) {
            onContributionDeleted()
        }
    }

    when (val current = mode) {
        AbilityListingContributionsViewModel.Mode.Edit.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("Loading") }
        AbilityListingContributionsViewModel.Mode.Edit.NotFound -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("This ability listing is not a user contribution and cannot be edited.") }
        AbilityListingContributionsViewModel.Mode.Create -> AbilityListingForm(
            initialName = null,
            isEdit = false,
            effects = effects,
            saveState = saveState,
            onUpdateEffect = viewModel::updateEffect,
            onRemoveEffect = viewModel::removeEffect,
            onAppendEffect = viewModel::appendEffect,
            onSave = viewModel::saveAbilityListing,
            onDelete = {},
            onClearState = viewModel::clearSaveState,
        )
        is AbilityListingContributionsViewModel.Mode.Edit.Ready -> AbilityListingForm(
            initialName = current.listing.name,
            isEdit = true,
            effects = effects,
            saveState = saveState,
            onUpdateEffect = viewModel::updateEffect,
            onRemoveEffect = viewModel::removeEffect,
            onAppendEffect = viewModel::appendEffect,
            onSave = viewModel::saveAbilityListing,
            onDelete = viewModel::deleteContribution,
            onClearState = viewModel::clearSaveState,
        )
    }
}

@Composable
private fun AbilityListingForm(
    initialName: String?,
    isEdit: Boolean,
    effects: List<EffectDraft>,
    saveState: AbilityListingContributionsViewModel.SaveState,
    onUpdateEffect: (Int, (EffectDraft) -> EffectDraft) -> Unit,
    onRemoveEffect: (Int) -> Unit,
    onAppendEffect: () -> Unit,
    onSave: (name: String) -> Unit,
    onDelete: () -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete contribution?") },
            text = { Text("Permanently delete \"$name\" from your contributions?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !preview,
                onClick = { preview = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Edit") }
            SegmentedButton(
                selected = preview,
                onClick = { preview = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Preview") }
        }

        if (!preview) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = isEdit,
            )

            effects.forEachIndexed { index, draft ->
                AbilityEffectContribution(
                    index = index,
                    draft = draft,
                    onUpdate = { transform -> onUpdateEffect(index, transform) },
                    onRemove = { onRemoveEffect(index) },
                )
            }

            AppendEffectButton(onClick = onAppendEffect)
        } else {
            AbilityPreview(
                ability = Ability.Listing(
                    name = name.ifBlank { "(unnamed)" },
                    effects = effects.toPreviewEffects(),
                ),
            )
        }

        SaveFeedback(saveState = saveState, onClearState = onClearState)

        Button(
            onClick = { onSave(name) },
            modifier = Modifier.fillMaxWidth(),
            enabled = saveState !is AbilityListingContributionsViewModel.SaveState.Saving,
        ) {
            Text(if (isEdit) "Update Ability Listing" else "Save Ability Listing")
        }

        if (isEdit) {
            androidx.compose.material3.OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = saveState !is AbilityListingContributionsViewModel.SaveState.Saving,
            ) {
                Text("Delete Contribution", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AppendEffectButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Add Effect")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AbilityEffectContribution(
    index: Int,
    draft: EffectDraft,
    onUpdate: ((EffectDraft) -> EffectDraft) -> Unit,
    onRemove: () -> Unit,
) {
    var showPropertySheet by remember { mutableStateOf(false) }
    var showCostSheet by remember { mutableStateOf(false) }
    val propertySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val costSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showPropertySheet) {
        ModalBottomSheet(
            onDismissRequest = { showPropertySheet = false },
            sheetState = propertySheetState,
        ) {
            PropertyPickerSheet(
                selected = draft.properties,
                onToggle = { property ->
                    onUpdate { d ->
                        if (property in d.properties) d.copy(properties = d.properties - property)
                        else d.copy(properties = d.properties + property)
                    }
                },
            )
        }
    }

    if (showCostSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCostSheet = false },
            sheetState = costSheetState,
        ) {
            CostPickerSheet(
                onAdd = { cost ->
                    onUpdate { d -> d.copy(costs = d.costs + cost) }
                    scope.launch { costSheetState.hide() }
                        .invokeOnCompletion { showCostSheet = false }
                },
            )
        }
    }

    var expanded by rememberSaveable(index) { mutableStateOf(true) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Effect ${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse effect" else "Expand effect",
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove effect")
                    }
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EnumDropdown(
                        label = "Rank *",
                        options = Rank.entries,
                        selected = draft.rank,
                        optionLabel = { it.name },
                        onSelected = { onUpdate { d -> d.copy(rank = it) } },
                        modifier = Modifier.weight(1f),
                    )

                    EnumDropdown(
                        label = "Type *",
                        options = AbilityTypeOptions,
                        selected = draft.type,
                        optionLabel = { it.toString() },
                        onSelected = { onUpdate { d -> d.copy(type = it) } },
                        modifier = Modifier.weight(1f),
                    )
                }

                PropertiesField(
                    properties = draft.properties,
                    onAddClick = { showPropertySheet = true },
                    onRemove = { property ->
                        onUpdate { d -> d.copy(properties = d.properties - property) }
                    },
                )

                CostsField(
                    costs = draft.costs,
                    onAddClick = { showCostSheet = true },
                    onRemove = { cost ->
                        onUpdate { d -> d.copy(costs = d.costs - cost) }
                    },
                )

                CooldownField(
                    value = draft.cooldown,
                    onValueChange = { onUpdate { d -> d.copy(cooldown = it) } },
                )

                ReplacementKeyField(
                    value = draft.replacementKey,
                    onValueChange = { onUpdate { d -> d.copy(replacementKey = it) } },
                )

                DescriptionField(
                    description = draft.description,
                    costs = draft.costs,
                    cooldown = draft.cooldown,
                    onValueChange = { onUpdate { d -> d.copy(description = it) } },
                )
            } else {
                CollapsedEffectSummary(
                    description = draft.description,
                    costs = draft.costs,
                    cooldown = draft.cooldown,
                )
            }
        }
    }
}

@Composable
private fun CollapsedEffectSummary(
    description: String,
    costs: List<Cost>,
    cooldown: String,
) {
    val resolvedCooldown = remember(cooldown) {
        parseCooldown(cooldown)?.let { if (it == Duration.ZERO) "" else it.toString() }.orEmpty()
    }
    Text(
        text = descriptionAnnotated(
            description = description,
            costs = costs,
            cooldown = resolvedCooldown,
            resolvedColor = MaterialTheme.colorScheme.primary,
            errorColor = MaterialTheme.colorScheme.error,
            emptyPlaceholder = "(empty)",
        ),
    )
}

@Composable
private fun descriptionAnnotated(
    description: String,
    costs: List<Cost>,
    cooldown: String,
    resolvedColor: Color,
    errorColor: Color,
    emptyPlaceholder: String,
): AnnotatedString = remember(description, costs, cooldown, resolvedColor, errorColor, emptyPlaceholder) {
    if (description.isBlank()) {
        AnnotatedString(emptyPlaceholder)
    } else {
        buildAnnotatedString {
            for (segment in parseDescription(description, costs, cooldown)) {
                when (segment) {
                    is DescriptionSegment.Literal -> append(segment.text)
                    is DescriptionSegment.Resolved ->
                        withStyle(SpanStyle(color = resolvedColor, fontWeight = FontWeight.Medium)) {
                            append(segment.value)
                        }
                    is DescriptionSegment.Unresolved ->
                        withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Medium)) {
                            append(segment.token)
                        }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DescriptionField(
    description: String,
    costs: List<Cost>,
    cooldown: String,
    onValueChange: (String) -> Unit,
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(text = description, selection = TextRange(description.length)))
    }
    LaunchedEffect(description) {
        if (fieldValue.text != description) {
            fieldValue = fieldValue.copy(text = description, selection = TextRange(description.length))
        }
    }

    val resolvedCooldown = remember(cooldown) {
        parseCooldown(cooldown)?.let { if (it == Duration.ZERO) "" else it.toString() }.orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TokenChipRow(
            costs = costs,
            hasCooldown = resolvedCooldown.isNotBlank(),
            onInsert = { token ->
                val start = fieldValue.selection.start.coerceIn(0, fieldValue.text.length)
                val end = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
                val before = fieldValue.text.substring(0, start)
                val after = fieldValue.text.substring(end)
                val newText = before + token + after
                val cursor = before.length + token.length
                fieldValue = TextFieldValue(text = newText, selection = TextRange(cursor))
                onValueChange(newText)
            },
        )
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                if (it.text != description) onValueChange(it.text)
            },
            label = { Text("Description *") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenChipRow(
    costs: List<Cost>,
    hasCooldown: Boolean,
    onInsert: (String) -> Unit,
) {
    if (costs.isEmpty() && !hasCooldown) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasCooldown) {
            AssistChip(
                onClick = { onInsert("{cooldown}") },
                label = { Text("{cooldown}") },
            )
        }
        costs.forEachIndexed { i, _ ->
            val token = if (i == 0) "{cost}" else "{cost${i + 1}}"
            AssistChip(
                onClick = { onInsert(token) },
                label = { Text(token) },
            )
        }
    }
}

private fun List<EffectDraft>.toPreviewEffects(): List<Effect.AbilityEffect> = map { draft ->
    Effect.AbilityEffect(
        rank = draft.rank ?: Rank.Iron,
        type = draft.type ?: AbilityType.Spell,
        properties = draft.properties,
        cost = draft.costs.ifEmpty { listOf(Cost.None) },
        cooldown = parseCooldown(draft.cooldown) ?: Duration.ZERO,
        description = draft.description,
        replacementKey = draft.replacementKey.trim().takeIf { it.isNotEmpty() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel).orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = LocalContentColor.current,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertiesField(
    properties: List<Property>,
    onAddClick: () -> Unit,
    onRemove: (Property) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Properties *", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            properties.forEach { property ->
                InputChip(
                    selected = true,
                    onClick = { onRemove(property) },
                    label = { Text(property.toString()) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
                    },
                )
            }
            AssistChip(
                onClick = onAddClick,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostsField(
    costs: List<Cost>,
    onAddClick: () -> Unit,
    onRemove: (Cost) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Costs", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            costs.forEach { cost ->
                val color = resourceColor(cost.resource)
                InputChip(
                    selected = true,
                    onClick = { onRemove(cost) },
                    label = { Text(cost.toString()) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove")
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = color.copy(alpha = 0.2f),
                        selectedLabelColor = color,
                        selectedTrailingIconColor = color,
                    ),
                )
            }
            AssistChip(
                onClick = onAddClick,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplacementKeyField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Replacement Key") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = {
                RichTooltip(
                    title = { Text("Replacement Key") },
                ) {
                    Text(
                        "Used to supplant an effect with a higher-rank version when the ability ranks up. " +
                            "Effects sharing the same key replace earlier ones as the ability progresses."
                    )
                }
            },
            state = tooltipState,
        ) {
            IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                Text("?", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CooldownField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Cooldown") },
            placeholder = { Text("e.g. 30s") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
            tooltip = {
                RichTooltip(
                    title = { Text("Cooldown format") },
                ) {
                    Text(
                        "Enter a number followed by a unit:\n" +
                            "  s — seconds\n" +
                            "  min — minutes\n" +
                            "  h — hours\n" +
                            "  d — days\n" +
                            "  w — weeks\n" +
                            "  m — months\n" +
                            "  y — years\n" +
                            "Examples: 30s, 5min, 2h, 1d, 6m"
                    )
                }
            },
            state = tooltipState,
        ) {
            IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                Text("?", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropertyPickerSheet(
    selected: List<Property>,
    onToggle: (Property) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) {
        if (query.isBlank()) PropertyOptions
        else PropertyOptions.filter { it.toString().contains(query, ignoreCase = true) }
    }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filter") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            filtered.forEach { property ->
                val isSelected = property in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(property) },
                    label = { Text(property.toString()) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostPickerSheet(
    onAdd: (Cost) -> Unit,
) {
    var ongoing by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf<Amount?>(null) }
    var resource by remember { mutableStateOf<Resource?>(null) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kind", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !ongoing,
                onClick = { ongoing = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Upfront") }
            SegmentedButton(
                selected = ongoing,
                onClick = { ongoing = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("Ongoing") }
        }

        Text("Amount", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AmountOptions.forEach { option ->
                FilterChip(
                    selected = amount == option,
                    onClick = { amount = option },
                    label = { Text(option.toString()) },
                )
            }
        }

        Text("Resource", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ResourceOptions.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = resource == option,
                    onClick = { resource = option },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ResourceOptions.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = resourceColor(option).copy(alpha = 0.2f),
                        activeContentColor = resourceColor(option),
                    ),
                ) { Text(option.toString()) }
            }
        }

        Button(
            onClick = {
                val a = amount ?: return@Button
                val r = resource ?: return@Button
                val cost = if (ongoing) Cost.Ongoing(a, r) else Cost.Upfront(a, r)
                onAdd(cost)
            },
            enabled = amount != null && resource != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add Cost")
        }
    }
}

@Composable
private fun SaveFeedback(
    saveState: AbilityListingContributionsViewModel.SaveState,
    onClearState: () -> Unit,
) {
    when (saveState) {
        is AbilityListingContributionsViewModel.SaveState.Success -> {
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
        is AbilityListingContributionsViewModel.SaveState.Error -> {
            Text(
                text = saveState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> {}
    }
}

private inline fun <reified T : Any> sealedObjects(): List<T> =
    T::class.sealedSubclasses.mapNotNull { it.objectInstance }

private val AbilityTypeOptions: List<AbilityType> =
    sealedObjects<AbilityType>().filterNot { it === AbilityType.Use }

private val AmountOptions: List<Amount> = sealedObjects<Amount>()

private val ResourceOptions: List<Resource> = sealedObjects<Resource>()

private val PropertyOptions: List<Property> =
    sealedObjects<Property>().sortedBy { it.toString() }

private fun resourceColor(resource: Resource): Color = when (resource) {
    Resource.Mana -> Color(0xFF1976D2)
    Resource.Stamina -> Color(0xFF388E3C)
    Resource.Health -> Color(0xFFD32F2F)
}

@Preview(showBackground = true)
@Composable
private fun AbilityListingFormEmptyPreview() {
    AbilityListingForm(
        initialName = null,
        isEdit = false,
        effects = emptyList(),
        saveState = AbilityListingContributionsViewModel.SaveState.Idle,
        onUpdateEffect = { _, _ -> },
        onRemoveEffect = {},
        onAppendEffect = {},
        onSave = {},
        onDelete = {},
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AbilityListingFormPopulatedPreview() {
    AbilityListingForm(
        initialName = null,
        isEdit = false,
        effects = listOf(
            EffectDraft(
                rank = Rank.Iron,
                type = AbilityType.Spell,
                properties = listOf(Property.Fire, Property.Magic),
                costs = listOf(Cost.Upfront(Amount.Low, Resource.Mana)),
                replacementKey = "",
                description = "Hurls a fireball at the target.",
            ),
            EffectDraft(),
        ),
        saveState = AbilityListingContributionsViewModel.SaveState.Idle,
        onUpdateEffect = { _, _ -> },
        onRemoveEffect = {},
        onAppendEffect = {},
        onSave = {},
        onDelete = {},
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AbilityListingFormErrorPreview() {
    AbilityListingForm(
        initialName = null,
        isEdit = false,
        effects = emptyList(),
        saveState = AbilityListingContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onUpdateEffect = { _, _ -> },
        onRemoveEffect = {},
        onAppendEffect = {},
        onSave = {},
        onDelete = {},
        onClearState = {},
    )
}
