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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.R
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun AwakeningStoneSearch(
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
        )
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: AwakeningStoneSearchUiState.Success,
    onFilterTermChanged: (String) -> Unit,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            items(state.stones, { it.name }) { stone ->
                AwakeningStoneListItem(stone = stone)
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
