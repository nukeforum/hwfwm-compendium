package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence

interface EssenceRepository {
    val essences: Flow<List<Essence>>

    suspend fun getEssences(): List<Essence>

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
        manifestation: Essence.Manifestation,
    ): ContributionResult

    suspend fun updateConfluenceContribution(
        confluence: Essence.Confluence,
    ): ContributionResult
}
