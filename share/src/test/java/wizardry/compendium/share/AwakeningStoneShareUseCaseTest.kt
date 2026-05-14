package wizardry.compendium.share

import kotlinx.coroutines.flow.flowOf
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
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.repo.WireIoRepository

class AwakeningStoneShareUseCaseTest {

    private val volcano = AwakeningStone.of("Volcano", Rarity.Epic)

    private fun newUseCase(): AwakeningStoneShareUseCase = AwakeningStoneShareUseCase(
        wireIo = WireIoRepository(
            essenceRepository = FakeEssenceRepo(),
            awakeningStoneRepository = FakeStoneRepo(),
            abilityListingRepository = FakeListingRepo(),
            statusEffectRepository = FakeEffectRepo(),
        ),
    )

    @Test
    fun `encode round-trips through decode for a single stone`() {
        val useCase = newUseCase()
        val text = useCase.encode(volcano)
        val result = useCase.decodeSingleStone(text)
        assertTrue(result is DecodedSingle.Loaded)
        assertEquals(volcano, (result as DecodedSingle.Loaded).model)
    }

    @Test
    fun `decode rejects envelope with stone and unrelated entry`() {
        val useCase = newUseCase()
        val burn = StatusEffect(
            name = "Burn",
            type = wizardry.compendium.essences.model.StatusType.Affliction.Elemental,
            properties = listOf(wizardry.compendium.essences.model.Property.Fire),
            stackable = true,
            description = "fire dot",
        )
        val envelope = wizardry.compendium.wire.Envelope(
            version = EnvelopeCodec.CurrentVersion,
            stones = listOf(EnvelopeMapper.toWire(volcano)),
            statusEffects = listOf(EnvelopeMapper.toWire(burn)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = useCase.decodeSingleStone(text)
        assertEquals(
            "This share doesn't contain exactly one awakening stone. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `decode rejects empty paste`() {
        val result = newUseCase().decodeSingleStone("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }
}

private class FakeEssenceRepo : EssenceRepository {
    override val essences = flowOf(emptyList<Essence>())
    override val conflicts = flowOf(emptyList<EssenceConflict>())
    override suspend fun getEssences() = emptyList<Essence>()
    override suspend fun getContributions() = emptyList<Essence>()
    override suspend fun getConflicts() = emptyList<EssenceConflict>()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: wizardry.compendium.essences.model.ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence) = ContributionResult.Success
}

private class FakeStoneRepo : AwakeningStoneRepository {
    override val awakeningStones = flowOf(emptyList<AwakeningStone>())
    override val conflicts = flowOf(emptyList<AwakeningStoneConflict>())
    override suspend fun getAwakeningStones() = emptyList<AwakeningStone>()
    override suspend fun getContributions() = emptyList<AwakeningStone>()
    override suspend fun getConflicts() = emptyList<AwakeningStoneConflict>()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone) = ContributionResult.Success
}

private class FakeListingRepo : AbilityListingRepository {
    override val abilityListings = flowOf(emptyList<Ability.Listing>())
    override val conflicts = flowOf(emptyList<AbilityListingConflict>())
    override suspend fun getAbilityListings() = emptyList<Ability.Listing>()
    override suspend fun getContributions() = emptyList<Ability.Listing>()
    override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing) = ContributionResult.Success
}

private class FakeEffectRepo : StatusEffectRepository {
    override val statusEffects = flowOf(emptyList<StatusEffect>())
    override val conflicts = flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) = ContributionResult.Success
}
