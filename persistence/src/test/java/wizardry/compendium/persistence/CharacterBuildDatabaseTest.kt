package wizardry.compendium.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity

class CharacterBuildDatabaseTest {

    /** Resolver that encodes ALL refs as canonical form (canon:<name>). Sufficient for round-trip tests. */
    private object CanonOnlyResolver : BuildRefResolver {
        override fun encodeListing(listing: Ability.Listing): String = "canon:${listing.name}"
        override fun encodeEssence(essence: Essence): String = "canon:${essence.name}"
    }

    private fun newDatabase(): CharacterBuildDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return CharacterBuildDatabase(driver)
    }

    private fun manifestation(name: String): Essence.Manifestation = Essence.Manifestation(
        name = name,
        rank = Rank.Unranked,
        rarity = Rarity.Unknown,
        properties = emptyList(),
        description = "",
        isRestricted = false,
    )

    private fun buildWith(
        name: String,
        race: String = "Human",
        racialNames: List<String> = emptyList(),
        powerEssence: String? = null,
        powerAbilities: List<String> = emptyList(),
    ): CharacterBuild {
        val powerAttr: Attribute = if (powerEssence == null) {
            Attribute.Power()
        } else {
            val essence = manifestation(powerEssence)
            val abilities = powerAbilities.map { abilityName ->
                Ability.Acquired(
                    name = abilityName,
                    effects = emptyList(),
                    rank = Rank.Iron,
                    tier = 0,
                    progress = 0f,
                    boundEssence = essence,
                    listing = Ability.Listing.of(abilityName),
                )
            }
            Attribute.Power(essence = AbsorbedEssence(essence = essence, abilities = abilities))
        }
        return CharacterBuild(
            name = name,
            race = race,
            racialAbilities = racialNames.map { Ability.Listing.of(it) },
            attributes = setOf(powerAttr, Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery()),
        )
    }

    @Test
    fun `empty database returns empty raw lists`() {
        val db = newDatabase()
        assertEquals(emptyList<RawBuildRow>(), db.readAllBuilds())
        assertEquals(emptyList<RawRacialAbilityRow>(), db.readAllRacialAbilities())
        assertEquals(emptyList<RawAttributeRow>(), db.readAllAttributes())
        assertEquals(emptyList<RawAcquiredAbilityRow>(), db.readAllAcquiredAbilities())
    }

    @Test
    fun `writeAll encodes refs via the resolver and round-trips raw rows`() {
        val db = newDatabase()
        val build = buildWith(
            name = "Hero",
            race = "Human",
            racialNames = listOf("Tough"),
            powerEssence = "Fire",
            powerAbilities = listOf("Flame Bolt"),
        )

        db.writeAll(listOf(build), CanonOnlyResolver)

        assertEquals(listOf(RawBuildRow("Hero", "Human")), db.readAllBuilds())
        assertEquals(
            listOf(RawRacialAbilityRow(buildName = "Hero", listingRef = "canon:Tough", ordinal = 0L)),
            db.readAllRacialAbilities(),
        )
        assertEquals(
            listOf(RawAttributeRow(buildName = "Hero", kind = "Power", essenceRef = "canon:Fire")),
            db.readAllAttributes(),
        )
        val acquired = db.readAllAcquiredAbilities()
        assertEquals(1, acquired.size)
        assertEquals("Hero", acquired.single().buildName)
        assertEquals("Power", acquired.single().attributeKind)
        assertEquals("canon:Flame Bolt", acquired.single().listingRef)
    }

    @Test
    fun `upsert replaces a single build without affecting others`() {
        val db = newDatabase()
        db.writeAll(listOf(
            buildWith(name = "Alpha", race = "Human", racialNames = listOf("RacialA")),
            buildWith(name = "Beta", race = "Elf", racialNames = listOf("RacialB")),
        ), CanonOnlyResolver)

        db.upsert(
            buildWith(name = "Alpha", race = "Goblin", racialNames = listOf("NewTrait")),
            CanonOnlyResolver,
        )

        val builds = db.readAllBuilds().associateBy { it.name }
        assertEquals("Goblin", builds.getValue("Alpha").race)
        assertEquals("Elf", builds.getValue("Beta").race)
        val racials = db.readAllRacialAbilities().groupBy { it.buildName }
        assertEquals(listOf("canon:NewTrait"), racials.getValue("Alpha").map { it.listingRef })
        assertEquals(listOf("canon:RacialB"), racials.getValue("Beta").map { it.listingRef })
    }

    @Test
    fun `deleteByName removes the build and all dependent rows`() {
        val db = newDatabase()
        db.writeAll(listOf(
            buildWith(name = "Alpha", racialNames = listOf("RA"), powerEssence = "Fire", powerAbilities = listOf("FB")),
            buildWith(name = "Beta", racialNames = listOf("RB")),
        ), CanonOnlyResolver)

        db.deleteByName("Alpha")

        assertEquals(listOf("Beta"), db.readAllBuilds().map { it.name })
        assertTrue(db.readAllRacialAbilities().none { it.buildName == "Alpha" })
        assertTrue(db.readAllAttributes().none { it.buildName == "Alpha" })
        assertTrue(db.readAllAcquiredAbilities().none { it.buildName == "Alpha" })
        // Beta survives.
        assertEquals(listOf("canon:RB"), db.readAllRacialAbilities().filter { it.buildName == "Beta" }.map { it.listingRef })
    }

    @Test
    fun `buildsReferencingListingRef finds matches in both racial and acquired tables`() {
        val db = newDatabase()
        db.writeAll(listOf(
            buildWith(name = "UsesAsRacial", racialNames = listOf("Shared")),
            buildWith(name = "UsesAsAcquired", powerEssence = "Fire", powerAbilities = listOf("Shared")),
            buildWith(name = "Unrelated", racialNames = listOf("Other")),
        ), CanonOnlyResolver)

        val users = db.buildsReferencingListingRef("canon:Shared")

        assertEquals(listOf("UsesAsAcquired", "UsesAsRacial"), users)
    }

    @Test
    fun `buildsReferencingEssenceRef finds matches in the attribute table`() {
        val db = newDatabase()
        db.writeAll(listOf(
            buildWith(name = "UsesFire", powerEssence = "Fire"),
            buildWith(name = "UsesWater", powerEssence = "Water"),
        ), CanonOnlyResolver)

        assertEquals(listOf("UsesFire"), db.buildsReferencingEssenceRef("canon:Fire"))
        assertEquals(listOf("UsesWater"), db.buildsReferencingEssenceRef("canon:Water"))
        assertEquals(emptyList<String>(), db.buildsReferencingEssenceRef("canon:Air"))
    }

    @Test
    fun `writeAll wipes prior rows`() {
        val db = newDatabase()
        db.writeAll(listOf(buildWith(name = "Old")), CanonOnlyResolver)
        db.writeAll(listOf(buildWith(name = "New")), CanonOnlyResolver)
        assertEquals(listOf("New"), db.readAllBuilds().map { it.name })
    }

    @Test
    fun `resolver receiving Contributed encoding round-trips contr-tagged refs`() {
        val db = newDatabase()
        val contribResolver = object : BuildRefResolver {
            override fun encodeListing(listing: Ability.Listing): String = "contr:42"
            override fun encodeEssence(essence: Essence): String = "contr:7"
        }

        db.writeAll(listOf(
            buildWith(name = "X", racialNames = listOf("CustomAbility"), powerEssence = "CustomEssence", powerAbilities = listOf("CustomAbility")),
        ), contribResolver)

        assertEquals(listOf("contr:42"), db.readAllRacialAbilities().map { it.listingRef })
        assertEquals(listOf("contr:7"), db.readAllAttributes().map { it.essenceRef })
        assertEquals(listOf("contr:42"), db.readAllAcquiredAbilities().map { it.listingRef })
    }
}
