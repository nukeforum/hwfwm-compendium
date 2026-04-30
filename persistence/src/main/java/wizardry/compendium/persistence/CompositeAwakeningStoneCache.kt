package wizardry.compendium.persistence

import wizardry.compendium.essences.model.AwakeningStone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeAwakeningStoneCache @Inject constructor(
    @param:Canonical private val canonicalCache: AwakeningStoneCache,
    @param:Contributions private val contributionsCache: AwakeningStoneCache,
    private val toggle: AwakeningStoneContributionsToggle,
) : AwakeningStoneCache {

    override var contents: List<AwakeningStone>
        get() {
            val canonical = canonicalCache.contents
            if (!toggle.isAwakeningStoneContributionsEnabled) return canonical

            val contributions = contributionsCache.contents
            if (contributions.isEmpty()) return canonical

            val byName = contributions.associateBy { it.name }
            val merged = canonical.map { byName[it.name] ?: it }
            val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
            return (merged + newOnes).sortedBy { it.name }
        }
        set(value) {
            canonicalCache.contents = value
        }
}
