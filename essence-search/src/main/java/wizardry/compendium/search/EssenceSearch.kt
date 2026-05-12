package wizardry.compendium.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ui.R
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.SearchEmptyState
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.ThemeMode
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun EssenceSearch(
    viewModel: SearchViewModel = hiltViewModel(),
    onEssenceClicked: (Essence) -> Unit,
    onAddClicked: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    when (val result = state) {
        is SearchUiState.Error -> TODO()

        SearchUiState.Loading -> Loading(
            modifier = Modifier.fillMaxSize()
        )

        is SearchUiState.Success -> Screen(
            modifier = Modifier.fillMaxSize(),
            state = result,
            onEssenceClicked = onEssenceClicked,
            onFilterTermChanged = viewModel::setFilterTerm,
            onFilterSelected = viewModel::applyFilter,
            onAddClicked = onAddClicked,
        )
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: SearchUiState.Success,
    onEssenceClicked: (Essence) -> Unit,
    onFilterTermChanged: (String) -> Unit,
    onFilterSelected: (SearchFilter) -> Unit,
    onAddClicked: () -> Unit,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (state.essences.isEmpty()) {
            SearchEmptyState(
                hasFilter = state.filterTerm.isNotEmpty() || state.appliedFilters.isNotEmpty(),
                onAddClicked = onAddClicked,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(state.essences, { it.name }) { essence ->
                    EssenceListItem(
                        essence = essence,
                        modifier = Modifier
                            .clickable(onClick = { onEssenceClicked(essence) })
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                label = { Text(text = "Type an essence name") },
                value = state.filterTerm,
                onValueChange = { onFilterTermChanged(it) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_x),
                        contentDescription = stringResource(R.string.clear_search_accessibility),
                        modifier = Modifier.clickable { onFilterTermChanged("") }
                    )
                },
                modifier = Modifier.weight(1f)
            )

            FilterDropDown(onFilterSelected, state.appliedFilters)
        }
    }
}

@Composable
private fun FilterDropDown(
    onFilterSelected: (SearchFilter) -> Unit,
    appliedFilters: Collection<SearchFilter>
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(
            onClick = { dropdownExpanded = true }
        ) {
            Text(
                text = "Rarity",
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
            )
        }
        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
        ) {
            SearchFilter.options.forEach {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (appliedFilters.contains(it)) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                            Text(text = it.name)
                        }
                    },
                    onClick = { onFilterSelected(it) },
                )
            }
        }
    }
}

@Composable
private fun Loading(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Loading")
    }
}

@Composable
fun EssenceListItem(
    modifier: Modifier = Modifier,
    essence: Essence
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(essenceHighlight(isRestricted = essence.isRestricted))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(essence.name)
        if (essence is Essence.Manifestation)
            Text(essence.rarity.toString())
        else
            Text("Confluence")
    }
}

@PreviewLightDark
@Composable
fun EssenceListItemPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        val essence = Essence.of(
            name = "sin",
            restricted = false,
            description = "Manifested essence of transgression",
            rarity = Rarity.Legendary
        )
        EssenceListItem(essence = essence)
    }
}

@PreviewLightDark
@Composable
private fun ScreenPopulatedPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Screen(
            modifier = Modifier.fillMaxSize(),
            state = SearchUiState.Success(
                essences = listOf(
                    Essence.of(name = "Fire", description = "Manifested essence of fire", rarity = Rarity.Common, restricted = false),
                    Essence.of(name = "Wind", description = "Manifested essence of wind", rarity = Rarity.Uncommon, restricted = false),
                    Essence.of(name = "Sin", description = "Manifested essence of transgression", rarity = Rarity.Legendary, restricted = false),
                ),
                filterTerm = "",
                appliedFilters = emptyList(),
            ),
            onEssenceClicked = {},
            onFilterTermChanged = {},
            onFilterSelected = {},
            onAddClicked = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ScreenEmptyPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Screen(
            modifier = Modifier.fillMaxSize(),
            state = SearchUiState.Success(
                essences = emptyList(),
                filterTerm = "xyz",
                appliedFilters = emptyList(),
            ),
            onEssenceClicked = {},
            onFilterTermChanged = {},
            onFilterSelected = {},
            onAddClicked = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun LoadingPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Loading(modifier = Modifier.fillMaxSize())
    }
}
