package wizardry.compendium.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity

/**
 * Round-trip edge cases for CharacterBuildDatabase. The basic populated round-trip is
 * already covered by MigrationTest's "CharacterBuild round trips ... v5 schema" case,
 * so this suite focuses on edges that case skips: empty DB, special characters,
 * upsert isolation, deleteByName cascade, slot/racial-ability capacity, ordinal
 * preservation, and writeAll wiping prior state.
 */
class CharacterBuildDatabaseTest {

    private fun newEnv(): Pair<SqlDriver, CharacterBuildDatabase> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)
        return driver to CharacterBuildDatabase(driver)
    }

    @Test
    fun `empty database returns empty list`() {
        val (_, db) = newEnv()
        assertEquals(emptyList<CharacterBuild>(), db.readAll())
    }

    @Test
    fun `round-trip preserves apostrophes and unicode in names`() {
        val (_, db) = newEnv()
        val build = sampleBuild(
            name = "Asha'man Çelîk",
            race = "Tír na nÓg",
            racialAbilityNames = emptyList(),
            slots = mapOf(
                ("Power" to "L'essence d'éclat") to listOf("Frappe d'éclat"),
            ),
        )

        db.writeAll(listOf(build))
        val read = db.readAll()

        assertEquals(1, read.size)
        val rt = read.single()
        assertEquals("Asha'man Çelîk", rt.name)
        assertEquals("Tír na nÓg", rt.race)
        val absorbed = rt.Power.essence
        assertNotNull("Power slot should have an essence after round-trip", absorbed)
        assertEquals("L'essence d'éclat", absorbed!!.essence.name)
        assertEquals(listOf("Frappe d'éclat"), absorbed.abilities.map { it.name })
    }

    @Test
    fun `upsert replaces a single build without affecting others`() {
        val (_, db) = newEnv()
        val a = sampleBuild(
            name = "Alpha",
            race = "Human",
            racialAbilityNames = listOf("Trait1"),
            slots = mapOf(("Power" to "Fire") to listOf("Flame Bolt")),
        )
        val b = sampleBuild(
            name = "Beta",
            race = "Elf",
            racialAbilityNames = listOf("Trait2"),
            slots = mapOf(("Speed" to "Wind") to listOf("Gust")),
        )
        db.writeAll(listOf(a, b))

        val updatedA = sampleBuild(
            name = "Alpha",
            race = "Smoulderer",
            racialAbilityNames = listOf("NewTrait"),
            slots = mapOf(("Spirit" to "Magma") to listOf("Lava Lash", "Eruption")),
        )
        db.upsert(updatedA)

        val read = db.readAll().associateBy { it.name }
        assertEquals(setOf("Alpha", "Beta"), read.keys)

        val rtA = read.getValue("Alpha")
        assertEquals("Smoulderer", rtA.race)
        assertEquals(listOf("NewTrait"), rtA.racialAbilities.map { it.name })
        assertNull("Alpha's old Power slot must be cleared", rtA.Power.essence)
        val newSpirit = rtA.Spirit.essence
        assertNotNull(newSpirit)
        assertEquals("Magma", newSpirit!!.essence.name)
        assertEquals(listOf("Lava Lash", "Eruption"), newSpirit.abilities.map { it.name })

        val rtB = read.getValue("Beta")
        assertEquals("Elf", rtB.race)
        assertEquals(listOf("Trait2"), rtB.racialAbilities.map { it.name })
        val bSpeed = rtB.Speed.essence
        assertNotNull(bSpeed)
        assertEquals("Wind", bSpeed!!.essence.name)
        assertEquals(listOf("Gust"), bSpeed.abilities.map { it.name })
    }

    @Test
    fun `deleteByName removes the build and all its dependent rows`() {
        val (driver, db) = newEnv()
        val a = sampleBuild(
            name = "Alpha",
            race = "Human",
            racialAbilityNames = listOf("RacialA1", "RacialA2"),
            slots = mapOf(("Power" to "Fire") to listOf("Flame Bolt", "Inferno")),
        )
        val b = sampleBuild(
            name = "Beta",
            race = "Elf",
            racialAbilityNames = listOf("RacialB1"),
            slots = mapOf(("Speed" to "Wind") to listOf("Gust")),
        )
        db.writeAll(listOf(a, b))

        db.deleteByName("Alpha")

        val read = db.readAll()
        assertEquals(listOf("Beta"), read.map { it.name })
        // Beta's data is intact.
        val rtB = read.single()
        assertEquals(listOf("RacialB1"), rtB.racialAbilities.map { it.name })
        val bSpeed = rtB.Speed.essence
        assertNotNull(bSpeed)
        assertEquals("Wind", bSpeed!!.essence.name)
        assertEquals(listOf("Gust"), bSpeed.abilities.map { it.name })

        // Raw-SQL assertions: no dependent rows for Alpha leaked.
        assertEquals(
            emptyList<String>(),
            selectColumn(driver, "SELECT listing_name FROM character_build_racial_ability WHERE build_name = 'Alpha'"),
        )
        assertEquals(
            emptyList<String>(),
            selectColumn(driver, "SELECT essence_name FROM character_build_attribute WHERE build_name = 'Alpha'"),
        )
        assertEquals(
            emptyList<String>(),
            selectColumn(driver, "SELECT listing_name FROM character_build_acquired_ability WHERE build_name = 'Alpha'"),
        )
        // And Beta's dependent rows still exist (sanity check that deletion was scoped).
        assertEquals(
            listOf("RacialB1"),
            selectColumn(driver, "SELECT listing_name FROM character_build_racial_ability WHERE build_name = 'Beta'"),
        )
    }

    @Test
    fun `up to 5 abilities per slot are preserved`() {
        val (_, db) = newEnv()
        val abilities = listOf("Ability1", "Ability2", "Ability3", "Ability4", "Ability5")
        val build = sampleBuild(
            name = "Hero",
            race = "Human",
            racialAbilityNames = emptyList(),
            slots = mapOf(("Power" to "Fire") to abilities),
        )

        db.writeAll(listOf(build))
        val rt = db.readAll().single()

        val absorbed = rt.Power.essence
        assertNotNull(absorbed)
        assertEquals(5, absorbed!!.abilities.size)
        assertEquals(abilities, absorbed.abilities.map { it.name })
    }

    @Test
    fun `up to 6 racial abilities are preserved`() {
        val (_, db) = newEnv()
        val racials = listOf("Racial1", "Racial2", "Racial3", "Racial4", "Racial5", "Racial6")
        val build = sampleBuild(
            name = "Hero",
            race = "Human",
            racialAbilityNames = racials,
            slots = emptyMap(),
        )

        db.writeAll(listOf(build))
        val rt = db.readAll().single()

        assertEquals(6, rt.racialAbilities.size)
        assertEquals(racials, rt.racialAbilities.map { it.name })
    }

    @Test
    fun `ordinals are preserved across read`() {
        val (_, db) = newEnv()
        // Title-case names because Ability.Listing.of() title-cases its input;
        // we want the round-tripped names to match insertion order exactly.
        val racials = listOf("First", "Second", "Third")
        val acquired = listOf("Alpha", "Beta", "Gamma")
        val build = sampleBuild(
            name = "Hero",
            race = "Human",
            racialAbilityNames = racials,
            slots = mapOf(("Power" to "Fire") to acquired),
        )

        db.writeAll(listOf(build))
        val rt = db.readAll().single()

        assertEquals(racials, rt.racialAbilities.map { it.name })
        val absorbed = rt.Power.essence
        assertNotNull(absorbed)
        assertEquals(acquired, absorbed!!.abilities.map { it.name })
    }

    @Test
    fun `writeAll wipes all prior rows`() {
        val (driver, db) = newEnv()
        val a = sampleBuild(
            name = "Alpha",
            race = "Human",
            racialAbilityNames = listOf("RacialA"),
            slots = mapOf(("Power" to "Fire") to listOf("Flame Bolt")),
        )
        val b = sampleBuild(
            name = "Beta",
            race = "Elf",
            racialAbilityNames = listOf("RacialB"),
            slots = mapOf(("Speed" to "Wind") to listOf("Gust")),
        )
        db.writeAll(listOf(a, b))

        val c = sampleBuild(
            name = "Gamma",
            race = "Dwarf",
            racialAbilityNames = listOf("RacialC"),
            slots = mapOf(("Spirit" to "Stone") to listOf("Boulder")),
        )
        db.writeAll(listOf(c))

        val read = db.readAll()
        assertEquals(listOf("Gamma"), read.map { it.name })

        // Cascade check: no orphaned dependent rows from A or B.
        val racialBuildNames = selectColumn(driver, "SELECT DISTINCT build_name FROM character_build_racial_ability")
        val attrBuildNames = selectColumn(driver, "SELECT DISTINCT build_name FROM character_build_attribute")
        val acquiredBuildNames = selectColumn(driver, "SELECT DISTINCT build_name FROM character_build_acquired_ability")
        assertTrue("racial rows should only reference Gamma but were $racialBuildNames", racialBuildNames.all { it == "Gamma" })
        assertTrue("attribute rows should only reference Gamma but were $attrBuildNames", attrBuildNames.all { it == "Gamma" })
        assertTrue("acquired rows should only reference Gamma but were $acquiredBuildNames", acquiredBuildNames.all { it == "Gamma" })
    }

    // --- Helpers ---------------------------------------------------------

    private fun selectColumn(driver: SqlDriver, sql: String): List<String> =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(buildList {
                    while (cursor.next().value) add(cursor.getString(0)!!)
                })
            },
            parameters = 0,
        ).value

    private fun manifestation(name: String): Essence.Manifestation = Essence.Manifestation(
        name = name,
        rank = Rank.Unranked,
        rarity = Rarity.Unknown,
        properties = emptyList(),
        description = "",
        isRestricted = false,
    )

    /**
     * Build a CharacterBuild with the given name, race, racial ability names, and
     * slot data. `slots` keys are (attributeKind, essenceName) pairs where
     * attributeKind is one of "Power"/"Speed"/"Spirit"/"Recovery". Values are
     * the names of acquired abilities in insertion order.
     */
    private fun sampleBuild(
        name: String,
        race: String,
        racialAbilityNames: List<String>,
        slots: Map<Pair<String, String>, List<String>>,
    ): CharacterBuild {
        fun absorbed(essenceName: String, abilityNames: List<String>): AbsorbedEssence {
            val essence = manifestation(essenceName)
            val abilities = abilityNames.map { abilityName ->
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
            return AbsorbedEssence(essence = essence, abilities = abilities)
        }

        fun attributeFor(kind: String): Attribute {
            val key = slots.keys.firstOrNull { it.first == kind }
            val absorbed = key?.let { absorbed(it.second, slots.getValue(it)) }
            return when (kind) {
                "Power" -> Attribute.Power(essence = absorbed)
                "Speed" -> Attribute.Speed(essence = absorbed)
                "Spirit" -> Attribute.Spirit(essence = absorbed)
                "Recovery" -> Attribute.Recovery(essence = absorbed)
                else -> error("Unknown attribute kind $kind")
            }
        }

        return CharacterBuild(
            name = name,
            race = race,
            racialAbilities = racialAbilityNames.map { Ability.Listing.of(it) },
            attributes = setOf(
                attributeFor("Power"),
                attributeFor("Speed"),
                attributeFor("Spirit"),
                attributeFor("Recovery"),
            ),
        )
    }
}
