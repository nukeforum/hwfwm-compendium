package wizardry.compendium.characterbuilddetails

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import wizardry.compendium.essences.model.CharacterBuild

@Composable
fun CharacterBuildDetails(
    buildName: String,
    onBuildLoaded: (CharacterBuild) -> Unit,
    onEditContribution: (CharacterBuild) -> Unit = {},
    viewModel: CharacterBuildDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(buildName) { viewModel.load(buildName) }

    val state by viewModel.state.collectAsState()

    when (val details = state) {
        is CharacterBuildDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load build")
        CharacterBuildDetailUiState.Loading -> Loading()
        is CharacterBuildDetailUiState.Success -> {
            onBuildLoaded(details.build)
            Details(
                state = details,
                onEdit = { onEditContribution(details.build) },
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading") }
}

@Composable
private fun Details(
    state: CharacterBuildDetailUiState.Success,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
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
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                .border(1.dp, Color.DarkGray)
                .padding(8.dp),
        ) {
            Text(
                modifier = Modifier.align(Alignment.TopStart),
                text = state.build.report(),
            )
        }
    }
}
