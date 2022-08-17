package com.mobile.wizardry.compendium.randomizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence

@Composable
fun Randomizer(
    essenceProvider: EssenceProvider
) {
    val essences by produceState(
        initialValue = emptyList<Essence>(),
        producer = { value = essenceProvider.getEssences() }
    )

    if (essences.isEmpty()) return

    val (manifestations, confluences) = essences.groupBy { it.javaClass.simpleName }
        .let { Pair(it[Essence.Manifestation::class.java.simpleName]!!, it[Essence.Confluence::class.java.simpleName]!!) }

    val set = mutableSetOf<Essence.Manifestation>()
    while (set.size < 3) {
        val manifestation = manifestations.random()
        if (manifestation !is Essence.Manifestation) continue
        set.add(manifestation)
    }

    val confluenceSet = ConfluenceSet(set)

    val confluence = confluences.find { (it as? Essence.Confluence)?.confluenceSets?.contains(confluenceSet) == true }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        set.forEach {
            Text(text = it.name)
        }
        Text(text = confluence?.name ?: "No known Confluence")
    }
}
