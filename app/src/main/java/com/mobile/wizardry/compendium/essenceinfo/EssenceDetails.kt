package com.mobile.wizardry.compendium.essenceinfo

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.MainAxisAlignment
import com.mobile.wizardry.compendium.EssenceProvider
import com.mobile.wizardry.compendium.data.Essence
import com.mobile.wizardry.compendium.ui.LinkedEssence

@Composable
fun EssenceDetails(essenceProvider: EssenceProvider, essenceHash: Int) {
    val essences by produceState(
        initialValue = emptyList<Essence>(),
        producer = { value = essenceProvider.getEssences() }
    )

    val selectedEssence = essences.find { it.hashCode() == essenceHash } ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = """
                Item: [${selectedEssence.name} Essence]
                (${selectedEssence.rank.toString().lowercase()}, ${selectedEssence.rarity.toString().lowercase()})
                
                ${selectedEssence.description} (${selectedEssence.properties.joinToString(", ")}).
                
                Requirements: Less than 4 absorbed essences.
                
                ${selectedEssence.effects.joinToString { "Effect: ${it.description}" }}
                """.trimIndent(),
            modifier = Modifier
                .border(1.dp, Color.DarkGray)
                .padding(8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedEssence.isConfluence()) {
            Text("Known confluence combinations:")
            ConfluenceCombinationsDisplay(selectedEssence)
        } else {
            val producedConfluences = essences.filter { it.isProducedBy(selectedEssence) }
            Text("Known to produce the following confluence essences:")
            FlowRow(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .verticalScroll(ScrollState(0)),
                mainAxisAlignment = MainAxisAlignment.Center,
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp,
            ) { producedConfluences.forEach { LinkedEssence(essence = it) } }
        }
    }
}

@Composable
private fun ConfluenceCombinationsDisplay(selectedEssence: Essence) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .verticalScroll(state = ScrollState(0)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        selectedEssence.confluences.forEach { confluence ->
            Row(
                modifier = Modifier.fillMaxWidth(0.8f),
                horizontalArrangement = Arrangement.Center,
            ) {
                confluence.forEach {
                    LinkedEssence(essence = it)
                    if (confluence.last() != it) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
            if (selectedEssence.confluences.last() != confluence) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private fun Essence.isProducedBy(selectedEssence: Essence): Boolean {
    return confluences.any { confluence -> confluence.any { essence -> essence == selectedEssence } }
}

private fun Essence.isConfluence() = confluences.isNotEmpty()
