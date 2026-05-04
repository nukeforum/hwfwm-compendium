package wizardry.compendium.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelDecodeSingleStatusEffectTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ShareViewModel

    private val burn = StatusEffect(
        name = "Burn",
        type = StatusType.Affliction.Elemental,
        properties = listOf(Property.DamageOverTime, Property.Fire),
        stackable = true,
        description = "Deals fire damage over time.",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel = ShareViewModel(
            FakeEssenceRepositoryForStatusEffectTest(),
            FakeAwakeningStoneRepositoryForStatusEffectTest(),
            FakeAbilityListingRepositoryForStatusEffectTest(),
            fakeStatusEffectRepoForTest(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `decodes envelope with single status effect`() = runTest(dispatcher) {
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            statusEffects = listOf(EnvelopeMapper.toWire(burn)),
        )
        val text = EnvelopeCodec.encode(envelope).text

        val result = viewModel.decodeSingleStatusEffect(text)

        assertTrue(result is ShareViewModel.DecodedSingle.Loaded)
        assertEquals(burn, (result as ShareViewModel.DecodedSingle.Loaded).model)
    }

    @Test
    fun `rejects envelope containing one status effect and one stone`() = runTest(dispatcher) {
        val stone = AwakeningStone.of("Volcano", Rarity.Epic)
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            statusEffects = listOf(EnvelopeMapper.toWire(burn)),
            stones = listOf(EnvelopeMapper.toWire(stone)),
        )
        val text = EnvelopeCodec.encode(envelope).text

        val result = viewModel.decodeSingleStatusEffect(text)

        assertEquals(
            "This share doesn't contain exactly one status effect. Use Settings → Import for multi-entry shares.",
            (result as ShareViewModel.DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `rejects empty paste`() = runTest(dispatcher) {
        val result = viewModel.decodeSingleStatusEffect("")
        assertEquals("Paste is empty.", (result as ShareViewModel.DecodedSingle.Failed).reason)
    }
}

private fun fakeStatusEffectRepoForTest() = object : wizardry.compendium.essences.StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.essences.StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) =
        wizardry.compendium.essences.ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) =
        wizardry.compendium.essences.ContributionResult.Success
}

private class FakeEssenceRepositoryForStatusEffectTest : wizardry.compendium.essences.EssenceRepository {
    override val essences = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.model.Essence>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.EssenceConflict>())
    override suspend fun getEssences() = emptyList<wizardry.compendium.essences.model.Essence>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.essences.model.Essence>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.essences.EssenceConflict>()
    override suspend fun saveManifestationContribution(manifestation: wizardry.compendium.essences.model.Essence.Manifestation) =
        wizardry.compendium.essences.ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: wizardry.compendium.essences.model.Essence.Confluence,
        referencedManifestations: List<wizardry.compendium.essences.model.Essence.Manifestation>,
    ) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: wizardry.compendium.essences.model.Essence.Confluence,
        combination: wizardry.compendium.essences.model.ConfluenceSet,
    ) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: wizardry.compendium.essences.model.Essence.Manifestation) =
        wizardry.compendium.essences.ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: wizardry.compendium.essences.model.Essence.Confluence) =
        wizardry.compendium.essences.ContributionResult.Success
}

private class FakeAwakeningStoneRepositoryForStatusEffectTest : wizardry.compendium.essences.AwakeningStoneRepository {
    override val awakeningStones = kotlinx.coroutines.flow.flowOf(emptyList<AwakeningStone>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.essences.AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) =
        wizardry.compendium.essences.ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone) =
        wizardry.compendium.essences.ContributionResult.Success
}

private class FakeAbilityListingRepositoryForStatusEffectTest : wizardry.compendium.essences.AbilityListingRepository {
    override val abilityListings = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.model.Ability.Listing>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<wizardry.compendium.essences.AbilityListingConflict>())
    override suspend fun getAbilityListings() = emptyList<wizardry.compendium.essences.model.Ability.Listing>()
    override suspend fun getContributions() = emptyList<wizardry.compendium.essences.model.Ability.Listing>()
    override suspend fun getConflicts() = emptyList<wizardry.compendium.essences.AbilityListingConflict>()
    override suspend fun saveAbilityListingContribution(listing: wizardry.compendium.essences.model.Ability.Listing) =
        wizardry.compendium.essences.ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = wizardry.compendium.essences.ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: wizardry.compendium.essences.model.Ability.Listing) =
        wizardry.compendium.essences.ContributionResult.Success
}
