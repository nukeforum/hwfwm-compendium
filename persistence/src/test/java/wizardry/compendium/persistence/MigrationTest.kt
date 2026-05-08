package wizardry.compendium.persistence

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
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
 * Migration safety tests. User-contributed data can take hours to craft, so a failed
 * upgrade is catastrophic. Every historical user_version must be able to reach the
 * current Schema.version with their existing rows intact.
 */
class MigrationTest {

    @Test
    fun `schema version is 5`() {
        assertEquals(5L, CompendiumDatabase.Schema.version)
    }

    @Test
    fun `fresh create at v5 produces all current tables`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)

        val tables = listTables(driver)
        ALL_CURRENT_TABLES.forEach { expected ->
            assertTrue("missing table $expected after create()", expected in tables)
        }
    }

    @Test
    fun `upgrade from v1 (Essences-only) to v5 preserves manifestation rows`() {
        // The very first persistent schema (commit 618b3a9): only the Essences tables.
        // No subsequent migration touches Essences columns, so any rows here must
        // round-trip untouched.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedSampleEssenceData(driver)

        migrate(driver, from = 1, to = 5)

        assertHasAllCurrentTables(driver)
        assertEssenceDataIntact(driver)
    }

    @Test
    fun `upgrade from v2 (post-1_sqm) to v5 preserves rows in all v2 tables`() {
        // After 1.sqm: essences + ability_effect family + awakening_stone.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        seedSampleAbilityEffect(driver, listingName = null)  // ability_listing doesn't exist yet at v2

        migrate(driver, from = 2, to = 5)

        assertHasAllCurrentTables(driver)
        assertEssenceDataIntact(driver)
        assertEquals(listOf("UserStone"), selectColumn(driver, "SELECT name FROM awakening_stone"))
        assertEquals(listOf("AbilityEffectRank"), selectColumn(driver, "SELECT rank FROM ability_effect"))
    }

    @Test
    fun `upgrade from v3 missing status_effect creates it without losing data`() {
        // The C1 hazard: a device created its DB at user_version=3 BEFORE migrations/3.sqm
        // existed AND BEFORE c17eae3 added status_effect to create(). status_effect is missing.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedV2ToV3Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        seedSampleAbilityListing(driver)
        seedSampleAbilityEffect(driver, listingName = "UserListing")

        migrate(driver, from = 3, to = 5)

        assertHasAllCurrentTables(driver)
        assertEssenceDataIntact(driver)
        assertEquals(listOf("UserStone"), selectColumn(driver, "SELECT name FROM awakening_stone"))
        assertEquals(listOf("UserListing"), selectColumn(driver, "SELECT name FROM ability_listing"))
        assertEquals(listOf("AbilityEffectRank"), selectColumn(driver, "SELECT rank FROM ability_effect"))
    }

    @Test
    fun `upgrade from v3 that already has status_effect with rows is a no-op for those rows`() {
        // Devices that did fresh-install at c17eae3 are at user_version=3 WITH status_effect.
        // The 3->4 migration must not destroy or duplicate their status_effect rows.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedV2ToV3Tables(driver)
        seedV3ToV4Tables(driver)
        driver.execute(null, "INSERT INTO status_effect VALUES ('PreExisting', 'Affliction.Elemental', 0, 'desc', '')", 0)

        migrate(driver, from = 3, to = 4)

        assertEquals(listOf("PreExisting"), selectColumn(driver, "SELECT name FROM status_effect"))
    }

    @Test
    fun `upgrade from v4 to v5 creates character_build tables and preserves rows in every pre-existing table`() {
        // Seed every table that exists at v4 with a recognizable user-row, run the
        // 4->5 migration, and assert the new character_build family appears empty
        // while every pre-existing user row survives untouched.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedV2ToV3Tables(driver)
        seedV3ToV4Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        seedSampleAbilityListing(driver)
        seedSampleAbilityEffect(driver, listingName = "UserListing")
        driver.execute(null, "INSERT INTO status_effect VALUES ('UserStatus', 'Affliction.Elemental', 0, 'desc', '')", 0)

        migrate(driver, from = 4, to = 5)

        assertHasAllCurrentTables(driver)
        assertEssenceDataIntact(driver)
        assertEquals(listOf("UserStone"), selectColumn(driver, "SELECT name FROM awakening_stone"))
        assertEquals(listOf("UserListing"), selectColumn(driver, "SELECT name FROM ability_listing"))
        assertEquals(listOf("AbilityEffectRank"), selectColumn(driver, "SELECT rank FROM ability_effect"))
        assertEquals(listOf("UserStatus"), selectColumn(driver, "SELECT name FROM status_effect"))
        // The four new tables exist and are empty.
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM character_build"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT build_name FROM character_build_racial_ability"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT build_name FROM character_build_attribute"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT build_name FROM character_build_acquired_ability"))
    }

    @Test
    fun `upgrade from v4 already populated with the character_build tables is a safe no-op`() {
        // c17eae3-style scenario: a device drifted into the v5 table layout before the
        // migration shipped. The 4->5 migration uses CREATE TABLE IF NOT EXISTS, so
        // pre-existing seeded rows in those tables MUST survive without duplication.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedV2ToV3Tables(driver)
        seedV3ToV4Tables(driver)
        seedV4ToV5Tables(driver)
        driver.execute(null, "INSERT INTO character_build(name, race) VALUES ('PreExistingBuild', 'Human')", 0)
        driver.execute(null, "INSERT INTO character_build_racial_ability(build_name, listing_name, ordinal) VALUES ('PreExistingBuild', 'PreExistingRacial', 0)", 0)
        driver.execute(null, "INSERT INTO character_build_attribute(build_name, kind, essence_name) VALUES ('PreExistingBuild', 'Power', 'PreExistingEssence')", 0)
        driver.execute(null, "INSERT INTO character_build_acquired_ability(build_name, attribute_kind, listing_name, rank, tier, progress, ordinal) VALUES ('PreExistingBuild', 'Power', 'PreExistingAbility', 'Iron', 1, 0.5, 0)", 0)

        migrate(driver, from = 4, to = 5)

        assertEquals(listOf("PreExistingBuild"), selectColumn(driver, "SELECT name FROM character_build"))
        assertEquals(listOf("PreExistingRacial"), selectColumn(driver, "SELECT listing_name FROM character_build_racial_ability"))
        assertEquals(listOf("PreExistingEssence"), selectColumn(driver, "SELECT essence_name FROM character_build_attribute"))
        assertEquals(listOf("PreExistingAbility"), selectColumn(driver, "SELECT listing_name FROM character_build_acquired_ability"))
        assertEquals(listOf("Iron"), selectColumn(driver, "SELECT rank FROM character_build_acquired_ability"))
    }

    @Test
    fun `CharacterBuild round trips through CharacterBuildDatabase against the migrated v5 schema`() {
        // Walk the migration ladder up to v5, then exercise CharacterBuildDatabase
        // writeAll/readAll with a build that populates name, race, racial abilities,
        // and a Power attribute with two acquired abilities at distinct rank/tier/progress.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        migrate(driver, from = 1, to = 2)
        migrate(driver, from = 2, to = 3)
        migrate(driver, from = 3, to = 4)
        migrate(driver, from = 4, to = 5)

        val placeholderEssence = Essence.Manifestation(
            name = "Fire",
            rank = Rank.Unranked,
            rarity = Rarity.Unknown,
            properties = emptyList(),
            description = "",
            isRestricted = false,
        )
        val acquiredA = Ability.Acquired(
            name = "Flame Bolt",
            effects = emptyList(),
            rank = Rank.Iron,
            tier = 2,
            progress = 0.25f,
            boundEssence = placeholderEssence,
            listing = Ability.Listing.of("Flame Bolt"),
        )
        val acquiredB = Ability.Acquired(
            name = "Inferno",
            effects = emptyList(),
            rank = Rank.Bronze,
            tier = 0,
            progress = 0.75f,
            boundEssence = placeholderEssence,
            listing = Ability.Listing.of("Inferno"),
        )
        val build = CharacterBuild(
            name = "Hero",
            race = "Human",
            racialAbilities = listOf(Ability.Listing.of("Trait1"), Ability.Listing.of("Trait2")),
            attributes = setOf(
                Attribute.Power(essence = AbsorbedEssence(placeholderEssence, listOf(acquiredA, acquiredB))),
                Attribute.Speed(),
                Attribute.Spirit(),
                Attribute.Recovery(),
            ),
        )

        val db = CharacterBuildDatabase(driver)
        db.writeAll(listOf(build))
        val read = db.readAll()

        assertEquals(1, read.size)
        val roundTripped = read.single()
        assertEquals("Hero", roundTripped.name)
        assertEquals("Human", roundTripped.race)
        assertEquals(listOf("Trait1", "Trait2"), roundTripped.racialAbilities.map { it.name })

        val power = roundTripped.Power
        val absorbed = power.essence
        assertTrue("expected Power attribute to carry an essence", absorbed != null)
        assertEquals("Fire", absorbed!!.essence.name)
        assertEquals(2, absorbed.abilities.size)

        val first = absorbed.abilities[0]
        assertEquals("Flame Bolt", first.name)
        assertEquals(Rank.Iron, first.rank)
        assertEquals(2, first.tier)
        assertEquals(0.25f, first.progress, 0.0001f)

        val second = absorbed.abilities[1]
        assertEquals("Inferno", second.name)
        assertEquals(Rank.Bronze, second.rank)
        assertEquals(0, second.tier)
        assertEquals(0.75f, second.progress, 0.0001f)

        // Unset attributes must round-trip as essence-less placeholders.
        assertEquals(null, roundTripped.Speed.essence)
        assertEquals(null, roundTripped.Spirit.essence)
        assertEquals(null, roundTripped.Recovery.essence)
    }

    @Test
    fun `applying every migration step incrementally yields the same schema as fresh create`() {
        // Belt-and-braces: walk the migration ladder one step at a time and confirm we
        // arrive at the same set of tables a fresh install produces.
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(migrated)
        migrate(migrated, from = 1, to = 2)
        migrate(migrated, from = 2, to = 3)
        migrate(migrated, from = 3, to = 4)
        migrate(migrated, from = 4, to = 5)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(fresh)

        assertEquals(listTables(fresh).sorted(), listTables(migrated).sorted())
    }

    // --- Helpers ---------------------------------------------------------

    private fun migrate(driver: SqlDriver, from: Long, to: Long) {
        CompendiumDatabase.Schema.migrate(driver, from, to, callbacks = arrayOf<AfterVersion>())
    }

    private fun seedV1Schema(driver: SqlDriver) {
        // Verbatim from the original Essences.sq (commit 618b3a9, unchanged since).
        driver.execute(null, "CREATE TABLE manifestation (name TEXT PRIMARY KEY NOT NULL, rarity TEXT NOT NULL, description TEXT NOT NULL, is_restricted INTEGER NOT NULL DEFAULT 0)", 0)
        driver.execute(null, "CREATE TABLE confluence (name TEXT PRIMARY KEY NOT NULL, is_restricted INTEGER NOT NULL DEFAULT 0)", 0)
        driver.execute(null, "CREATE TABLE confluence_set (id INTEGER PRIMARY KEY AUTOINCREMENT, confluence_name TEXT NOT NULL REFERENCES confluence(name), essence1 TEXT NOT NULL REFERENCES manifestation(name), essence2 TEXT NOT NULL REFERENCES manifestation(name), essence3 TEXT NOT NULL REFERENCES manifestation(name), is_restricted INTEGER NOT NULL DEFAULT 0)", 0)
    }

    private fun seedV1ToV2Tables(driver: SqlDriver) {
        driver.execute(null, "CREATE TABLE ability_effect (id INTEGER PRIMARY KEY AUTOINCREMENT, listing_name TEXT NOT NULL REFERENCES ability_listing(name), rank TEXT NOT NULL, type TEXT NOT NULL, cooldown_seconds INTEGER NOT NULL, description TEXT NOT NULL, replacement_key TEXT, ordinal INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE effect_property (effect_id INTEGER NOT NULL REFERENCES ability_effect(id), property TEXT NOT NULL, ordinal INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE effect_cost (effect_id INTEGER NOT NULL REFERENCES ability_effect(id), kind TEXT NOT NULL, amount TEXT NOT NULL, resource TEXT NOT NULL, ordinal INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE awakening_stone (name TEXT PRIMARY KEY NOT NULL, rarity TEXT NOT NULL)", 0)
    }

    private fun seedV2ToV3Tables(driver: SqlDriver) {
        driver.execute(null, "CREATE TABLE ability_listing (name TEXT PRIMARY KEY NOT NULL)", 0)
    }

    private fun seedV3ToV4Tables(driver: SqlDriver) {
        driver.execute(null, "CREATE TABLE status_effect (name TEXT PRIMARY KEY NOT NULL, type TEXT NOT NULL, stackable INTEGER NOT NULL, description TEXT NOT NULL, properties TEXT NOT NULL)", 0)
    }

    private fun seedV4ToV5Tables(driver: SqlDriver) {
        // No IF NOT EXISTS — historical helpers represent a clean state at a specific
        // version. Mirrors the column definitions in CharacterBuilds.sq.
        driver.execute(null, "CREATE TABLE character_build (name TEXT PRIMARY KEY NOT NULL, race TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE character_build_racial_ability (build_name TEXT NOT NULL REFERENCES character_build(name), listing_name TEXT NOT NULL, ordinal INTEGER NOT NULL, PRIMARY KEY (build_name, ordinal))", 0)
        driver.execute(null, "CREATE TABLE character_build_attribute (build_name TEXT NOT NULL REFERENCES character_build(name), kind TEXT NOT NULL, essence_name TEXT NOT NULL, PRIMARY KEY (build_name, kind))", 0)
        driver.execute(null, "CREATE TABLE character_build_acquired_ability (build_name TEXT NOT NULL, attribute_kind TEXT NOT NULL, listing_name TEXT NOT NULL, rank TEXT NOT NULL, tier INTEGER NOT NULL, progress REAL NOT NULL, ordinal INTEGER NOT NULL, PRIMARY KEY (build_name, attribute_kind, ordinal), FOREIGN KEY (build_name, attribute_kind) REFERENCES character_build_attribute(build_name, kind))", 0)
    }

    private fun seedSampleEssenceData(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('UserEssence', 'Common', 'user contribution', 0)", 0)
        driver.execute(null, "INSERT INTO confluence(name, is_restricted) VALUES ('UserConfluence', 0)", 0)
    }

    private fun seedSampleAwakeningStone(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO awakening_stone(name, rarity) VALUES ('UserStone', 'Rare')", 0)
    }

    private fun seedSampleAbilityListing(driver: SqlDriver) {
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('UserListing')", 0)
    }

    private fun seedSampleAbilityEffect(driver: SqlDriver, listingName: String?) {
        // FK is declared but SQLite default has FK enforcement off, so this works
        // even when ability_listing doesn't yet exist.
        val name = listingName ?: "OrphanListing"
        driver.execute(
            null,
            "INSERT INTO ability_effect(listing_name, rank, type, cooldown_seconds, description, replacement_key, ordinal) " +
                "VALUES ('$name', 'AbilityEffectRank', 'attack', 0, 'desc', NULL, 0)",
            0,
        )
    }

    private fun assertEssenceDataIntact(driver: SqlDriver) {
        assertEquals(listOf("UserEssence"), selectColumn(driver, "SELECT name FROM manifestation"))
        assertEquals(listOf("UserConfluence"), selectColumn(driver, "SELECT name FROM confluence"))
    }

    private fun assertHasAllCurrentTables(driver: SqlDriver) {
        val tables = listTables(driver)
        ALL_CURRENT_TABLES.forEach { expected ->
            assertTrue("missing table $expected after migration", expected in tables)
        }
    }

    private fun listTables(driver: SqlDriver): List<String> =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
            mapper = { cursor ->
                QueryResult.Value(buildList {
                    while (cursor.next().value) add(cursor.getString(0)!!)
                })
            },
            parameters = 0,
        ).value

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

    companion object {
        private val ALL_CURRENT_TABLES = listOf(
            "manifestation", "confluence", "confluence_set",
            "awakening_stone",
            "ability_listing", "ability_effect", "effect_property", "effect_cost",
            "status_effect",
            "character_build", "character_build_racial_ability",
            "character_build_attribute", "character_build_acquired_ability",
        )
    }
}
