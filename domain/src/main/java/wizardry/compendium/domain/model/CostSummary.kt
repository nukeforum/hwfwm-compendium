package wizardry.compendium.domain.model

sealed interface CostSummary {
    data object None : CostSummary
    data class Single(val cost: Cost) : CostSummary
    data object Varies : CostSummary
}

/**
 * Summarizes the cost across a list of ability effects per the five reporting rules:
 *
 *  1. No effects with a non-None Cost → [CostSummary.None].
 *  2. Exactly one effect with a non-None Cost → [CostSummary.Single].
 *  3. Replacement-key groups with differing costs collapse to the highest-rank cost
 *     within the viewable ceiling. This rule is honored by the caller passing
 *     post-`viewAt` input — the helper itself does not look at replacement keys.
 *  4. Multiple effects with the same non-None Cost → [CostSummary.Single].
 *  5. Multiple effects with differing non-None Costs → [CostSummary.Varies].
 *
 * `Cost.None` entries are filtered out before evaluating the rules.
 */
fun List<Effect.AbilityEffect>.summarizeCost(): CostSummary {
    val realCosts = flatMap { it.cost }.filterNot { it is Cost.None }
    return when {
        realCosts.isEmpty() -> CostSummary.None
        realCosts.toSet().size == 1 -> CostSummary.Single(realCosts.first())
        else -> CostSummary.Varies
    }
}

fun CostSummary.render(): String = when (this) {
    CostSummary.None -> "None"
    is CostSummary.Single -> cost.toString()
    CostSummary.Varies -> "Varies"
}
