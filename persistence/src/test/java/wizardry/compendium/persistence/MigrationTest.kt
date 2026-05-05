package wizardry.compendium.persistence

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration safety tests. User-contributed data can take hours to craft, so a failed
 * upgrade is catastrophic. Every historical user_version must be able to reach the
 * current Schema.version with their existing rows intact.
 */
class MigrationTest {

    @Test
    fun `schema version is 4`() {
        assertEquals(4L, CompendiumDatabase.Schema.version)
    }

    @Test
    fun `fresh create at v4 produces all current tables`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(driver)

        val tables = listTables(driver)
        ALL_CURRENT_TABLES.forEach { expected ->
            assertTrue("missing table $expected after create()", expected in tables)
        }
    }

    @Test
    fun `upgrade from v1 (Essences-only) to v4 preserves manifestation rows`() {
        // The very first persistent schema (commit 618b3a9): only the Essences tables.
        // No subsequent migration touches Essences columns, so any rows here must
        // round-trip untouched.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedSampleEssenceData(driver)

        migrate(driver, from = 1, to = 4)

        assertHasAllCurrentTables(driver)
        assertEssenceDataIntact(driver)
    }

    @Test
    fun `upgrade from v2 (post-1_sqm) to v4 preserves rows in all v2 tables`() {
        // After 1.sqm: essences + ability_effect family + awakening_stone.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        seedSampleAbilityEffect(driver, listingName = null)  // ability_listing doesn't exist yet at v2

        migrate(driver, from = 2, to = 4)

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

        migrate(driver, from = 3, to = 4)

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
    fun `applying every migration step incrementally yields the same schema as fresh create`() {
        // Belt-and-braces: walk the migration ladder one step at a time and confirm we
        // arrive at the same set of tables a fresh install produces.
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(migrated)
        migrate(migrated, from = 1, to = 2)
        migrate(migrated, from = 2, to = 3)
        migrate(migrated, from = 3, to = 4)

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
        )
    }
}
