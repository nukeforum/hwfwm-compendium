package wizardry.compendium.ability.preview

import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Amount
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Resource
import wizardry.compendium.domain.model.StatusEffect
import kotlin.math.roundToInt
import kotlin.time.Duration

/**
 * Plain-text rendering for character-build sharing. The output mirrors what
 * the Details screen displays so a recipient sees the same content the sender
 * did when pasted into Discord, Messages, etc.
 *
 * # Why a separate renderer rather than refactoring Report.kt
 *
 * Report.kt emits Compose nodes; the share path needs a String. Sharing the
 * implementation would require either pulling Compose into the share module
 * (bad) or extracting a pure model layer (worth doing eventually but out of
 * scope here). For now: a parallel renderer kept honest by integration tests.
 *
 * # Why this lives in `:ability-preview`
 *
 * `CharacterBuildShareUseCase` (in `:share`) calls this renderer to build the
 * plaintext share variant. `:share` is an Android library that already
 * depends on `:ability-preview`, so this is the natural shared home — and
 * it sits next to the parallel Compose-based renderer that drives the
 * detail screen.
 */
object AbilityTextRenderer {

    fun renderBuild(build: CharacterBuild, statusEffects: List<StatusEffect>): String {
        val pct = (build.progression * 100).roundToInt()
        val header = "${build.name}\n${build.race}\nRank: ${build.rank}     Progression: $pct%"

        val slotSections = listOf(
            "Power" to build.Power.essence,
            "Speed" to build.Speed.essence,
            "Spirit" to build.Spirit.essence,
            "Recovery" to build.Recovery.essence,
        ).joinToString("\n\n") { (label, absorbed) -> renderAttribute(label, absorbed, statusEffects) }

        val racials = if (build.racialAbilities.isEmpty()) {
            "Racial Abilities: (none)"
        } else {
            buildString {
                appendLine("Racial Abilities")
                build.racialAbilities.forEachIndexed { i, listing ->
                    if (i > 0) appendLine()
                    append(renderAbility(listing, rankCeiling = null, statusEffects = statusEffects, indent = "  "))
                }
            }.trimEnd()
        }

        return "$header\n\n$slotSections\n\n$racials"
    }

    fun renderAbility(
        ability: Ability,
        rankCeiling: Rank?,
        statusEffects: List<StatusEffect>,
        indent: String = "    ",
    ): String {
        val nameLine = when (ability) {
            is Ability.Acquired -> "${indent.dropLast(2)}${ability.name} (${ability.rank})"
            is Ability.Listing -> "${indent.dropLast(2)}${ability.name}"
        }
        val effects = ability.effects
            .let { all -> if (rankCeiling == null) all else all.filter { it.rank.ordinal <= rankCeiling.ordinal } }
        val effectLines = effects.joinToString("\n") { effect ->
            renderEffect(effect, indent, statusEffects)
        }
        return if (effectLines.isEmpty()) nameLine else "$nameLine\n$effectLines"
    }

    private fun renderAttribute(label: String, absorbed: AbsorbedEssence?, statusEffects: List<StatusEffect>): String {
        if (absorbed == null) return "$label\nEssence: (none)"
        val abilities = absorbed.abilities.joinToString("\n") {
            renderAbility(it, rankCeiling = it.rank, statusEffects = statusEffects, indent = "    ")
        }
        return if (abilities.isEmpty()) {
            "$label\nEssence: ${absorbed.essence.name}"
        } else {
            "$label\nEssence: ${absorbed.essence.name}\n$abilities"
        }
    }

    private fun renderEffect(effect: Effect.AbilityEffect, indent: String, statusEffects: List<StatusEffect>): String {
        val typeAndProps = buildString {
            append(typeLabel(effect.type))
            val propLabels = effect.properties.joinToString(", ") { it.toString() }
            if (propLabels.isNotEmpty()) append(" — ").append(propLabels)
        }
        val lines = mutableListOf("$indent$typeAndProps")
        val costLine = costLabel(effect.cost)
        if (costLine != null) lines += "$indent$costLine"
        if (effect.cooldown != Duration.ZERO) lines += "${indent}Cooldown: ${effect.cooldown}"
        if (effect.description.isNotBlank()) lines += "$indent${effect.description}"
        // Future: render linked status-effect references via `statusEffects`. Out of scope for v1.
        return lines.joinToString("\n")
    }

    private fun typeLabel(type: AbilityType): String = when (type) {
        AbilityType.SpecialAttack -> "Special Attack"
        AbilityType.SpecialAbility -> "Special Ability"
        AbilityType.RacialAbility -> "Racial Ability"
        AbilityType.Spell -> "Spell"
        AbilityType.Aura -> "Aura"
        AbilityType.Conjuration -> "Conjuration"
        AbilityType.Familiar -> "Familiar"
        AbilityType.Summoning -> "Summoning"
        AbilityType.Use -> "Use"
    }

    private fun costLabel(costs: List<Cost>): String? {
        val payable = costs.filterNot { it is Cost.None }
        if (payable.isEmpty()) return null
        val parts = payable.joinToString(", ") { c ->
            when (c) {
                is Cost.Upfront -> "${amountLabel(c.amount)} ${resourceLabel(c.resource)}"
                is Cost.Ongoing -> "${amountLabel(c.amount)} ${resourceLabel(c.resource)}/sec"
                is Cost.None -> ""
            }
        }
        return "Cost: $parts"
    }

    private fun amountLabel(a: Amount): String = when (a) {
        Amount.None -> "No"
        Amount.VeryLow -> "Very Low"
        Amount.Low -> "Low"
        Amount.Moderate -> "Moderate"
        Amount.High -> "High"
        Amount.VeryHigh -> "Very High"
        Amount.Extreme -> "Extreme"
        Amount.BeyondExtreme -> "Beyond Extreme"
    }

    private fun resourceLabel(r: Resource): String = when (r) {
        Resource.Mana -> "Mana"
        Resource.Stamina -> "Stamina"
        Resource.Health -> "Health"
    }
}
