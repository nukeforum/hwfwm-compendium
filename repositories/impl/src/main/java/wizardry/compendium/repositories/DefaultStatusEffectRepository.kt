package wizardry.compendium.repositories

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.essences.dataloader.StatusEffectDataLoader
import wizardry.compendium.repositories.detectStatusEffectConflicts
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.Canonical
import wizardry.compendium.persistence.Contributions
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.preferences.StatusEffectContributionsToggle
import wizardry.compendium.preferences.StatusEffectContributionsToggleFlow

@Singleton
internal class DefaultStatusEffectRepository @Inject constructor(
    private val dataLoader: StatusEffectDataLoader,
    @param:Canonical private val canonicalCache: StatusEffectCache,
    @param:Contributions private val contributionsCache: StatusEffectCache,
    @param:Contributions private val abilityListingContributionsCache: AbilityListingCache,
    private val toggle: StatusEffectContributionsToggle,
    toggleFlow: StatusEffectContributionsToggleFlow,
) : StatusEffectRepository {

    private val writeMutex = Mutex()
    private val invalidations = MutableStateFlow(0)

    override val statusEffects: Flow<List<StatusEffect>> = combine(
        toggleFlow.statusEffectContributionsEnabled,
        invalidations,
    ) { _, _ -> getStatusEffects() }

    override val conflicts: Flow<List<StatusEffectConflict>> = combine(
        toggleFlow.statusEffectContributionsEnabled,
        invalidations,
    ) { _, _ -> getConflicts() }

    override suspend fun getStatusEffects(): List<StatusEffect> {
        val canonical = ensureCanonicalLoaded()
        if (!toggle.isStatusEffectContributionsEnabled) return canonical
        val contributions = contributionsCache.contents
        if (contributions.isEmpty()) return canonical
        if (detectStatusEffectConflicts(canonical, contributions).isNotEmpty()) return canonical
        val byName = contributions.associateBy { it.name }
        val merged = canonical.map { byName[it.name] ?: it }
        val newOnes = contributions.filter { c -> canonical.none { it.name == c.name } }
        return (merged + newOnes).sortedBy { it.name }
    }

    override suspend fun getConflicts(): List<StatusEffectConflict> {
        val canonical = ensureCanonicalLoaded()
        return detectStatusEffectConflicts(canonical, contributionsCache.contents)
    }

    override suspend fun getContributions(): List<StatusEffect> = contributionsCache.contents

    override suspend fun saveStatusEffectContribution(
        effect: StatusEffect,
    ): ContributionResult = writeMutex.withLock {
        val canonical = ensureCanonicalLoaded()
        val existing = contributionsCache.contents
        val key = effect.name.normalized()
        val canonicalNames = canonical.map { it.name.normalized() }.toSet()
        val contributedNames = existing.map { it.name.normalized() }.toSet()
        if (key in canonicalNames || key in contributedNames) {
            return@withLock ContributionResult.Failure(
                "A status effect named \"${effect.name}\" already exists"
            )
        }
        contributionsCache.insert(effect)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun isContribution(name: String): Boolean = writeMutex.withLock {
        val key = name.normalized()
        contributionsCache.contents.any { it.name.normalized() == key }
    }

    override suspend fun deleteContribution(name: String): ContributionResult = writeMutex.withLock {
        val key = name.normalized()
        val existing = contributionsCache.contents
        val target = existing.firstOrNull { it.name.normalized() == key }
            ?: return@withLock ContributionResult.Failure(
                "No contribution exists for \"$name\""
            )
        val id = contributionsCache.findIdByName(target.name)
            ?: return@withLock ContributionResult.Failure("No contribution exists for \"$name\"")
        contributionsCache.deleteById(id)
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun updateStatusEffectContribution(
        originalName: String,
        effect: StatusEffect,
    ): ContributionResult = writeMutex.withLock {
        val id = contributionsCache.findIdByName(originalName)
            ?: return@withLock ContributionResult.Failure(
                "No contributed status effect named \"$originalName\""
            )
        val nameChanged = !effect.name.equals(originalName, ignoreCase = true)
        if (nameChanged) {
            val canonical = ensureCanonicalLoaded()
            val key = effect.name.normalized()
            val canonicalNames = canonical.map { it.name.normalized() }.toSet()
            val contributedNames = contributionsCache.identified
                .map { it.statusEffect.name.normalized() }
                .toSet() - originalName.normalized()
            if (key in canonicalNames || key in contributedNames) {
                return@withLock ContributionResult.Failure(
                    "A status effect named \"${effect.name}\" already exists"
                )
            }
        }
        contributionsCache.update(id, effect)
        if (nameChanged) {
            rewriteStatusTokens(originalName = originalName, newName = effect.name)
        }
        invalidations.update { it + 1 }
        ContributionResult.Success
    }

    override suspend fun checkDeleteImpact(name: String): DeleteImpact {
        val tokenRegex = Regex("""\{status:([^}]+)\}""", RegexOption.IGNORE_CASE)
        val target = name.normalized()
        val abilityNames = abilityListingContributionsCache.identified
            .filter { (_, listing) ->
                listing.effects.any { effect ->
                    tokenRegex.findAll(effect.description).any { match ->
                        match.groupValues[1].normalized() == target
                    }
                }
            }
            .map { it.listing.name }
            .distinct()
            .sorted()
        return DeleteImpact(referencingAbilityListings = abilityNames)
    }

    /**
     * Rewrite `{status:ORIGINALNAME}` tokens in contributed ability descriptions
     * to `{status:NEWNAME}`. Prefix-safe: only exact (case-insensitive) inner-name
     * matches are rewritten. E.g., renaming "Burn" does NOT affect "{status:Burning}".
     *
     * The scan + per-row updates run inside a single SQLite transaction (see
     * [AbilityListingCache.bulkRewriteStatusTokens]) so a crash between the
     * status-row rename and the dependent description rewrites cannot leave
     * orphan `{status:OldName}` tokens — either both commit or neither does.
     */
    private fun rewriteStatusTokens(originalName: String, newName: String) {
        val tokenRegex = Regex("""\{status:([^}]+)\}""", RegexOption.IGNORE_CASE)
        abilityListingContributionsCache.bulkRewriteStatusTokens { _, description ->
            var changed = false
            val rewritten = tokenRegex.replace(description) { match ->
                val captured = match.groupValues[1]
                if (captured.equals(originalName, ignoreCase = true)) {
                    changed = true
                    "{status:$newName}"
                } else {
                    match.value
                }
            }
            if (changed) rewritten else null
        }
    }

    private suspend fun ensureCanonicalLoaded(): List<StatusEffect> {
        val current = canonicalCache.contents
        if (current.isNotEmpty()) return current
        val loaded = dataLoader.loadStatusEffectData()
        canonicalCache.replaceAll(loaded)
        return loaded
    }

    private fun String.normalized(): String = trim().lowercase()
}
