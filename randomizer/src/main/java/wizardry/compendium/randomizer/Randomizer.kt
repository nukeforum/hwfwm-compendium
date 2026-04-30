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
import wizardry.compendium.essences.model.Essence

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
