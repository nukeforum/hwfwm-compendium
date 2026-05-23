package wizardry.compendium.wire

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.StatusEffect as ModelStatusEffect

class WireExporterBuildTest {

    private val exporter = WireExporter(
        essenceRepository = BuildTestStubEssenceRepository(),
        awakeningStoneRepository = BuildTestStubAwakeningStoneRepository(),
        abilityListingRepository = BuildTestStubAbilityListingRepository(),
        statusEffectRepository = BuildTestStubStatusEffectRepository(),
    )

    @Test
    fun `exportSingle build emits envelope with one build and nothing else`() {
        val build = CharacterBuild(name = "Test", race = "Human", racialAbilities = emptyList())
        val envelope = exporter.exportSingle(build)
        assertEquals(EnvelopeCodec.CurrentVersion, envelope.version)
        assertEquals(1, envelope.builds.size)
        assertEquals("Test", envelope.builds.single().name)
        assertEquals(0, envelope.manifestations.size)
        assertEquals(0, envelope.confluences.size)
        assertEquals(0, envelope.stones.size)
        assertEquals(0, envelope.listings.size)
        assertEquals(0, envelope.statusEffects.size)
    }
}

private class BuildTestStubEssenceRepository : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = emptyList()
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult = ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(
        confluence: Essence.Confluence,
    ): ContributionResult = ContributionResult.Success
}

private class BuildTestStubAwakeningStoneRepository : AwakeningStoneRepository {
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
    override suspend fun getContributions(): List<AwakeningStone> = emptyList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
        ContributionResult.Success
}

private class BuildTestStubAbilityListingRepository : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private class BuildTestStubStatusEffectRepository : StatusEffectRepository {
    override val statusEffects: Flow<List<ModelStatusEffect>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
    override suspend fun getStatusEffects(): List<ModelStatusEffect> = emptyList()
    override suspend fun getContributions(): List<ModelStatusEffect> = emptyList()
    override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
    override suspend fun saveStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
}
