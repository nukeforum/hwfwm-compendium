package com.mobile.wizardry.compendium.essenceinfo

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = """
                Item: [${selectedEssence.name} Essence]
                (${selectedEssence.rank.toString().lowercase()}, ${selectedEssence.rarity.toString().lowercase()})
                
                ${selectedEssence.description} (${selectedEssence.properties.joinToString(", ")}).
                
                Requirements: Less than 4 absorbed essences.
                
                ${selectedEssence.effects.joinToString { "Effect: ${it.description}" }}
                """.trimIndent()
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedEssence.isConfluence()) {
            Text("Known confluence combinations:")
            selectedEssence.confluences.forEach { confluence ->
                Row { confluence.forEach { LinkedEssence(essence = it) } }
            }
        } else {
            val producedConfluences = essences
                .filter { it.isConfluence() && it.isProducedBy(selectedEssence) }
            Text("Known to produce the following confluence essences:")
            producedConfluences.forEach { LinkedEssence(essence = it) }
        }
    }
}

private fun Essence.isProducedBy(selectedEssence: Essence): Boolean {
    return confluences.any { confluence -> confluence.any { essence -> essence == selectedEssence } }
}

private fun Essence.isConfluence() = confluences.isNotEmpty()
