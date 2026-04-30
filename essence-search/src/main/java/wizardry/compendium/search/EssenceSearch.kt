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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ui.R
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun EssenceSearch(
    viewModel: SearchViewModel = hiltViewModel(),
    onEssenceClicked: (Essence) -> Unit,
    onContributeClicked: () -> Unit,
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
            onContributeClicked = onContributeClicked,
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
    onContributeClicked: () -> Unit,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(state.essences, { it.name }) { essence ->
                EssenceListItem(
                    essence = essence,
                    modifier = Modifier
                        .clickable(onClick = { onEssenceClicked(essence) })
                )
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

        Button(
            onClick = onContributeClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text("Contribute Essence")
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

@Preview
@Composable
fun EssenceListItemPreview() {
    val essence = Essence.of(
        name = "sin",
        restricted = false,
        description = "Manifested essence of transgression",
        rarity = Rarity.Legendary
    )
    EssenceListItem(essence = essence)
}
