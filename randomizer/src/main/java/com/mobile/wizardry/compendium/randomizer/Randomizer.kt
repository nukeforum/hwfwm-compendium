package com.mobile.wizardry.compendium.randomizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobile.wizardry.compendium.model.core.UiResult

@Composable
fun Randomizer(
    viewModel: RandomizerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    if (state !is UiResult.Success) return

    val uiState = remember { state.data }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        uiState.randomizedSet.forEach {
            Text(text = it.name)
        }
        Text(text = uiState.knownConfluence?.name ?: "No known Confluence")
    }
}
