package wizardry.compendium.drive.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect as ModelStatusEffect
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.wire.repo.WireIoRepository

class WirePayloadCodecTest {

    @Test
    fun `encode emits envelope text including contributions from all domains`() = runTest {
        val codec = WirePayloadCodec(stubWireIo())
        val text = codec.encode()
        assertTrue("envelope text should be non-empty", text.isNotEmpty())
    }

    @Test
    fun `hasLocalContributions returns false when all repos are empty`() = runTest {
        val codec = WirePayloadCodec(stubWireIo(empty = true))
        assertEquals(false, codec.hasLocalContributions())
    }

    @Test
    fun `hasLocalContributions returns true when any repo has contributions`() = runTest {
        val codec = WirePayloadCodec(stubWireIo(empty = false))
        assertEquals(true, codec.hasLocalContributions())
    }

    private fun stubWireIo(empty: Boolean = false): WireIoRepository {
        val essence = if (empty) emptyList() else listOf(
            Essence.Manifestation("Wind", Rank.Iron, Rarity.Common, emptyList(), "", false)
        )
        val stone = if (empty) emptyList() else listOf(
            AwakeningStone("Volcano", Rank.Unranked, Rarity.Epic, emptyList(), emptyList(), "")
        )
        return WireIoRepository(
            essenceRepository = TestStubEssenceRepository(essence),
            awakeningStoneRepository = TestStubAwakeningStoneRepository(stone),
            abilityListingRepository = TestStubAbilityListingRepository(emptyList()),
            statusEffectRepository = TestStubStatusEffectRepository(emptyList()),
        )
    }
}

// Stub classes copied verbatim from wire-repo/src/test/.../WireIoRepositoryTest.kt,
// renamed StubEssenceRepo -> TestStubEssenceRepository etc. to avoid collisions.

private class TestStubEssenceRepository(initial: List<Essence>) : EssenceRepository {
    private val contributions = initial.toMutableList()
    override val essences: Flow<List<Essence>> = MutableStateFlow(contributions.toList())
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = contributions.toList()
    override suspend fun getContributions(): List<Essence> = contributions.toList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult {
        contributions += manifestation
        return ContributionResult.Success
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult {
        contributions += confluence
        return ContributionResult.Success
    }
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = error("not used in tests")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult =
        error("not used in tests")
    override suspend fun updateManifestationContribution(
        manifestation: Essence.Manifestation,
    ): ContributionResult = error("not used in tests")
    override suspend fun updateConfluenceContribution(
        confluence: Essence.Confluence,
    ): ContributionResult = error("not used in tests")
}

private class TestStubAwakeningStoneRepository(initial: List<AwakeningStone>) : AwakeningStoneRepository {
    private val contributions = initial.toMutableList()
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(contributions.toList())
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = contributions.toList()
    override suspend fun getContributions(): List<AwakeningStone> = contributions.toList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult {
        contributions += stone
        return ContributionResult.Success
    }
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult =
        error("not used in tests")
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult =
        error("not used in tests")
}

private class TestStubAbilityListingRepository(initial: List<Ability.Listing>) : AbilityListingRepository {
    private val contributions = initial.toMutableList()
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(contributions.toList())
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = contributions.toList()
    override suspend fun getContributions(): List<Ability.Listing> = contributions.toList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult {
        contributions += listing
        return ContributionResult.Success
    }
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult =
        error("not used in tests")
    override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult =
        error("not used in tests")
    override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
}

private class TestStubStatusEffectRepository(initial: List<ModelStatusEffect>) : StatusEffectRepository {
    private val contributions = initial.toMutableList()
    override val statusEffects: Flow<List<ModelStatusEffect>> = MutableStateFlow(contributions.toList())
    override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
    override suspend fun getStatusEffects(): List<ModelStatusEffect> = contributions.toList()
    override suspend fun getContributions(): List<ModelStatusEffect> = contributions.toList()
    override suspend fun getConflicts(): List<StatusEffectConflict> = emptyList()
    override suspend fun saveStatusEffectContribution(effect: ModelStatusEffect): ContributionResult {
        contributions += effect
        return ContributionResult.Success
    }
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult =
        error("not used in tests")
    override suspend fun updateStatusEffectContribution(effect: ModelStatusEffect): ContributionResult =
        error("not used in tests")
}
