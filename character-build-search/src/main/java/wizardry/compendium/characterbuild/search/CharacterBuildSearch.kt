package wizardry.compendium.characterbuild.search

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.R
import wizardry.compendium.ui.SearchEmptyState
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.ThemeMode
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun CharacterBuildSearch(
    onBuildClicked: (CharacterBuild) -> Unit,
    onAddClicked: () -> Unit,
    viewModel: CharacterBuildSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val result = state) {
        is CharacterBuildSearchUiState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("Failed to load builds") }

        CharacterBuildSearchUiState.Loading -> Loading(modifier = Modifier.fillMaxSize())

        is CharacterBuildSearchUiState.Success -> Screen(
            modifier = Modifier.fillMaxSize(),
            state = result,
            onFilterTermChanged = viewModel::setFilterTerm,
            onBuildClicked = onBuildClicked,
            onAddClicked = onAddClicked,
        )
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: CharacterBuildSearchUiState.Success,
    onFilterTermChanged: (String) -> Unit,
    onBuildClicked: (CharacterBuild) -> Unit,
    onAddClicked: () -> Unit,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (state.builds.isEmpty()) {
            SearchEmptyState(
                hasFilter = state.filterTerm.isNotEmpty(),
                onAddClicked = onAddClicked,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.builds, { it.name }) { build ->
                    CharacterBuildListItem(
                        build = build,
                        modifier = Modifier.clickable { onBuildClicked(build) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                label = { Text(text = "Type a build name") },
                value = state.filterTerm,
                onValueChange = { onFilterTermChanged(it) },
                trailingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_x),
                        contentDescription = stringResource(R.string.clear_search_accessibility),
                        modifier = Modifier.clickable { onFilterTermChanged("") },
                    )
                },
                modifier = Modifier.weight(1f),
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
fun CharacterBuildListItem(
    modifier: Modifier = Modifier,
    build: CharacterBuild,
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(essenceHighlight(isRestricted = false))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(build.name)
        Text(build.race)
    }
}

@PreviewLightDark
@Composable
private fun CharacterBuildListItemPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        CharacterBuildListItem(
            build = CharacterBuild(name = "Jason", race = "Outworlder", racialAbilities = emptyList()),
        )
    }
}
