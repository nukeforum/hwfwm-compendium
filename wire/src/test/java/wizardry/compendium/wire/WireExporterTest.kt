package wizardry.compendium.wire

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect as ModelStatusEffect

class WireExporterTest {

    private val manifestation = Essence.Manifestation(
        name = "Wind",
        rank = Rank.Iron,
        rarity = Rarity.Common,
        properties = emptyList(),
        description = "",
        isRestricted = false,
    )

    private val stone = AwakeningStone(
        name = "Volcano",
        rank = Rank.Unranked,
        rarity = Rarity.Epic,
        properties = emptyList(),
        effects = emptyList(),
        description = "",
    )

    private val essenceRepo = StubEssenceRepository(contributions = listOf(manifestation))
    private val stoneRepo = StubAwakeningStoneRepository(contributions = listOf(stone))
    private val listingRepo = StubAbilityListingRepository(contributions = emptyList())
    private val effectRepo = StubStatusEffectRepository(contributions = emptyList())

    private val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, effectRepo)

    @Test
    fun `exportFiltered with empty selection returns envelope with all lists empty`() = runTest {
        val envelope = exporter.exportFiltered(emptySet())

        assertEquals(EnvelopeCodec.CurrentVersion, envelope.version)
        assertTrue(envelope.manifestations.isEmpty())
        assertTrue(envelope.confluences.isEmpty())
        assertTrue(envelope.stones.isEmpty())
        assertTrue(envelope.listings.isEmpty())
        assertTrue(envelope.statusEffects.isEmpty())
    }

    @Test
    fun `exportFiltered with only Essences includes manifestations and skips stones`() = runTest {
        val envelope = exporter.exportFiltered(setOf(ContributionDomain.Essences))

        assertEquals(1, envelope.manifestations.size)
        assertEquals("Wind", envelope.manifestations.single().name)
        assertTrue(envelope.stones.isEmpty())
    }

    @Test
    fun `exportFiltered with all domains matches exportAll`() = runTest {
        val full = exporter.exportFiltered(ContributionDomain.entries.toSet())
        val all = exporter.exportAll()

        assertEquals(all, full)
    }
}

private class StubEssenceRepository(
    private val contributions: List<Essence>,
) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = contributions
    override suspend fun getContributions(): List<Essence> = contributions
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
        originalName: String,
        manifestation: Essence.Manifestation,
    ): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(
        originalName: String,
        confluence: Essence.Confluence,
    ): ContributionResult = ContributionResult.Success
    override suspend fun checkEssenceDeleteImpact(name: String): DeleteImpact = DeleteImpact()
}

private class StubAwakeningStoneRepository(
    private val contributions: List<AwakeningStone>,
) : AwakeningStoneRepository {
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = contributions
    override suspend fun getContributions(): List<AwakeningStone> = contributions
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
        ContributionResult.Success
}

private class StubAbilityListingRepository(
    private val contributions: List<Ability.Listing>,
) : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = contributions
    override suspend fun getContributions(): List<Ability.Listing> = contributions
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
        ContributionResult.Success
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private class StubStatusEffectRepository(
    private val contributions: List<ModelStatusEffect>,
) : StatusEffectRepository {
    override val statusEffects: Flow<List<ModelStatusEffect>> = MutableStateFlow(contributions)
    override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
    override suspend fun getStatusEffects(): List<ModelStatusEffect> = contributions
    override suspend fun getContributions(): List<ModelStatusEffect> = contributions
    override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
    override suspend fun saveStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        ContributionResult.Success
}
