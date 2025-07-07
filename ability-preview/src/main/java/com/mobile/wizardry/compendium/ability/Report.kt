package com.mobile.wizardry.compendium.ability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mobile.wizardry.compendium.essences.model.Ability
import com.mobile.wizardry.compendium.essences.model.AbilityType
import com.mobile.wizardry.compendium.essences.model.Amount
import com.mobile.wizardry.compendium.essences.model.Cost
import com.mobile.wizardry.compendium.essences.model.Effect
import com.mobile.wizardry.compendium.essences.model.Property
import com.mobile.wizardry.compendium.essences.model.Rank
import com.mobile.wizardry.compendium.essences.model.Resource

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
            Text(text = "Current Rank: $rank ${tier}(${progress * 100}%)")
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
    Column {
        titleSlot()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "${reportType()} (${reportProperties()})")
        Text(text = "Cost: ${reportCost()}.")
        Text(text = "Cooldown: ${reportCooldown()}.")
        Spacer(modifier = Modifier.height(12.dp))
        progressSlot()
        effectsSlot()
    }
}

private fun Ability.reportType(): String {
    return effects.map { it.type }.toSet().joinToString("/")
}

private fun Ability.reportProperties(): String {
    return effects.flatMap { it.properties }.toSet().joinToString(", ")
}

private fun Ability.reportCost(): String {
    return effects.flatMap { effect -> effect.cost }
        .runCatching { single { it is Cost.Upfront } }
        .getOrNull()
        ?.toString()
        ?: "Varies"
}

private fun Ability.reportCooldown(): String {
    return effects.map { it.cooldown }.toSet()
        .takeIf { it.size == 1 }
        ?.first()
        ?.let {
            if (it == 0) {
                "None"
            } else {
                it.toString()
            }
        }
        ?: "Varies"
}

@Composable
private fun Collection<Effect.AbilityEffect>.Report(rank: Rank = Rank.Diamond) {
    val effectsByRank = groupBy { it.rank }
    for (r in Rank.Unranked.ordinal..rank.ordinal) {
        val currentRank = Rank.entries[r]
        val effectsOfRank = effectsByRank.getOrDefault(currentRank, emptyList())
        if (effectsOfRank.isNotEmpty()) {
            Text(text = "Effect (${currentRank.name}): ${effectsOfRank.joinToString(" ") { it.description }}")
        }
    }
}

@Preview
@Composable
private fun Foo() {
    Ability.Listing(
        "Cloak of Night",
        listOf(
            Effect.AbilityEffect(
                Rank.Iron,
                AbilityType.Conjuration,
                listOf(Property.Darkness, Property.Light, Property.Dimension),
                listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
                0,
                "Conjures a magical cloak that can alter the wearer. Offers limited physical protection. Can generate light or blend into shadows."
            ),
            Effect.AbilityEffect(
                Rank.Iron,
                AbilityType.Conjuration,
                listOf(Property.Darkness, Property.Light, Property.Dimension),
                listOf(Cost.Ongoing(Amount.Low, Resource.Mana)),
                0,
                "Cloak can reduce the weight of the wearer for a low mana-per-second cost, allowing reduced falling speed and water-walking."
            ),
            Effect.AbilityEffect(
                Rank.Iron,
                AbilityType.Conjuration,
                listOf(Property.Darkness, Property.Light, Property.Dimension),
                listOf(Cost.None),
                0,
                "Cannot be given or taken away, although effects can be extended to others in very close proximity."
            )
        )
    ).Report()
}
