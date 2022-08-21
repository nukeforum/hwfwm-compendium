package com.mobile.wizardry.compendium.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobile.wizardry.compendium.R
import com.mobile.wizardry.compendium.UiResult
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity
import com.mobile.wizardry.compendium.ui.theme.essenceHighlight

private val skyBlue = Color(0xFF87CEEB)

@Composable
fun EssenceSearch(
    viewModel: SearchViewModel = hiltViewModel(),
    onEssenceClicked: (Essence) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showPopUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
//        .background(skyBlue)
    ) {
        when (state) {
            is UiResult.Error -> TODO()
            UiResult.Loading -> Loading(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .weight(1f)
            )
            is UiResult.Success -> Screen(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = state.data,
                onEssenceClicked = onEssenceClicked,
                onFilterTermChanged = viewModel::setFilterTerm,
                onFilterSelected = viewModel::applyFilter,
            )
        }
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: SearchUiState,
    onEssenceClicked: (Essence) -> Unit,
    onFilterTermChanged: (String) -> Unit,
    onFilterSelected: (SearchFilter) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(state.essences, { it.hashCode() }) { essence ->
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
    ) {
        TextField(
            label = { Text(text = "Type an essence name") },
            value = state.filterTerm,
            onValueChange = { onFilterTermChanged(it) },
//            modifier = Modifier.weight(1f),
            trailingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_x),
                    contentDescription = stringResource(R.string.clear_search_accessibility),
                    modifier = Modifier.clickable { onFilterTermChanged("") }
                )
            }
        )

        var dropdownExpanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart)
        ) {
            TextButton(onClick = { dropdownExpanded = true }) { Text(text = "Show/Hide Kinds") }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                SearchFilter.options.forEach {
                    DropdownMenuItem(onClick = { onFilterSelected(it) }) {
                        Text(text = it.name)
                        if (state.appliedFilters.contains(it)) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
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
