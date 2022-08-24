package com.mobile.wizardry.compendium.essenceinfo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.*
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
    onEssenceClick: (Essence) -> Unit, //TODO: Refactor to re-use this screen for every essence
    viewModel: EssenceDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is UiResult.Error -> TODO()
        UiResult.Loading -> Loading(onLoad = { viewModel.load(essenceHash) })
        is UiResult.Success -> Details(state.data, onEssenceClick)
    }
}

@Composable
private fun Loading(
    onLoad: () -> Unit
) {
    SideEffect {
        onLoad()
    }

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
    val essence = remember { state.essence }

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
                text = essence.report()
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (essence is Essence.Confluence) {
            Text("Known confluence combinations:")
            ConfluenceCombinationsDisplay(
                selectedEssence = essence,
                onEssenceClick = onEssenceClick
            )
        } else {
            val producedConfluences = (state as EssenceDetailUiState.ManifestationUiState).knownConfluences
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
                        isRestricted = it.isRestricted,
                        onEssenceClick = onEssenceClick,
                    )
                }
            }
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
