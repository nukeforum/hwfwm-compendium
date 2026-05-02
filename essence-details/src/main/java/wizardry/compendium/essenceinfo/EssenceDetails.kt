package wizardry.compendium.essenceinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.ui.LinkedEssence
import java.security.InvalidParameterException

@Composable
fun EssenceDetails(
    essenceName: String,
    onEssenceLoaded: (Essence) -> Unit,
    onEditContribution: (Essence) -> Unit = {},
    onShareContribution: (Essence) -> Unit = {},
    viewModel: EssenceDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(essenceName) {
        viewModel.load(essenceName)
    }

    val state by viewModel.state.collectAsState()

    BackHandler(
        enabled = (state as? EssenceDetailUiState.Success)?.previousEssence != null
    ) { viewModel.goBack() }

    when (val details = state) {
        is EssenceDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load essence")
        EssenceDetailUiState.Loading -> Loading()
        is EssenceDetailUiState.Success -> {
            onEssenceLoaded(details.essence)
            Details(
                state = details,
                onEssenceClick = { viewModel.load(it) },
                onEdit = { onEditContribution(details.essence) },
                onShare = { onShareContribution(details.essence) },
            )
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
private fun Details(
    state: EssenceDetailUiState.Success,
    onEssenceClick: (Essence) -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
) {
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
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
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
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = state.essence.report()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is EssenceDetailUiState.Success.ConfluenceUiState -> ConfluenceDetails(
                state = state,
                onEssenceClick = onEssenceClick
            )
            is EssenceDetailUiState.Success.ManifestationUiState -> ManifestationDetails(
                state = state,
                onEssenceClick = onEssenceClick
            )
        }
    }
}

@Composable
private fun ConfluenceDetails(
    state: EssenceDetailUiState.Success.ConfluenceUiState,
    onEssenceClick: (Essence) -> Unit,
) {
    Text("Known confluence combinations:")
    ConfluenceCombinationsDisplay(
        selectedEssence = state.essence,
        previousEssence = state.previousEssence,
        onEssenceClick = onEssenceClick,
    )
}

@Composable
private fun ManifestationDetails(
    state: EssenceDetailUiState.Success.ManifestationUiState,
    onEssenceClick: (Essence) -> Unit,
) {
    val producedConfluences = state.knownConfluences
    Text("Known to produce the following confluence essences:")
    FlowRow(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .verticalScroll(ScrollState(0)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        producedConfluences.forEach {
            LinkedEssence(
                essence = it,
                isLastViewed = state.previousEssence == it,
                isRestricted = it.isRestricted,
                onEssenceClick = onEssenceClick,
            )
        }
    }
}

private fun Essence.report(): String {
    return when (this) {
        is Essence.Confluence -> {
            """
                $name Confluence
            """.trimIndent()
        }
        is Essence.Manifestation -> {
            """
                Item: [$name Essence]
                (${rank.toString().lowercase()}, ${rarity.toString().lowercase()})
                
                $description (${properties.joinToString(", ")}).
                
                Requirements: Less than 4 absorbed essences.
                
                ${effects.joinToString { "Effect: ${it.description}" }}
                """.trimIndent()
        }
        else -> throw InvalidParameterException("Provided Essence subtype is unsupported in EssenceDetails")
    }
}

@Composable
private fun ConfluenceCombinationsDisplay(
    selectedEssence: Essence.Confluence,
    previousEssence: Essence?,
    onEssenceClick: (Essence) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(state = ScrollState(0)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        selectedEssence.confluenceSets.forEach { confluenceSet ->
            Row(
                modifier = Modifier
                    .background(if (confluenceSet.isRestricted) Color.Red.copy(alpha = 0.5f) else Color.Unspecified)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                confluenceSet.set.forEach {
                    LinkedEssence(
                        essence = it,
                        isLastViewed = previousEssence == it,
                        isRestricted = it.isRestricted,
                        onEssenceClick = onEssenceClick
                    )
                    if (confluenceSet.set.last() != it) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            if (selectedEssence.confluenceSets.last() != confluenceSet) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
