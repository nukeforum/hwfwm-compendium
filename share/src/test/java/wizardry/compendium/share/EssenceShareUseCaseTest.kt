package wizardry.compendium.share

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.EnvelopeMapper
import wizardry.compendium.wire.WireExporter

@OptIn(ExperimentalCoroutinesApi::class)
class EssenceShareUseCaseTest {

    private val wind = Essence.of("Wind", "", Rarity.Common, false)
    private val blood = Essence.of("Blood", "", Rarity.Uncommon, false)
    private val sin = Essence.of("Sin", "", Rarity.Legendary, false)
    private val doom = Essence.of(
        name = "Doom",
        restricted = false,
        ConfluenceSet(wind, blood, sin),
    )

    private fun newUseCase(
        essenceRepo: EssenceRepository = FakeEssenceRepo(),
    ): EssenceShareUseCase = EssenceShareUseCase(
        wireIo = stubWireIoRepository(essenceRepository = essenceRepo),
        essenceRepository = essenceRepo,
    )

    private fun exporter(essenceRepo: EssenceRepository = FakeEssenceRepo()) =
        WireExporter(essenceRepo, StubStoneRepo, StubListingRepo, StubEffectRepo)

    @Test
    fun `decodes a bundled confluence with all-new essences`() = runTest {
        val useCase = newUseCase()
        val text = EnvelopeCodec.encode(exporter().exportSingle(doom)).text

        val result = useCase.decodeConfluenceBundle(text)

        assertTrue(result is DecodedSingle.Loaded)
        val preview = (result as DecodedSingle.Loaded).model
        assertEquals("Doom", preview.confluenceName)
        assertEquals(false, preview.isRestricted)
        assertEquals(1, preview.combinations.size)
        assertEquals(setOf("Wind", "Blood", "Sin"), preview.essences.map { it.name }.toSet())
        assertTrue(preview.essences.all { it.isNew })
        assertTrue(preview.unresolvableNames.isEmpty())
    }

    @Test
    fun `marks bundled essences as not new when already in repo`() = runTest {
        val repo = FakeEssenceRepo(canonical = listOf(wind))
        val useCase = newUseCase(repo)
        val text = EnvelopeCodec.encode(exporter(repo).exportSingle(doom)).text

        val preview = (useCase.decodeConfluenceBundle(text) as DecodedSingle.Loaded).model

        val newness = preview.essences.associate { it.name to it.isNew }
        assertEquals(false, newness["Wind"])
        assertEquals(true, newness["Blood"])
        assertEquals(true, newness["Sin"])
    }

    @Test
    fun `flags unresolvable combination references`() = runTest {
        val useCase = newUseCase()
        val phantomReferencingConfluence = Essence.of(
            name = "Mirage",
            restricted = false,
            ConfluenceSet(
                wind,
                blood,
                Essence.of("Phantom", "", Rarity.Rare, false),
            ),
        )
        val full = exporter().exportSingle(phantomReferencingConfluence)
        val stripped = full.copy(
            manifestations = full.manifestations.filter { it.name != "Phantom" },
        )
        val text = EnvelopeCodec.encode(stripped).text

        val preview = (useCase.decodeConfluenceBundle(text) as DecodedSingle.Loaded).model

        assertEquals(setOf("phantom"), preview.unresolvableNames)
    }

    @Test
    fun `rejects empty paste`() = runTest {
        val result = newUseCase().decodeConfluenceBundle("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }

    @Test
    fun `rejects malformed paste`() = runTest {
        val result = newUseCase().decodeConfluenceBundle("not-a-real-share")
        assertTrue(result is DecodedSingle.Failed)
        val reason = (result as DecodedSingle.Failed).reason
        assertTrue(
            "expected a decode-failure reason, got '$reason'",
            reason != "Paste is empty." && reason != "This share was made with a newer app version. Update to import.",
        )
    }

    @Test
    fun `rejects envelope with zero confluences`() = runTest {
        val useCase = newUseCase()
        val text = EnvelopeCodec.encode(exporter().exportSingle(wind)).text
        val result = useCase.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `rejects envelope with two confluences`() = runTest {
        val useCase = newUseCase()
        val a = exporter().exportSingle(doom)
        val tempest = Essence.of(
            name = "Tempest",
            restricted = false,
            ConfluenceSet(wind, blood, sin),
        )
        val b = exporter().exportSingle(tempest)
        val combined = a.copy(confluences = a.confluences + b.confluences)
        val text = EnvelopeCodec.encode(combined).text

        val result = useCase.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `rejects envelope containing a stone`() = runTest {
        val useCase = newUseCase()
        val confluenceEnv = exporter().exportSingle(doom)
        val stone = wizardry.compendium.domain.model.AwakeningStone.of("Volcano", Rarity.Epic)
        val stoneEnv = exporter().exportSingle(stone)
        val combined = confluenceEnv.copy(stones = stoneEnv.stones)
        val text = EnvelopeCodec.encode(combined).text

        val result = useCase.decodeConfluenceBundle(text)
        assertEquals(
            "This share doesn't contain exactly one confluence. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `encode round-trips for a manifestation`() {
        val useCase = newUseCase()
        val flame = Essence.of(name = "Flame", description = "fire", rarity = Rarity.Common, restricted = false)
        val text = useCase.encode(flame)
        val result = useCase.decodeSingleManifestation(text)
        assertTrue(result is DecodedSingle.Loaded)
        assertEquals(flame, (result as DecodedSingle.Loaded).model)
    }

    @Test
    fun `decodeSingleManifestation rejects multi-entry envelope`() {
        val useCase = newUseCase()
        val flame = Essence.of(name = "Flame", description = "fire", rarity = Rarity.Common, restricted = false)
        val ice = Essence.of(name = "Ice", description = "cold", rarity = Rarity.Common, restricted = false)
        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = listOf(EnvelopeMapper.toWire(flame), EnvelopeMapper.toWire(ice)),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = useCase.decodeSingleManifestation(text)
        assertEquals(
            "This share doesn't contain exactly one essence manifestation. Use Settings → Import for multi-entry shares.",
            (result as DecodedSingle.Failed).reason,
        )
    }

    @Test
    fun `decodeSingleManifestation rejects empty paste`() {
        val result = newUseCase().decodeSingleManifestation("")
        assertEquals("Paste is empty.", (result as DecodedSingle.Failed).reason)
    }

    @Test
    fun `renderAsText for manifestation includes name rank rarity description`() {
        val flame = Essence.of(name = "Flame", description = "fire essence", rarity = Rarity.Common, restricted = false)
        val text = newUseCase().renderAsText(flame)
        assertTrue("missing name in $text", text.contains("Flame Essence"))
        assertTrue("missing rank in $text", text.contains(flame.rank.toString().lowercase()))
        assertTrue("missing rarity in $text", text.contains(flame.rarity.toString().lowercase()))
        assertTrue("missing description in $text", text.contains("fire essence"))
    }

    @Test
    fun `renderAsText for confluence includes name and Confluence label`() {
        val text = newUseCase().renderAsText(doom)
        assertEquals("Doom Confluence", text)
    }
}

private class FakeEssenceRepo(
    canonical: List<Essence> = emptyList(),
    contributions: List<Essence> = emptyList(),
) : EssenceRepository {
    private val all: List<Essence> = canonical + contributions

    override val essences: Flow<List<Essence>> = MutableStateFlow(all)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = all
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ) = ContributionResult.Success
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ) = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String) = ContributionResult.Success
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation) =
        ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence) =
        ContributionResult.Success
}
