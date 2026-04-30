package wizardry.compendium.ability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Cost
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Resource
import wizardry.compendium.essences.model.resolveDescription
import kotlin.time.Duration

@Composable
internal fun Report(ability: Ability) {
    when (ability) {
        is Ability.Acquired -> Report(ability)
        is Ability.Listing -> Report(ability)
    }
}

@Composable
private fun Report(acquiredAbility: Ability.Acquired) {
    acquiredAbility.Report(
        titleSlot = { Text(text = "Ability: ${acquiredAbility.name} (${acquiredAbility.boundEssence.name})") },
        progressSlot = {
            Text(text = "Current Rank: ${acquiredAbility.rank} ${acquiredAbility.tier}(${acquiredAbility.progress * 100}%)")
            Spacer(modifier = Modifier.height(12.dp))
        },
        effectsSlot = { acquiredAbility.effects.Report(acquiredAbility.rank) },
    )
}

@Composable
private fun Report(abilityListing: Ability.Listing) {
    abilityListing.Report(
        titleSlot = {
            Text(text = "Ability: ${abilityListing.name}")
        },
        effectsSlot = { abilityListing.effects.Report() },
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
            if (it == Duration.ZERO) {
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
            Text(text = "Effect (${currentRank.name}): ${effectsOfRank.joinToString(" ") { it.resolveDescription() }}")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Foo() {
    Report(
        Ability.Listing(
            "Cloak of Night",
            listOf(
                Effect.AbilityEffect(
                    Rank.Iron,
                    AbilityType.Conjuration,
                    listOf(Property.Darkness, Property.Light, Property.Dimension),
                    listOf(Cost.Upfront(Amount.Moderate, Resource.Mana)),
                    Duration.ZERO,
                    "Conjures a magical cloak that can alter the wearer. Offers limited physical protection. Can generate light or blend into shadows."
                ),
                Effect.AbilityEffect(
                    Rank.Iron,
                    AbilityType.Conjuration,
                    listOf(Property.Darkness, Property.Light, Property.Dimension),
                    listOf(Cost.Ongoing(Amount.Low, Resource.Mana)),
                    Duration.ZERO,
                    "Cloak can reduce the weight of the wearer for a low mana-per-second cost, allowing reduced falling speed and water-walking."
                ),
                Effect.AbilityEffect(
                    Rank.Iron,
                    AbilityType.Conjuration,
                    listOf(Property.Darkness, Property.Light, Property.Dimension),
                    listOf(Cost.None),
                    Duration.ZERO,
                    "Cannot be given or taken away, although effects can be extended to others in very close proximity."
                )
            )
        )
    )
}
