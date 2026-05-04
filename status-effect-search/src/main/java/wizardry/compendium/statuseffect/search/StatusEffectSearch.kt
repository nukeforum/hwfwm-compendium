package wizardry.compendium.statuseffect.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

private val AfflictionSubtypes: List<StatusType.Affliction> = listOf(
    StatusType.Affliction.Curse, StatusType.Affliction.Disease, StatusType.Affliction.Elemental,
    StatusType.Affliction.Holy, StatusType.Affliction.Magic, StatusType.Affliction.Poison,
    StatusType.Affliction.Unholy, StatusType.Affliction.Wound, StatusType.Affliction.UnTyped,
)

private val BoonSubtypes: List<StatusType.Boon> = listOf(
    StatusType.Boon.Holy, StatusType.Boon.Magic, StatusType.Boon.Unholy, StatusType.Boon.UnTyped,
)

private enum class TopLevel { Affliction, Boon }

@Composable
fun StatusEffectSearch(
    onEffectClicked: (StatusEffect) -> Unit,
    viewModel: StatusEffectSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val result = state) {
        is StatusEffectSearchUiState.Error -> TODO()
        StatusEffectSearchUiState.Loading -> Loading(modifier = Modifier.fillMaxSize())
        is StatusEffectSearchUiState.Success -> Screen(
            modifier = Modifier.fillMaxSize(),
            state = result,
            onFilterTermChanged = viewModel::setFilterTerm,
            onFilterSelected = viewModel::applyFilter,
            onEffectClicked = onEffectClicked,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Screen(
    modifier: Modifier,
    state: StatusEffectSearchUiState.Success,
    onFilterTermChanged: (String) -> Unit,
    onFilterSelected: (StatusEffectSearchFilter?) -> Unit,
    onEffectClicked: (StatusEffect) -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = modifier) {
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(state.effects, { it.name }) { effect ->
                StatusEffectRow(effect = effect, onClick = { onEffectClicked(effect) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                label = { Text("Search by name") },
                value = state.filterTerm,
                onValueChange = onFilterTermChanged,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showFilterSheet = true }) {
                Text("Filter")
            }
        }
    }

    if (showFilterSheet) {
        val current = state.appliedFilter
        var topLevel by remember {
            mutableStateOf(
                when (current) {
                    is StatusEffectSearchFilter.Boon -> TopLevel.Boon
                    else -> TopLevel.Affliction
                }
            )
        }
        var afflictionSubs by remember {
            mutableStateOf((current as? StatusEffectSearchFilter.Affliction)?.subtypes.orEmpty())
        }
        var boonSubs by remember {
            mutableStateOf((current as? StatusEffectSearchFilter.Boon)?.subtypes.orEmpty())
        }

        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val topLevelOptions = TopLevel.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    topLevelOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, topLevelOptions.size),
                            selected = topLevel == option,
                            onClick = { topLevel = option },
                            label = { Text(option.name) },
                        )
                    }
                }

                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (topLevel) {
                        TopLevel.Affliction -> AfflictionSubtypes.forEach { sub ->
                            FilterChip(
                                selected = sub in afflictionSubs,
                                onClick = {
                                    afflictionSubs = if (sub in afflictionSubs)
                                        afflictionSubs - sub
                                    else
                                        afflictionSubs + sub
                                },
                                label = { Text(sub::class.simpleName ?: sub.toString()) },
                            )
                        }
                        TopLevel.Boon -> BoonSubtypes.forEach { sub ->
                            FilterChip(
                                selected = sub in boonSubs,
                                onClick = {
                                    boonSubs = if (sub in boonSubs)
                                        boonSubs - sub
                                    else
                                        boonSubs + sub
                                },
                                label = { Text(sub::class.simpleName ?: sub.toString()) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onFilterSelected(null)
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clear")
                    }
                    Button(
                        onClick = {
                            val filter = when (topLevel) {
                                TopLevel.Affliction -> StatusEffectSearchFilter.Affliction(afflictionSubs)
                                TopLevel.Boon -> StatusEffectSearchFilter.Boon(boonSubs)
                            }
                            onFilterSelected(filter)
                            showFilterSheet = false
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusEffectRow(effect: StatusEffect, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(effect.name, style = MaterialTheme.typography.titleMedium)
            Text(typeLabel(effect.type), style = MaterialTheme.typography.bodySmall)
            if (effect.stackable) {
                Text("Stackable", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun typeLabel(type: StatusType): String {
    return when (type) {
        is StatusType.Affliction -> "Affliction · ${type::class.simpleName}"
        is StatusType.Boon -> "Boon · ${type::class.simpleName}"
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Loading")
    }
}
