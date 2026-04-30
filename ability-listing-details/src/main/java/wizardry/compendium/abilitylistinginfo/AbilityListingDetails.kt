package wizardry.compendium.abilitylistinginfo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.model.Ability

@Composable
fun AbilityListingDetails(
    listingName: String,
    onListingLoaded: (Ability.Listing) -> Unit,
    viewModel: AbilityListingDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(listingName) {
        viewModel.load(listingName)
    }

    val state by viewModel.state.collectAsState()

    when (val details = state) {
        is AbilityListingDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load ability listing")
        AbilityListingDetailUiState.Loading -> Loading()
        is AbilityListingDetailUiState.Success -> {
            onListingLoaded(details.listing)
            Details(state = details)
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Loading")
    }
}

@Composable
private fun Details(state: AbilityListingDetailUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                .border(1.dp, Color.DarkGray)
                .padding(8.dp)
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = state.listing.report()
            )
        }
    }
}

private fun Ability.Listing.report(): String {
    val effectsLine = if (effects.isEmpty()) {
        "No effects recorded."
    } else {
        effects.joinToString(separator = "\n") {
            "Effect (${it.rank}): ${it.description}"
        }
    }
    return """
        Ability Listing: $name

        $effectsLine
    """.trimIndent()
}
