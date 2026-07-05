package wizardry.compendium.racetemplate.search

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
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.R
import wizardry.compendium.ui.SearchEmptyState
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.essenceHighlight

@Composable
fun RaceTemplateSearch(
    onTemplateClicked: (RaceTemplate) -> Unit,
    onAddClicked: () -> Unit,
    viewModel: RaceTemplateSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val result = state) {
        is RaceTemplateSearchUiState.Error -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("Failed to load race templates") }

        RaceTemplateSearchUiState.Loading -> Loading(modifier = Modifier.fillMaxSize())

        is RaceTemplateSearchUiState.Success -> Screen(
            modifier = Modifier.fillMaxSize(),
            state = result,
            onFilterTermChanged = viewModel::setFilterTerm,
            onTemplateClicked = onTemplateClicked,
            onAddClicked = onAddClicked,
        )
    }
}

@Composable
private fun Screen(
    modifier: Modifier,
    state: RaceTemplateSearchUiState.Success,
    onFilterTermChanged: (String) -> Unit,
    onTemplateClicked: (RaceTemplate) -> Unit,
    onAddClicked: () -> Unit,
) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        if (state.templates.isEmpty()) {
            SearchEmptyState(
                hasFilter = state.filterTerm.isNotEmpty(),
                onAddClicked = onAddClicked,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.templates, { it.name }) { template ->
                    RaceTemplateListItem(
                        template = template,
                        modifier = Modifier.clickable { onTemplateClicked(template) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                label = { Text(text = "Type a race template name") },
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
fun RaceTemplateListItem(
    modifier: Modifier = Modifier,
    template: RaceTemplate,
) {
    Row(
        modifier = modifier
            .padding(vertical = 4.dp)
            .background(essenceHighlight(isRestricted = false))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(template.name)
        Text("${template.racialAbilities.size} racial abilities")
    }
}

@PreviewLightDark
@Composable
private fun RaceTemplateListItemPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        RaceTemplateListItem(
            template = RaceTemplate(name = "Runic Golem", racialAbilities = emptyList()),
        )
    }
}

@PreviewLightDark
@Composable
private fun ScreenPopulatedPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Screen(
            modifier = Modifier.fillMaxSize(),
            state = RaceTemplateSearchUiState.Success(
                templates = listOf(
                    RaceTemplate(name = "Runic Golem", racialAbilities = emptyList()),
                    RaceTemplate(name = "Celestial", racialAbilities = emptyList()),
                ),
                filterTerm = "",
            ),
            onFilterTermChanged = {},
            onTemplateClicked = {},
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
            state = RaceTemplateSearchUiState.Success(templates = emptyList(), filterTerm = "xyz"),
            onFilterTermChanged = {},
            onTemplateClicked = {},
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
