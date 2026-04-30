package wizardry.compendium.awakeningstone.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.R
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun AwakeningStoneSearch(
    onStoneClicked: (AwakeningStone) -> Unit,
    viewModel: AwakeningStoneSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val result = state) {
        is AwakeningStoneSearchUiState.Error -> TODO()

        AwakeningStoneSearchUiState.Loading -> Loading(
            modifier = Modifier.fillMaxSize()
        )

        is AwakeningStoneSearchUiState.Success -> Screen(
            modifier = Modifier.fillMaxSize(),
            state = result,
            onFilterTermChanged = viewModel::setFilterTerm,
            onFilterSelected = viewModel::applyFilter,
            onStoneClicked = onStoneClicked,
        )
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: AwakeningStoneSearchUiState.Success,
    onFilterTermChanged: (String) -> Unit,
    onFilterSelected: (AwakeningStoneSearchFilter) -> Unit,
    onStoneClicked: (AwakeningStone) -> Unit,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(state.stones, { it.name }) { stone ->
                AwakeningStoneListItem(
                    stone = stone,
                    modifier = Modifier.clickable { onStoneClicked(stone) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                label = { Text(text = "Type an awakening stone name") },
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
    onFilterSelected: (AwakeningStoneSearchFilter) -> Unit,
    appliedFilters: Collection<AwakeningStoneSearchFilter>,
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
            AwakeningStoneSearchFilter.options.forEach {
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
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text = "Loading")
    }
}

@Composable
fun AwakeningStoneListItem(
    modifier: Modifier = Modifier,
    stone: AwakeningStone,
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(essenceHighlight(isRestricted = false))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stone.name)
        Text(stone.rarity.toString())
    }
}

@Preview
@Composable
private fun AwakeningStoneListItemPreview() {
    AwakeningStoneListItem(
        stone = AwakeningStone.of(name = "Wind", rarity = Rarity.Common),
    )
}
