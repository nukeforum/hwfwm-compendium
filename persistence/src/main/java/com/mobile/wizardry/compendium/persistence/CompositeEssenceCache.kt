package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.Essence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeEssenceCache @Inject constructor(
    @Canonical private val canonicalCache: EssenceCache,
    @Contributions private val contributionsCache: EssenceCache,
    private val toggle: ContributionsToggle,
) : EssenceCache {

    override var contents: List<Essence>
        get() {
            val canonical = canonicalCache.contents
            if (!toggle.isContributionsEnabled) return canonical

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
