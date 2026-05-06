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
 * contributor-authored declaration order from the input list — except that the
 * winner of a replacement-key group takes the position of the group's first-declared
 * effect at the slot rank.
 */
fun List<Effect.AbilityEffect>.viewAt(ceiling: Rank?): List<RankedEffectLine> {
    val maxOrdinal = ceiling?.ordinal ?: Int.MAX_VALUE

    // Group by replacement key (only non-null/non-blank keys group). Use the original
    // list order so the slot-rank lookup uses the full listing definition, not the
    // post-ceiling subset.
    val groups: Map<String, List<Effect.AbilityEffect>> = this
        .filter { !it.replacementKey.isNullOrBlank() }
        .groupBy { it.replacementKey!! }

    // For each group, pick the winner (highest rank within ceiling) and slot it
    // at the lowest rank of the group's full definition. Groups whose lowest-rank
    // member is above the ceiling are dropped entirely.
    val winnerByGroup: Map<String, Effect.AbilityEffect?> = groups.mapValues { (_, members) ->
        members
            .filter { it.rank.ordinal <= maxOrdinal }
            .maxByOrNull { it.rank.ordinal }
    }
    val slotRankByGroup: Map<String, Rank> = groups.mapValues { (_, members) ->
        members.minBy { it.rank.ordinal }.rank
    }

    // Walk the original list once, emitting either the effect itself (for ungrouped
    // effects within ceiling) or the group's winner (only when we hit the group's
    // first-declared effect — this preserves contributor order within each rank line).
    val seenGroups = mutableSetOf<String>()
    val displayed: List<Pair<Rank, Effect.AbilityEffect>> = buildList {
        for (effect in this@viewAt) {
            val key = effect.replacementKey?.takeIf { it.isNotBlank() }
            if (key == null) {
                if (effect.rank.ordinal <= maxOrdinal) {
                    add(effect.rank to effect)
                }
                continue
            }
            if (key in seenGroups) continue
            seenGroups += key
            val winner = winnerByGroup[key] ?: continue // entire group above ceiling
            val slotRank = slotRankByGroup.getValue(key)
            add(slotRank to winner)
        }
    }

    return displayed
        .groupBy({ it.first }, { it.second })
        .toSortedMap(compareBy { it.ordinal })
        .map { (rank, effects) -> RankedEffectLine(rank, effects) }
}
