package wizardry.compendium.wire.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectConflict
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.StatusEffect as ModelStatusEffect
import wizardry.compendium.wire.ContributionDomain

class WireIoRepositoryTest {

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

    private fun repo(
        essences: List<Essence> = listOf(manifestation),
        stones: List<AwakeningStone> = listOf(stone),
        listings: List<Ability.Listing> = emptyList(),
        effects: List<ModelStatusEffect> = emptyList(),
    ) = WireIoRepository(
        essenceRepository = StubEssenceRepo(essences),
        awakeningStoneRepository = StubStoneRepo(stones),
        abilityListingRepository = StubListingRepo(listings),
        statusEffectRepository = StubEffectRepo(effects),
    )

    @Test
    fun `exportCounts reflects per-domain contribution sizes`() = runTest {
        val counts = repo().exportCounts()
        assertEquals(1, counts.manifestations)
        assertEquals(0, counts.confluences)
        assertEquals(1, counts.stones)
        assertEquals(0, counts.listings)
        assertEquals(0, counts.effects)
    }

    @Test
    fun `exportFiltered with Essences selection encodes only manifestations and reports fit`() = runTest {
        val encoded = repo().exportFiltered(setOf(ContributionDomain.Essences))
        assertTrue(encoded.text.isNotEmpty())
        assertTrue(encoded.byteSize > 0)
        assertTrue(encoded.fitsInShareLimit)
        assertEquals(100 * 1024, encoded.shareSizeLimitBytes)
    }

    @Test
    fun `decodeEnvelopeOrFailed returns Failed for blank text`() {
        val result = repo().decodeEnvelopeOrFailed("")
        assertTrue(result is WireIoRepository.DecodeResult.Failed)
        assertEquals("Paste is empty.", (result as WireIoRepository.DecodeResult.Failed).reason)
    }

    @Test
    fun `decodeEnvelopeOrFailed returns Failed for malformed text`() {
        val result = repo().decodeEnvelopeOrFailed("not-a-valid-share")
        assertTrue(result is WireIoRepository.DecodeResult.Failed)
    }

    @Test
    fun `roundtrip exportFiltered then decode produces a Decoded result`() = runTest {
        val source = repo()
        val encoded = source.exportFiltered(setOf(ContributionDomain.Essences, ContributionDomain.AwakeningStones))
        val decoded = source.decodeEnvelopeOrFailed(encoded.text)
        assertTrue(decoded is WireIoRepository.DecodeResult.Decoded)
        val envelope = (decoded as WireIoRepository.DecodeResult.Decoded).envelope
        assertEquals(1, envelope.manifestations.size)
        assertEquals(1, envelope.stones.size)
    }

    @Test
    fun `import applies a filtered envelope to the repositories`() = runTest {
        val source = repo()
        val encoded = source.exportFiltered(setOf(ContributionDomain.Essences, ContributionDomain.AwakeningStones))
        val decoded = (source.decodeEnvelopeOrFailed(encoded.text)
            as WireIoRepository.DecodeResult.Decoded).envelope

        val target = repo(essences = emptyList(), stones = emptyList())
        val summary = target.import(
            decoded,
            setOf(ContributionDomain.Essences, ContributionDomain.AwakeningStones),
        )
        assertEquals(2, summary.addedCount)
        assertEquals(0, summary.skippedCount)
        assertEquals(0, summary.failedCount)
    }

    // ---- Stub repos: signatures reconciled against the real :essences interfaces ----

    private class StubEssenceRepo(initial: List<Essence>) : EssenceRepository {
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

    private class StubStoneRepo(initial: List<AwakeningStone>) : AwakeningStoneRepository {
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

    private class StubListingRepo(initial: List<Ability.Listing>) : AbilityListingRepository {
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
        override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult =
            error("not used in tests")
    }

    private class StubEffectRepo(initial: List<ModelStatusEffect>) : StatusEffectRepository {
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
}
