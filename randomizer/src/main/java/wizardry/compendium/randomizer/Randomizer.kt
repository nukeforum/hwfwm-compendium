package wizardry.compendium.randomizer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

@Composable
fun Randomizer(
    viewModel: RandomizerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        when (val result = state) {
            is RandomizerUiState.Success -> {
                RandomizerResult(
                    result.randomizedSet,
                    result.knownConfluence,
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            is RandomizerUiState.Error -> TODO()
            RandomizerUiState.Loading -> Loading(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            onClick = { viewModel.randomize() },
        ) {
            Text(text = "Randomize Essences")
        }
    }
}

@Composable
private fun RandomizerResult(
    randomizedSet: Set<Essence.Manifestation>,
    confluence: Essence.Confluence?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        randomizedSet.forEach { Text(text = it.name) }
        Text(text = confluence?.name ?: "No known Confluence")
    }
}

@Composable
private fun Loading(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Loading")
    }
}

private fun sampleManifestation(name: String): Essence.Manifestation = Essence.Manifestation(
    name = name,
    rank = Rank.Iron,
    rarity = Rarity.Common,
    properties = listOf(Property.Magic),
    description = "Manifested essence of $name",
    isRestricted = false,
)

@PreviewLightDark
@Composable
private fun RandomizerResultWithConfluencePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        RandomizerResult(
            randomizedSet = setOf(
                sampleManifestation("Fire"),
                sampleManifestation("Wind"),
                sampleManifestation("Water"),
                sampleManifestation("Lightning"),
            ),
            confluence = Essence.of(
                "Storm",
                restricted = false,
                ConfluenceSet(
                    sampleManifestation("Water"),
                    sampleManifestation("Wind"),
                    sampleManifestation("Lightning"),
                ),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun RandomizerResultNoConfluencePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        RandomizerResult(
            randomizedSet = setOf(
                sampleManifestation("Earth"),
                sampleManifestation("Wood"),
                sampleManifestation("Sand"),
                sampleManifestation("Iron"),
            ),
            confluence = null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun RandomizerLoadingPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Loading(modifier = Modifier.fillMaxWidth())
    }
}
