package wizardry.compendium.abilitylistinginfo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import wizardry.compendium.ability.preview.AbilityPreview
import wizardry.compendium.essences.model.Ability

@Composable
fun AbilityListingDetails(
    listingName: String,
    onListingLoaded: (Ability.Listing) -> Unit,
    onEditContribution: (Ability.Listing) -> Unit = {},
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
            Details(state = details, onEdit = { onEditContribution(details.listing) })
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
private fun Details(state: AbilityListingDetailUiState.Success, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (state.isContribution) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(text = " Edit", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                .border(1.dp, Color.DarkGray)
                .padding(8.dp)
        ) {
            AbilityPreview(ability = state.listing)
        }
    }
}
