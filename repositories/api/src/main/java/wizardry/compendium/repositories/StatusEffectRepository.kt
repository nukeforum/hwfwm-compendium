package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.domain.model.StatusEffect

interface StatusEffectRepository {
    val statusEffects: Flow<List<StatusEffect>>

    val conflicts: Flow<List<StatusEffectConflict>>

    suspend fun getStatusEffects(): List<StatusEffect>

    /** Returns only user-contributed status effects (no canonical entries). */
    suspend fun getContributions(): List<StatusEffect>

    suspend fun getConflicts(): List<StatusEffectConflict>

    suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult

    suspend fun isContribution(name: String): Boolean

    suspend fun deleteContribution(name: String): ContributionResult

    /**
     * Update a contributed status effect. When [effect.name] differs from
     * [originalName] (case-insensitive), the repository performs a cascade
     * rewrite of all `{status:ORIGINALNAME}` tokens found in contributed
     * ability descriptions, atomically within the same write-mutex block.
     */
    suspend fun updateStatusEffectContribution(
        originalName: String,
        effect: StatusEffect,
    ): ContributionResult

    /**
     * Scan contributed ability descriptions for `{status:NAME}` tokens that
     * reference this status effect. Returns the names of any abilities found.
     */
    suspend fun checkDeleteImpact(name: String): DeleteImpact
}
