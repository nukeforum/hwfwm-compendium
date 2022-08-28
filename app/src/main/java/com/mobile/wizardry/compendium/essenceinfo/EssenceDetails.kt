package com.mobile.wizardry.compendium.essenceinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.model.core.UiResult
import com.mobile.wizardry.compendium.ui.LinkedEssence
import java.security.InvalidParameterException

@Composable
fun EssenceDetails(
    essenceHash: Int,
    onEssenceLoaded: (Essence) -> Unit,
    viewModel: EssenceDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.load(essenceHash)
    }

    val state by viewModel.state.collectAsState()

    BackHandler(enabled = (state as? UiResult.Success)?.data?.previousEssence != null) { viewModel.goBack() }

    when (state) {
        is UiResult.Error -> TODO()
        UiResult.Loading -> Loading()
        is UiResult.Success -> {
            onEssenceLoaded(state.data.essence)
            Details(state = state.data, onEssenceClick = { viewModel.load(it) })
        }
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
    state: EssenceDetailUiState,
    onEssenceClick: (Essence) -> Unit,
) {
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
                text = state.essence.report()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        when (state) {
            is EssenceDetailUiState.ConfluenceUiState -> ConfluenceDetails(state = state, onEssenceClick = onEssenceClick)
            is EssenceDetailUiState.ManifestationUiState -> ManifestationDetails(state = state, onEssenceClick = onEssenceClick)
        }
    }
}

@Composable
private fun ConfluenceDetails(
    state: EssenceDetailUiState.ConfluenceUiState,
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
    state: EssenceDetailUiState.ManifestationUiState,
    onEssenceClick: (Essence) -> Unit,
) {
    val producedConfluences = state.knownConfluences
    Text("Known to produce the following confluence essences:")
    FlowRow(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .verticalScroll(ScrollState(0)),
        mainAxisAlignment = MainAxisAlignment.Center,
        mainAxisSpacing = 8.dp,
        crossAxisSpacing = 8.dp,
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
