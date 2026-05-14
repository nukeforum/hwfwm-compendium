package wizardry.compendium.wire.share

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.Build
import wizardry.compendium.wire.BuildAbility
import wizardry.compendium.wire.BuildAttribute
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.Stone

class BuildShareDecoderTest {

    private fun newDecoder(
        canonicalEssences: List<Essence> = emptyList(),
        listings: List<Ability.Listing> = emptyList(),
        existingBuilds: List<CharacterBuild> = emptyList(),
    ): BuildShareDecoder = BuildShareDecoder(
        essenceRepository = StubEssenceRepository(canonicalEssences),
        abilityListingRepository = StubAbilityListingRepository(listings),
        buildRepository = StubCharacterBuildRepository(existingBuilds),
    )

    @Test
    fun `multi-entry envelope produces Failed`() = runTest {
        val decoder = newDecoder()

        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            builds = listOf(simpleBuild()),
            stones = listOf(Stone(name = "Stone", rarityIndex = 0)),
        )
        val text = EnvelopeCodec.encode(envelope).text

        val result = decoder.decode(text)
        assertTrue(result is BuildShareDecoder.Result.Failed)
        assertEquals(
            "This share doesn't contain exactly one character build. Use Settings → Import for multi-entry shares.",
            (result as BuildShareDecoder.Result.Failed).reason,
        )
    }

    @Test
    fun `all-resolved produces preview with no missing and no collision`() = runTest {
        val wind = manifestation("Wind")
        val frostBolt = Ability.Listing(name = "Frost Bolt", effects = emptyList())
        val decoder = newDecoder(
            canonicalEssences = listOf(wind),
            listings = listOf(frostBolt),
        )

        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            builds = listOf(
                Build(
                    name = "Frosty", race = "Human",
                    attributes = listOf(
                        BuildAttribute(essenceName = "Wind", abilities = listOf(BuildAbility("Frost Bolt", 1, 0, 0f))),
                        BuildAttribute(), BuildAttribute(), BuildAttribute(),
                    ),
                ),
            ),
        )
        val text = EnvelopeCodec.encode(envelope).text

        val result = decoder.decode(text)
        assertTrue(result is BuildShareDecoder.Result.Loaded)
        val preview = (result as BuildShareDecoder.Result.Loaded).preview
        assertEquals("Frosty", preview.originalName)
        assertEquals("Human", preview.race)
        assertFalse(preview.nameCollides)
        assertEquals(0, preview.missingCount)
    }

    @Test
    fun `missing essence shows as SlotResolution Missing`() = runTest {
        val decoder = newDecoder()

        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            builds = listOf(
                Build(
                    name = "Frosty", race = "Human",
                    attributes = listOf(
                        BuildAttribute(essenceName = "Wind"),
                        BuildAttribute(), BuildAttribute(), BuildAttribute(),
                    ),
                ),
            ),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = decoder.decode(text) as BuildShareDecoder.Result.Loaded
        val powerSlot = result.preview.attributes[0].resolution
        assertTrue(powerSlot is SlotResolution.Missing)
        assertEquals("Wind", (powerSlot as SlotResolution.Missing).essenceName)
        assertEquals(1, result.preview.missingCount)
    }

    @Test
    fun `name collision is flagged`() = runTest {
        val existing = CharacterBuild(name = "Frosty", race = "Human", racialAbilities = emptyList())
        val decoder = newDecoder(existingBuilds = listOf(existing))

        val envelope = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            builds = listOf(simpleBuild()),
        )
        val text = EnvelopeCodec.encode(envelope).text
        val result = decoder.decode(text) as BuildShareDecoder.Result.Loaded
        assertTrue(result.preview.nameCollides)
    }

    @Test
    fun `empty paste produces Failed`() = runTest {
        val decoder = newDecoder()
        val result = decoder.decode("")
        assertTrue(result is BuildShareDecoder.Result.Failed)
        assertEquals("Paste is empty.", (result as BuildShareDecoder.Result.Failed).reason)
    }

    private fun simpleBuild() = Build(
        name = "Frosty", race = "Human",
        attributes = listOf(BuildAttribute(), BuildAttribute(), BuildAttribute(), BuildAttribute()),
    )

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name, rank = Rank.Iron, rarity = Rarity.Common,
        properties = emptyList(), description = "", isRestricted = false,
    )
}

private class StubEssenceRepository(
    private val all: List<Essence>,
) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(all)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = all
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
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
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = ContributionResult.Success
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult = ContributionResult.Success
}

private class StubAbilityListingRepository(
    private val all: List<Ability.Listing>,
) : AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(all)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = all
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult = ContributionResult.Success
}

private class StubCharacterBuildRepository(
    private val all: List<CharacterBuild>,
) : CharacterBuildRepository {
    override val builds: Flow<List<CharacterBuild>> = MutableStateFlow(all)
    override suspend fun getBuilds(): List<CharacterBuild> = all
    override suspend fun getBuild(name: String): CharacterBuild? = all.firstOrNull { it.name == name }
    override suspend fun saveBuildContribution(build: CharacterBuild): ContributionResult = ContributionResult.Success
    override suspend fun deleteContribution(name: String): ContributionResult = ContributionResult.Success
}
