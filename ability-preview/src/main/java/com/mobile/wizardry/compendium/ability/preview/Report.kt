package com.mobile.wizardry.compendium.ability.preview

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobile.wizardry.compendium.essences.model.Ability
import com.mobile.wizardry.compendium.essences.model.Effect
import com.mobile.wizardry.compendium.essences.model.Rank

@Composable
internal fun Ability.Report() {
    when (this) {
        is Ability.Acquired -> Report()
        is Ability.Listing -> Report()
    }
}

@Composable
private fun Ability.Acquired.Report() {
    Report(
        titleSlot = { Text(text = "Ability: $name (${boundEssence.name})") },
        progressSlot = {
            Text(text = "Current Rank: ${ability.rank} ${ability.tier}(${ability.progress * 100}%)")
            Spacer(modifier = Modifier.height(12.dp))
        },
        effectsSlot = { effects.Report(rank) },
    )
}

@Composable
private fun Ability.Listing.Report() {
    Report(
        titleSlot = {
            Text(text = "Ability: $name")
        },
        effectsSlot = { effects.Report() },
    )
}

@Composable
private fun Ability.Report(
    titleSlot: @Composable () -> Unit = {},
    progressSlot: @Composable () -> Unit = {},
    effectsSlot: @Composable () -> Unit = {}
) {
    titleSlot()
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = "${reportType()} (${reportProperties()})")
    Text(text = "Cost: ${reportCost()}.")
    Text(text = "Cooldown: ${reportCooldown()}.")
    Spacer(modifier = Modifier.height(12.dp))
    progressSlot()
    effectsSlot()
}

private fun Ability.reportType(): String {
    return effects.map { it.type }.toSet().joinToString("/")
}

private fun Ability.reportProperties(): String {
    return effects.flatMap { it.properties }.toSet().joinToString(", ")
}

private fun Ability.reportCost(): String {
    return effects.mapNotNull { effect -> effect.cost.takeIf { cost -> cost.isNotEmpty() } }
        .takeIf { it.size == 1 }
        ?.first()?.joinToString(separator = ", ")
        ?: "Varies"
}

private fun Ability.reportCooldown(): String {
    return effects.map { it.cooldown }.toSet()
        .takeIf { it.size == 1 }
        ?.first()?.toString()
        ?: "Varies"
}

@Composable
private fun Collection<Effect.AbilityEffect>.Report(rank: Rank = Rank.Diamond) {
    val effectsByRank = groupBy { it.rank }
    for (r in Rank.Unranked.ordinal..rank.ordinal) {
        val currentRank = Rank.values()[r]
        val effectsOfRank = effectsByRank.getOrDefault(currentRank, emptyList())
        if (effectsOfRank.isNotEmpty()) {
            Text(text = "Effect (${currentRank.name}): ${effectsOfRank.joinToString("\n") { it.description }}")
        }
    }
}
