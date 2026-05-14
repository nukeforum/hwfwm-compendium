package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.persistence.CharacterBuildCache

class DefaultCharacterBuildRepositoryTest {

    @Test
    fun `getBuilds resolves all references when everything exists`() = runBlocking {
        val sin = manifestation("Sin")
        val handOfReaper = listing("Hand of the Reaper")
        val inflictDisease = listing("Inflict Disease")
        val build = CharacterBuild(
            name = "Reaper",
            race = "Human",
            racialAbilities = listOf(inflictDisease),
            attributes = setOf(
                Attribute.Power(essence = absorbed(sin, listOf(handOfReaper))),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val repo = repository(
            stored = listOf(build),
            essences = listOf(sin),
            listings = listOf(handOfReaper, inflictDisease),
        )

        val result = repo.getBuilds().single()

        assertEquals("Sin", result.Power.essence?.essence?.name)
        assertEquals(listOf("Hand of the Reaper"), result.Power.essence?.abilities?.map { it.name })
        assertEquals(listOf("Inflict Disease"), result.racialAbilities.map { it.name })
    }

    @Test
    fun `missing essence drops the slot but keeps other slots`() = runBlocking {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val build = CharacterBuild(
            name = "Cursed",
            race = "Human",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(essence = absorbed(sin, emptyList())),
                Attribute.Speed(essence = absorbed(doom, emptyList())),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val repo = repository(
            stored = listOf(build),
            essences = listOf(doom),
            listings = emptyList(),
        )

        val result = repo.getBuilds().single()

        assertNull(result.Power.essence)
        assertEquals("Doom", result.Speed.essence?.essence?.name)
    }

    @Test
    fun `missing ability listing drops just that ability`() = runBlocking {
        val sin = manifestation("Sin")
        val handOfReaper = listing("Hand of the Reaper")
        val inflictWound = listing("Inflict Wound")
        val build = CharacterBuild(
            name = "Reaper",
            race = "Human",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(essence = absorbed(sin, listOf(handOfReaper, inflictWound))),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val repo = repository(
            stored = listOf(build),
            essences = listOf(sin),
            listings = listOf(handOfReaper),
        )

        val result = repo.getBuilds().single()

        assertEquals(
            listOf("Hand of the Reaper"),
            result.Power.essence?.abilities?.map { it.name },
        )
    }

    @Test
    fun `missing racial ability listing drops it`() = runBlocking {
        val inflictDisease = listing("Inflict Disease")
        val build = CharacterBuild(
            name = "Reaper",
            race = "Human",
            racialAbilities = listOf(inflictDisease),
            attributes = setOf(
                Attribute.Power(),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )
        val repo = repository(
            stored = listOf(build),
            essences = emptyList(),
            listings = emptyList(),
        )

        val result = repo.getBuilds().single()

        assertTrue(result.racialAbilities.isEmpty())
    }

    @Test
    fun `confluence essence assignment hydrates intact`() = runBlocking {
        val sin = manifestation("Sin")
        val doom = manifestation("Doom")
        val dark = manifestation("Dark")
        val confluence = Essence.Confluence(
            name = "Sinister",
            confluenceSets = setOf(
                ConfluenceSet(setOf(sin, doom, dark), isRestricted = false),
            ),
            isRestricted = false,
        )
        val recoveryAbsorbed = AbsorbedEssence(
            essence = confluence,
            abilities = emptyList(),
        )
        val stored = CharacterBuild(
            name = "Jason",
            race = "Human",
            racialAbilities = emptyList(),
            attributes = setOf(
                Attribute.Power(),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(essence = recoveryAbsorbed),
            ),
        )

        val repo = repository(
            stored = listOf(stored),
            essences = listOf(sin, doom, dark, confluence),
            listings = emptyList(),
        )

        val result = repo.getBuilds().single()
        assertEquals("Sinister", result.Recovery.essence?.essence?.name)
        assertTrue(
            "expected Confluence, got ${result.Recovery.essence?.essence?.javaClass?.simpleName}",
            result.Recovery.essence?.essence is Essence.Confluence,
        )
    }

    @Test
    fun `getBuild returns null for unknown name`() = runBlocking {
        val repo = repository(
            stored = emptyList(),
            essences = emptyList(),
            listings = emptyList(),
        )

        assertNull(repo.getBuild("ghost"))
    }

    @Test
    fun `saveBuildContribution adds new build and replaces existing one by name`() = runBlocking {
        val cache = InMemoryCharacterBuildCache()
        val repo = DefaultCharacterBuildRepository(
            cache = cache,
            essenceRepository = CharacterBuildFakeEssenceRepository(emptyList()),
            abilityListingRepository = FakeAbilityListingRepository(emptyList()),
        )
        val original = CharacterBuild(
            name = "Reaper",
            race = "Human",
            racialAbilities = emptyList(),
        )
        val updated = CharacterBuild(
            name = "Reaper",
            race = "Smeldraxian",
            racialAbilities = emptyList(),
        )

        repo.saveBuildContribution(original)
        repo.saveBuildContribution(updated)

        val stored = cache.contents
        assertEquals(1, stored.size)
        assertEquals("Smeldraxian", stored.single().race)
    }

    @Test
    fun `deleteContribution removes the build`() = runBlocking {
        val seeded = CharacterBuild(
            name = "Reaper",
            race = "Human",
            racialAbilities = emptyList(),
        )
        val cache = InMemoryCharacterBuildCache().apply { contents = listOf(seeded) }
        val repo = DefaultCharacterBuildRepository(
            cache = cache,
            essenceRepository = CharacterBuildFakeEssenceRepository(emptyList()),
            abilityListingRepository = FakeAbilityListingRepository(emptyList()),
        )

        val result = repo.deleteContribution("Reaper")

        assertEquals(ContributionResult.Success, result)
        assertTrue(cache.contents.isEmpty())
    }

    @Test
    fun `deleteContribution on unknown name returns Failure`() = runBlocking {
        val repo = DefaultCharacterBuildRepository(
            cache = InMemoryCharacterBuildCache(),
            essenceRepository = CharacterBuildFakeEssenceRepository(emptyList()),
            abilityListingRepository = FakeAbilityListingRepository(emptyList()),
        )

        val result = repo.deleteContribution("ghost")

        assertTrue(
            "expected Failure but got $result",
            result is ContributionResult.Failure,
        )
    }
}

private fun repository(
    stored: List<CharacterBuild>,
    essences: List<Essence>,
    listings: List<Ability.Listing>,
): DefaultCharacterBuildRepository {
    return DefaultCharacterBuildRepository(
        cache = InMemoryCharacterBuildCache().apply { contents = stored },
        essenceRepository = CharacterBuildFakeEssenceRepository(essences),
        abilityListingRepository = FakeAbilityListingRepository(listings),
    )
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "$name essence", rarity = Rarity.Common, restricted = false)

private fun listing(name: String): Ability.Listing = Ability.Listing.of(name)

private fun absorbed(
    essence: Essence,
    listings: List<Ability.Listing>,
): AbsorbedEssence {
    val abilities = listings.map { listing ->
        Ability.Acquired(
            name = listing.name,
            effects = listing.effects,
            rank = Rank.Iron,
            tier = 0,
            progress = 0f,
            boundEssence = essence,
            listing = listing,
        )
    }
    return AbsorbedEssence(essence = essence, abilities = abilities)
}

private class InMemoryCharacterBuildCache : CharacterBuildCache {
    override var contents: List<CharacterBuild> = emptyList()
}

private class CharacterBuildFakeEssenceRepository(private val data: List<Essence>) : EssenceRepository {
    override val essences: Flow<List<Essence>> = MutableStateFlow(data)
    override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
    override suspend fun getEssences(): List<Essence> = data
    override suspend fun getContributions(): List<Essence> = emptyList()
    override suspend fun getConflicts(): List<EssenceConflict> = emptyList()
    override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        error("not used")
    override suspend fun saveConfluenceContribution(
        confluence: Essence.Confluence,
        referencedManifestations: List<Essence.Manifestation>,
    ): ContributionResult = error("not used")
    override suspend fun addCombinationToConfluence(
        target: Essence.Confluence,
        combination: ConfluenceSet,
    ): ContributionResult = error("not used")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
    override suspend fun updateManifestationContribution(manifestation: Essence.Manifestation): ContributionResult =
        error("not used")
    override suspend fun updateConfluenceContribution(confluence: Essence.Confluence): ContributionResult =
        error("not used")
}

private class FakeAbilityListingRepository(private val data: List<Ability.Listing>) :
    AbilityListingRepository {
    override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(data)
    override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
    override suspend fun getAbilityListings(): List<Ability.Listing> = data
    override suspend fun getContributions(): List<Ability.Listing> = emptyList()
    override suspend fun getConflicts(): List<AbilityListingConflict> = emptyList()
    override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        error("not used")
    override suspend fun isContribution(name: String): Boolean = false
    override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
    override suspend fun updateAbilityListingContribution(listing: Ability.Listing): ContributionResult =
        error("not used")
}
