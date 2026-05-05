package wizardry.compendium.essences

import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank

/**
 * Convert an essence manifestation to a stone shape so it can sit
 * alongside canonical/contributed stones in awakening-stone listings.
 *
 * Properties keep `Essence` so anything filtering by it still works.
 */
fun Essence.Manifestation.toAwakeningStone(): AwakeningStone = AwakeningStone(
    name = name,
    rank = Rank.Unranked,
    rarity = rarity,
    properties = listOf(Property.Consumable, Property.Essence),
    effects = emptyList(),
    description = description,
)

/**
 * Filter manifestations down to those that don't already correspond to
 * an existing stone. Match rule (case-insensitive, trimmed):
 *   - exact name match, OR
 *   - one name is a prefix of the other AND the shorter name is ≥ 3 chars.
 *
 * The prefix rule catches "Dark" essence ↔ "Darkness" stone without
 * over-matching tiny tokens like "I" → every "I…" stone.
 */
fun manifestationsNotMatchingStones(
    manifestations: List<Essence.Manifestation>,
    stones: List<AwakeningStone>,
): List<Essence.Manifestation> {
    val stoneKeys = stones.map { it.name.normalizedKey() }
    return manifestations.filterNot { m ->
        val key = m.name.normalizedKey()
        stoneKeys.any { it.matchesEssenceKey(key) }
    }
}

private fun String.normalizedKey(): String = trim().lowercase()

private fun String.matchesEssenceKey(essenceKey: String): Boolean {
    if (this == essenceKey) return true
    val shorter = if (length < essenceKey.length) this else essenceKey
    val longer = if (length < essenceKey.length) essenceKey else this
    if (shorter.length < 3) return false
    return longer.startsWith(shorter)
}
