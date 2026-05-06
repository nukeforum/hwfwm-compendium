package wizardry.compendium.essences.model

data class RankedEffectLine(
    val rank: Rank,
    val effects: List<Effect.AbilityEffect>,
)

/**
 * Returns the visible effect lines for an ability listing, given a rank ceiling.
 *
 *  - `ceiling == null` means show every rank.
 *  - Otherwise effects with rank.ordinal > ceiling.ordinal are filtered out.
 *  - `replacementKey` groups (within this list — keys do NOT cross listings) collapse
 *    so only the highest-rank effect within the ceiling renders, displayed at the slot
 *    of the lowest-rank member of the group across the full listing definition.
 *
 * Lines are emitted in ascending rank order. Within a line, effects keep their
 * contributor-authored declaration order from the input list.
 */
fun List<Effect.AbilityEffect>.viewAt(ceiling: Rank?): List<RankedEffectLine> {
    val maxOrdinal = ceiling?.ordinal ?: Int.MAX_VALUE

    val groupedByRank = filter { it.rank.ordinal <= maxOrdinal }
        .groupBy { it.rank }
        .toSortedMap(compareBy { it.ordinal })

    return groupedByRank.map { (rank, effects) ->
        RankedEffectLine(rank, effects)
    }
}
