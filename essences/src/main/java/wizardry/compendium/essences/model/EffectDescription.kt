package wizardry.compendium.essences.model

import kotlin.time.Duration

private val TOKEN_REGEX = Regex("\\{(cooldown|cost\\d*|status:[^}]+)\\}")

sealed interface DescriptionSegment {
    data class Literal(val text: String) : DescriptionSegment

    /** A token like `{cost}` that resolved to a value. */
    data class Resolved(val token: String, val value: String) : DescriptionSegment

    /** A token like `{cost3}` or `{status:Foo}` that did not resolve. */
    data class Unresolved(val token: String) : DescriptionSegment

    /** A token like `{status:Bleeding}` that resolved to a known status effect. */
    data class StatusLink(
        val token: String,
        val name: String,
        val target: StatusEffect,
    ) : DescriptionSegment
}

/**
 * Walks a description template, splitting it into literal text and tokens.
 *
 * Supported tokens:
 *  - `{cost}`        — first cost (equivalent to `{cost1}`)
 *  - `{cost2}`...    — Nth cost (1-indexed)
 *  - `{cooldown}`    — formatted cooldown
 *  - `{status:Name}` — link to a status effect with that name
 */
fun parseDescription(
    template: String,
    costs: List<Cost>,
    cooldown: String,
    statusEffects: List<StatusEffect> = emptyList(),
): List<DescriptionSegment> {
    val out = mutableListOf<DescriptionSegment>()
    var index = 0
    for (match in TOKEN_REGEX.findAll(template)) {
        if (match.range.first > index) {
            out += DescriptionSegment.Literal(template.substring(index, match.range.first))
        }
        val raw = match.groupValues[1]
        out += if (raw.startsWith("status:")) {
            val name = raw.removePrefix("status:")
            val target = name.takeIf { it.isNotBlank() }
                ?.let { lookup -> statusEffects.firstOrNull { it.name == lookup } }
            if (target != null) {
                DescriptionSegment.StatusLink(token = match.value, name = target.name, target = target)
            } else {
                DescriptionSegment.Unresolved(token = match.value)
            }
        } else {
            val resolved = resolveToken(raw, costs, cooldown)
            if (resolved != null) {
                DescriptionSegment.Resolved(token = match.value, value = resolved)
            } else {
                DescriptionSegment.Unresolved(token = match.value)
            }
        }
        index = match.range.last + 1
    }
    if (index < template.length) {
        out += DescriptionSegment.Literal(template.substring(index))
    }
    return out
}

fun resolveDescription(
    template: String,
    costs: List<Cost>,
    cooldown: String,
): String = buildString {
    for (segment in parseDescription(template, costs, cooldown)) {
        when (segment) {
            is DescriptionSegment.Literal -> append(segment.text)
            is DescriptionSegment.Resolved -> append(segment.value)
            is DescriptionSegment.Unresolved -> append(segment.token)
            is DescriptionSegment.StatusLink -> append(segment.token)
        }
    }
}

/**
 * Like [resolveDescription], but additionally substitutes resolved status links
 * with `[Name]`. Unresolved tokens still fall back to their raw text. Use this
 * for plain-text rendering paths where the reader will see linked names but
 * not the surrounding bracket markup token.
 */
fun resolveDescription(
    template: String,
    costs: List<Cost>,
    cooldown: String,
    statusEffects: List<StatusEffect>,
): String = buildString {
    for (segment in parseDescription(template, costs, cooldown, statusEffects)) {
        when (segment) {
            is DescriptionSegment.Literal -> append(segment.text)
            is DescriptionSegment.Resolved -> append(segment.value)
            is DescriptionSegment.Unresolved -> append(segment.token)
            is DescriptionSegment.StatusLink -> {
                append("[")
                append(segment.name)
                append("]")
            }
        }
    }
}

private fun resolveToken(token: String, costs: List<Cost>, cooldown: String): String? = when {
    token == "cooldown" -> cooldown.takeIf { it.isNotBlank() }
    token == "cost" -> costs.getOrNull(0)?.toString()
    token.startsWith("cost") -> {
        val n = token.removePrefix("cost").toIntOrNull()
        if (n != null && n >= 1) costs.getOrNull(n - 1)?.toString() else null
    }
    else -> null
}

fun Effect.AbilityEffect.resolveDescription(): String =
    resolveDescription(description, cost, cooldownDisplay(cooldown))

private fun cooldownDisplay(cooldown: Duration): String =
    if (cooldown == Duration.ZERO) "" else cooldown.toString()

/**
 * Walks each effect description and returns the unique `StatusEffect`s referenced
 * by `{status:Name}` tokens, in first-appearance order. Unresolved references are
 * skipped.
 */
fun collectLinkedStatusEffects(
    effects: List<Effect.AbilityEffect>,
    statusEffects: List<StatusEffect>,
): List<StatusEffect> {
    if (statusEffects.isEmpty()) return emptyList()
    val seen = LinkedHashSet<String>()
    val result = mutableListOf<StatusEffect>()
    for (effect in effects) {
        val cooldownText = if (effect.cooldown == Duration.ZERO) "" else effect.cooldown.toString()
        for (segment in parseDescription(effect.description, effect.cost, cooldownText, statusEffects)) {
            if (segment is DescriptionSegment.StatusLink && seen.add(segment.target.name)) {
                result += segment.target
            }
        }
    }
    return result
}
