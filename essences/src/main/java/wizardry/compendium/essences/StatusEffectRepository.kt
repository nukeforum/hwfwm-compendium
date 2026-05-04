package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.StatusEffect

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

    suspend fun updateStatusEffectContribution(effect: StatusEffect): ContributionResult
}
