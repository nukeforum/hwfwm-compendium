package wizardry.compendium.ability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import wizardry.compendium.ability.preview.LocalStatusEffects
import wizardry.compendium.ability.preview.annotatedDescription
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.RankedEffectLine
import wizardry.compendium.domain.model.Resource
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.domain.model.collectLinkedStatusEffects
import wizardry.compendium.domain.model.render
import wizardry.compendium.domain.model.summarizeCost
import wizardry.compendium.domain.model.viewAt
import kotlin.time.Duration

/**
 * `ceiling` is honored for [Ability.Listing]. For [Ability.Acquired] it is ignored —
 * an acquired ability's own rank is always its ceiling.
 */
@Composable
internal fun Report(ability: Ability, ceiling: Rank? = null) {
    when (ability) {
        is Ability.Acquired -> Report(ability)
        is Ability.Listing -> Report(ability, ceiling)
    }
}

@Composable
private fun Report(acquiredAbility: Ability.Acquired) {
    val rankedLines = acquiredAbility.effects.viewAt(acquiredAbility.rank)
    val visibleEffects = rankedLines.flatMap { it.effects }
    acquiredAbility.Report(
        visibleEffects = visibleEffects,
        titleSlot = { Text(text = "Ability: ${acquiredAbility.name} (${acquiredAbility.boundEssence.name})") },
        progressSlot = {
            Text(text = "Current Rank: ${acquiredAbility.rank} ${acquiredAbility.tier}(${acquiredAbility.progress * 100}%)")
            Spacer(modifier = Modifier.height(12.dp))
        },
        effectsSlot = { rankedLines.RenderRankLines() },
    )
}

@Composable
private fun Report(abilityListing: Ability.Listing, ceiling: Rank?) {
    val rankedLines = abilityListing.effects.viewAt(ceiling)
    val visibleEffects = rankedLines.flatMap { it.effects }
    abilityListing.Report(
        visibleEffects = visibleEffects,
        titleSlot = { Text(text = "Ability: ${abilityListing.name}") },
        effectsSlot = { rankedLines.RenderRankLines() },
    )
}

@Composable
private fun Ability.Report(
    visibleEffects: List<Effect.AbilityEffect>,
    titleSlot: @Composable () -> Unit = {},
    progressSlot: @Composable () -> Unit = {},
    effectsSlot: @Composable () -> Unit = {},
) {
    Column {
        titleSlot()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "${visibleEffects.reportType()} (${visibleEffects.reportProperties()})")
        Text(text = "Cost: ${visibleEffects.reportCost()}.")
        Text(text = "Cooldown: ${visibleEffects.reportCooldown()}.")
        Spacer(modifier = Modifier.height(12.dp))
        progressSlot()
        effectsSlot()
        LinkedStatusEffectsSection(visibleEffects)
    }
}

private fun List<Effect.AbilityEffect>.reportType(): String =
    map { it.type }.toSet().joinToString("/")

private fun List<Effect.AbilityEffect>.reportProperties(): String =
    flatMap { it.properties }.toSet().joinToString(", ")

private fun List<Effect.AbilityEffect>.reportCost(): String =
    summarizeCost().render()

private fun List<Effect.AbilityEffect>.reportCooldown(): String =
    map { it.cooldown }.toSet()
        .takeIf { it.size == 1 }
        ?.first()
        ?.let { if (it == Duration.ZERO) "None" else it.toString() }
        ?: "Varies"

@Composable
private fun List<RankedEffectLine>.RenderRankLines() {
    for (line in this) {
        Text(text = line.effects.annotatedRankLine(line.rank))
    }
}

@Composable
@ReadOnlyComposable
private fun List<Effect.AbilityEffect>.annotatedRankLine(rank: Rank): AnnotatedString =
    buildAnnotatedString {
        append("Effect (${rank.name}): ")
        forEachIndexed { index, effect ->
            if (index > 0) append(" ")
            append(
                annotatedDescription(
                    template = effect.description,
                    costs = effect.cost,
                    cooldown = effect.cooldownText(),
                ),
            )
        }
    }

@Composable
private fun LinkedStatusEffectsSection(effects: List<Effect.AbilityEffect>) {
    val statusEffects = LocalStatusEffects.current
    val linked = remember(effects, statusEffects) {
        collectLinkedStatusEffects(effects, statusEffects)
    }
    if (linked.isEmpty()) return
    Spacer(modifier = Modifier.height(12.dp))
    for (statusEffect in linked) {
        Text(text = statusEffect.annotatedLinkedBlock(statusEffects))
    }
}

@Composable
@ReadOnlyComposable
private fun StatusEffect.annotatedLinkedBlock(statusEffects: List<StatusEffect>): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append("[")
            append(name)
            append("]")
        }
        append(" (")
        append(typeLabel())
        for (property in properties) {
            append(", ")
            append(property.toString())
        }
        append("): ")
        append(
            annotatedDescription(
                template = description,
                costs = emptyList(),
                cooldown = "",
                statusEffects = statusEffects,
            ),
        )
    }

private fun StatusEffect.typeLabel(): String = when (type) {
    is StatusType.Affliction -> "affliction"
    is StatusType.Boon -> "boon"
}

private fun Effect.AbilityEffect.cooldownText(): String =
    if (cooldown == Duration.ZERO) "" else cooldown.toString()

@PreviewLightDark
@Composable
private fun Foo() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
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
                        "Conjures a magical cloak that can alter the wearer. Offers limited physical protection. Can generate light or blend into shadows.",
                    ),
                    Effect.AbilityEffect(
                        Rank.Iron,
                        AbilityType.Conjuration,
                        listOf(Property.Darkness, Property.Light, Property.Dimension),
                        listOf(Cost.Ongoing(Amount.Low, Resource.Mana)),
                        Duration.ZERO,
                        "Cloak can reduce the weight of the wearer for a low mana-per-second cost, allowing reduced falling speed and water-walking.",
                    ),
                    Effect.AbilityEffect(
                        Rank.Iron,
                        AbilityType.Conjuration,
                        listOf(Property.Darkness, Property.Light, Property.Dimension),
                        listOf(Cost.None),
                        Duration.ZERO,
                        "Cannot be given or taken away, although effects can be extended to others in very close proximity.",
                    ),
                ),
            ),
        )
    }
}
