package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence

interface EssenceRepository {
    val essences: Flow<List<Essence>>

    val conflicts: Flow<List<EssenceConflict>>

    suspend fun getEssences(): List<Essence>

    /**
     * Returns the user's contributed essences only (no canonical entries).
     * Used by the export flow to build a wire envelope of the user's data.
     */
    suspend fun getContributions(): List<Essence>

    suspend fun getConflicts(): List<EssenceConflict>

    suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult

    suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult

    suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult

    suspend fun isContribution(name: String): Boolean

    suspend fun deleteContribution(name: String): ContributionResult

    suspend fun updateManifestationContribution(
        originalName: String,
        manifestation: Essence.Manifestation,
    ): ContributionResult

    suspend fun updateConfluenceContribution(
        originalName: String,
        confluence: Essence.Confluence,
    ): ContributionResult

    suspend fun checkDeleteImpact(name: String): DeleteImpact
}
