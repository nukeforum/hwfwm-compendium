package wizardry.compendium.essences

import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.StatusEffect

sealed interface Conflict {
    val title: String
    val summary: String
    val key: ConflictKey
}

data class ConflictKey(val domain: String, val identifier: String)

sealed interface EssenceConflict : Conflict {
    data class NameCollision(
        val contribution: Essence,
        val canonical: Essence,
    ) : EssenceConflict {
        override val title get() = contribution.name
        override val summary get() = "Name conflicts with canonical ${canonical.kindLabel()}"
        override val key get() = ConflictKey(DOMAIN, "name::${contribution.name}")
    }

    data class CombinationCollision(
        val contribution: Essence.Confluence,
        val combination: ConfluenceSet,
        val canonicalOwner: Essence.Confluence,
    ) : EssenceConflict {
        override val title get() = contribution.name
        override val summary
            get() = "${combination.set.joinToString(" + ") { it.name }} now produces ${canonicalOwner.name}"
        override val key
            get() = ConflictKey(
                DOMAIN,
                "combination::${contribution.name}::${combination.set.joinToString(",") { it.name }}",
            )
    }

    companion object {
        const val DOMAIN: String = "essence"
    }
}

sealed interface AwakeningStoneConflict : Conflict {
    data class NameCollision(
        val contribution: AwakeningStone,
        val canonical: AwakeningStone,
    ) : AwakeningStoneConflict {
        override val title get() = contribution.name
        override val summary get() = "Name conflicts with a canonical awakening stone"
        override val key get() = ConflictKey(DOMAIN, "name::${contribution.name}")
    }

    companion object {
        const val DOMAIN: String = "awakening-stone"
    }
}

sealed interface AbilityListingConflict : Conflict {
    data class NameCollision(
        val contribution: Ability.Listing,
        val canonical: Ability.Listing,
    ) : AbilityListingConflict {
        override val title get() = contribution.name
        override val summary get() = "Name conflicts with a canonical ability listing"
        override val key get() = ConflictKey(DOMAIN, "name::${contribution.name}")
    }

    companion object {
        const val DOMAIN: String = "ability-listing"
    }
}

private fun Essence.kindLabel(): String = when (this) {
    is Essence.Manifestation -> "essence"
    is Essence.Confluence -> "confluence"
    else -> "entry"
}

private fun String.normalizedName(): String = trim().lowercase()

fun detectEssenceConflicts(
    canonical: List<Essence>,
    contributions: List<Essence>,
): List<EssenceConflict> {
    if (contributions.isEmpty()) return emptyList()
    val canonicalByName = canonical.associateBy { it.name.normalizedName() }
    val conflicts = mutableListOf<EssenceConflict>()

    for (contribution in contributions) {
        val canonicalMatch = canonicalByName[contribution.name.normalizedName()]
        if (canonicalMatch != null) {
            conflicts += EssenceConflict.NameCollision(contribution, canonicalMatch)
        }
    }

    val canonicalConfluences = canonical.filterIsInstance<Essence.Confluence>()
    for (contribution in contributions.filterIsInstance<Essence.Confluence>()) {
        for (combination in contribution.confluenceSets) {
            val combinationKey = combination.set.map { it.name.normalizedName() }.toSet()
            val owner = canonicalConfluences.firstOrNull { canonicalConf ->
                if (canonicalConf.name.normalizedName() == contribution.name.normalizedName()) return@firstOrNull false
                canonicalConf.confluenceSets.any { set ->
                    set.set.map { it.name.normalizedName() }.toSet() == combinationKey
                }
            } ?: continue
            conflicts += EssenceConflict.CombinationCollision(
                contribution = contribution,
                combination = combination,
                canonicalOwner = owner,
            )
        }
    }

    return conflicts
}

fun detectAwakeningStoneConflicts(
    canonical: List<AwakeningStone>,
    contributions: List<AwakeningStone>,
): List<AwakeningStoneConflict> {
    if (contributions.isEmpty()) return emptyList()
    val canonicalByName = canonical.associateBy { it.name.normalizedName() }
    return contributions.mapNotNull { contribution ->
        val match = canonicalByName[contribution.name.normalizedName()] ?: return@mapNotNull null
        AwakeningStoneConflict.NameCollision(contribution, match)
    }
}

fun detectAbilityListingConflicts(
    canonical: List<Ability.Listing>,
    contributions: List<Ability.Listing>,
): List<AbilityListingConflict> {
    if (contributions.isEmpty()) return emptyList()
    val canonicalByName = canonical.associateBy { it.name.normalizedName() }
    return contributions.mapNotNull { contribution ->
        val match = canonicalByName[contribution.name.normalizedName()] ?: return@mapNotNull null
        AbilityListingConflict.NameCollision(contribution, match)
    }
}

sealed interface StatusEffectConflict : Conflict {
    data class NameCollision(
        val contribution: StatusEffect,
        val canonical: StatusEffect,
    ) : StatusEffectConflict {
        override val title get() = contribution.name
        override val summary get() = "Name conflicts with a canonical status effect"
        override val key get() = ConflictKey(DOMAIN, "name::${contribution.name}")
    }

    companion object {
        const val DOMAIN: String = "status-effect"
    }
}

fun detectStatusEffectConflicts(
    canonical: List<StatusEffect>,
    contributions: List<StatusEffect>,
): List<StatusEffectConflict> {
    if (contributions.isEmpty()) return emptyList()
    val canonicalByName = canonical.associateBy { it.name.normalizedName() }
    return contributions.mapNotNull { contribution ->
        val match = canonicalByName[contribution.name.normalizedName()] ?: return@mapNotNull null
        StatusEffectConflict.NameCollision(contribution, match)
    }
}
