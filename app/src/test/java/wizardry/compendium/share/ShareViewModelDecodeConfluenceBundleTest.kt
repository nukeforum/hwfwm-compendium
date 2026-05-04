package wizardry.compendium.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelDecodeConfluenceBundleTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var essenceRepo: FakeEssenceRepository
    private lateinit var stoneRepo: FakeAwakeningStoneRepository
    private lateinit var listingRepo: FakeAbilityListingRepository
    private lateinit var viewModel: ShareViewModel

    private val wind = Essence.of("Wind", "", Rarity.Common, false)
    private val blood = Essence.of("Blood", "", Rarity.Uncommon, false)
    private val sin = Essence.of("Sin", "", Rarity.Legendary, false)
    private val doom = Essence.of(
        name = "Doom",
        restricted = false,
        ConfluenceSet(wind, blood, sin),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        essenceRepo = FakeEssenceRepository(canonical = emptyList(), contributions = emptyList())
        stoneRepo = FakeAwakeningStoneRepository()
        listingRepo = FakeAbilityListingRepository()
        viewModel = ShareViewModel(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `decodes a bundled confluence with all-new essences`() = runTest(dispatcher) {
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val text = EnvelopeCodec.encode(exporter.exportSingle(doom)).text

        val result = viewModel.decodeConfluenceBundle(text)

        assertTrue(result is ShareViewModel.DecodedSingle.Loaded)
        val preview = (result as ShareViewModel.DecodedSingle.Loaded).model
        assertEquals("Doom", preview.confluenceName)
        assertEquals(false, preview.isRestricted)
        assertEquals(1, preview.combinations.size)
        assertEquals(setOf("Wind", "Blood", "Sin"), preview.essences.map { it.name }.toSet())
        assertTrue(preview.essences.all { it.isNew })
        assertTrue(preview.unresolvableNames.isEmpty())
    }

    @Test
    fun `marks bundled essences as not new when already in repo`() = runTest(dispatcher) {
        essenceRepo = FakeEssenceRepository(canonical = listOf(wind), contributions = emptyList())
        viewModel = ShareViewModel(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val text = EnvelopeCodec.encode(exporter.exportSingle(doom)).text

        val preview = (viewModel.decodeConfluenceBundle(text) as ShareViewModel.DecodedSingle.Loaded).model

        val newness = preview.essences.associate { it.name to it.isNew }
        assertEquals(false, newness["Wind"])
        assertEquals(true, newness["Blood"])
        assertEquals(true, newness["Sin"])
    }

    @Test
    fun `flags unresolvable combination references`() = runTest(dispatcher) {
        val phantomReferencingConfluence = Essence.of(
            name = "Mirage",
            restricted = false,
            ConfluenceSet(
                wind,
                blood,
                Essence.of("Phantom", "", Rarity.Rare, false),
            ),
        )
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val full = exporter.exportSingle(phantomReferencingConfluence)
        val stripped = full.copy(
            manifestations = full.manifestations.filter { it.name != "Phantom" },
        )
        val text = EnvelopeCodec.encode(stripped).text

        val preview = (viewModel.decodeConfluenceBundle(text) as ShareViewModel.DecodedSingle.Loaded).model

        assertEquals(setOf("phantom"), preview.unresolvableNames)
    }

    @Test
    fun `rejects empty paste`() = runTest(dispatcher) {
        val result = viewModel.decodeConfluenceBundle("")
        assertEquals("Paste is empty.", (result as ShareViewModel.DecodedSingle.Failed).reason)
    }

    @Test
    fun `rejects malformed paste`() = runTest(dispatcher) {
        val result = viewModel.decodeConfluenceBundle("not-a-real-share")
        assertTrue(result is ShareViewModel.DecodedSingle.Failed)
        val reason = (result as ShareViewModel.DecodedSingle.Failed).reason
        // The malformed-paste path goes through WireDecodeException; reason should
        // not be the version-unsupported message and not the empty-paste message.
        assertTrue(
            "expected a decode-failure reason, got '$reason'",
            reason != "Paste is empty." && reason != "This share was made with a newer app version. Update to import.",
        )
    }

    @Test
    fun `rejects envelope with zero confluences`() = runTest(dispatcher) {
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val text = EnvelopeCodec.encode(exporter.exportSingle(wind)).text
        val result = viewModel.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as ShareViewModel.DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `rejects envelope with two confluences`() = runTest(dispatcher) {
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val a = exporter.exportSingle(doom)
        val tempest = Essence.of(
            name = "Tempest",
            restricted = false,
            ConfluenceSet(wind, blood, sin),
        )
        val b = exporter.exportSingle(tempest)
        val combined = a.copy(confluences = a.confluences + b.confluences)
        val text = EnvelopeCodec.encode(combined).text

        val result = viewModel.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as ShareViewModel.DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `rejects envelope containing a stone`() = runTest(dispatcher) {
        val exporter = WireExporter(essenceRepo, stoneRepo, listingRepo, fakeStatusEffectRepo())
        val confluenceEnv = exporter.exportSingle(doom)
        val stone = wizardry.compendium.essences.model.AwakeningStone.of("Volcano", Rarity.Epic)
        val stoneEnv = exporter.exportSingle(stone)
        val combined = confluenceEnv.copy(stones = stoneEnv.stones)
        val text = EnvelopeCodec.encode(combined).text

        val result = viewModel.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as ShareViewModel.DecodedSingle.Failed).reason,
        )
    }
}

private fun fakeStatusEffectRepo() = object : StatusEffectRepository {
    override val statusEffects = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffect>())
    override val conflicts = kotlinx.coroutines.flow.flowOf(emptyList<StatusEffectConflict>())
    override suspend fun getStatusEffects() = emptyList<StatusEffect>()
    override suspend fun getContributions() = emptyList<StatusEffect>()
    override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
    override suspend fun saveStatusEffectContribution(effect: StatusEffect) =
        ContributionResult.Success
    override suspend fun isContribution(name: String) = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateStatusEffectContribution(effect: StatusEffect) =
        ContributionResult.Success
}

private class FakeEssenceRepository(
    canonical: List<Essence> = emptyList(),
    contributions: List<Essence> = emptyList(),
) : EssenceRepository {
    private val all: List<Essence> = canonical + contributions
    val savedManifestations = mutableListOf<Essence.Manifestation>()
    val savedConfluences = mutableListOf<Essence.Confluence>()

    override val essences: Flow<List<Essence>> = MutableStateFlow(all)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = all
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult {
        savedManifestations += manifestation
        return ContributionResult.Success
    }
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult {
        savedConfluences += confluence
        return ContributionResult.Success
    }
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
}

private class FakeAwakeningStoneRepository : AwakeningStoneRepository {
    override val awakeningStones: Flow<List<AwakeningStone>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AwakeningStoneConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAwakeningStones(): List<AwakeningStone> = emptyList()
    override suspend fun getContributions(): List<AwakeningStone> = emptyList()
    override suspend fun getConflicts(): List<AwakeningStoneConflict> = emptyList()
    override suspend fun saveAwakeningStoneContribution(stone: AwakeningStone): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAwakeningStoneContribution(stone: AwakeningStone): ContributionResult = ContributionResult.Success
}

private class FakeAbilityListingRepository : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(emptyList())
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = emptyList()
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
}
