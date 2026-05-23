package wizardry.compendium.persistence

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity

/**
 * Migration safety tests. User-contributed data can take hours to craft, so a failed
 * upgrade is catastrophic. Every historical user_version must be able to reach the
 * current Schema.version with their existing rows intact.
 */
class MigrationTest {

    @Test
    fun `schema version is 6`() {
        assertEquals(6L, CompendiumDatabase.Schema.version)
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
        migrate(migrated, from = 5, to = 6)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(fresh)

        assertEquals(listTables(fresh).sorted(), listTables(migrated).sorted())
    }

    // --- v5 -> v6 migration tests ---------------------------------------
    //
    // The v5 -> v6 migration (migrations/5.sqm) adds surrogate INTEGER ids to
    // the five contributable entity tables, translates the two intra-DB
    // name-FKs (ability_effect.listing_name, confluence_set.confluence_name)
    // into integer FKs, and re-encodes six cross-domain reference columns as
    // tagged TEXT strings ('contr:<id>' if the name resolves in the current
    // DB at migration time, otherwise 'canon:<name>'). The six ref columns:
    //   confluence_set.essence{1,2,3}_ref
    //   character_build_racial_ability.listing_ref
    //   character_build_attribute.essence_ref
    //   character_build_acquired_ability.listing_ref
    //
    // For v5 -> v6 tests we seed the full v5 shape via the historical
    // helpers (which produce a clean state at version 5) and then call
    // migrate(driver, from = 5, to = 6).

    @Test
    fun `v5 to v6 with empty contributions produces clean v6 schema with zero rows`() {
        val driver = freshV5Driver()

        migrate(driver, from = 5, to = 6)

        assertHasAllCurrentTables(driver)
        // Every entity table is empty post-migration.
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM ability_listing"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM manifestation"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM confluence"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM awakening_stone"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT name FROM status_effect"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_effect"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM confluence_set"))
        // The five entity tables now expose an `id` column.
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_listing"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM confluence"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM awakening_stone"))
        assertEquals(emptyList<String>(), selectColumn(driver, "SELECT CAST(id AS TEXT) FROM status_effect"))
    }

    @Test
    fun `v5 to v6 assigns ids to contributed ability listings preserving names`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('A')", 0)
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('B')", 0)
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('C')", 0)

        migrate(driver, from = 5, to = 6)

        val rows = selectColumn(driver, "SELECT id || '|' || name FROM ability_listing ORDER BY name")
        assertEquals(3, rows.size)
        val byName = rows.map { it.split("|") }.associate { it[1] to it[0].toLong() }
        assertEquals(setOf("A", "B", "C"), byName.keys)
        // Ids are distinct and ascending in insertion-by-name order.
        val ids = byName.values.toList()
        assertEquals(ids.toSet().size, ids.size)
        assertTrue("ids must all be positive", ids.all { it > 0 })
    }

    @Test
    fun `v5 to v6 translates ability_effect listing_name to integer FK`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('Pyromancy')", 0)
        driver.execute(
            null,
            "INSERT INTO ability_effect(listing_name, rank, type, cooldown_seconds, description, replacement_key, ordinal) " +
                "VALUES ('Pyromancy', 'Iron', 'attack', 10, 'first', NULL, 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO ability_effect(listing_name, rank, type, cooldown_seconds, description, replacement_key, ordinal) " +
                "VALUES ('Pyromancy', 'Bronze', 'attack', 20, 'second', NULL, 1)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        val pyroId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_listing WHERE name = 'Pyromancy'")
            .single().toLong()
        val effectListingIds = selectColumn(driver, "SELECT CAST(listing_id AS TEXT) FROM ability_effect ORDER BY ordinal")
        assertEquals(listOf(pyroId.toString(), pyroId.toString()), effectListingIds)
        // The descriptions and ordinals must survive untouched.
        assertEquals(listOf("first", "second"), selectColumn(driver, "SELECT description FROM ability_effect ORDER BY ordinal"))
        assertEquals(listOf("Iron", "Bronze"), selectColumn(driver, "SELECT rank FROM ability_effect ORDER BY ordinal"))
    }

    @Test
    fun `v5 to v6 translates confluence_set columns to FK + tagged refs`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO confluence(name, is_restricted) VALUES ('FirebrandX', 0)", 0)
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('Fire', 'Common', 'fire', 0)", 0)
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('Earth', 'Common', 'earth', 0)", 0)
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('Wind', 'Common', 'wind', 0)", 0)
        driver.execute(
            null,
            "INSERT INTO confluence_set(confluence_name, essence1, essence2, essence3, is_restricted) " +
                "VALUES ('FirebrandX', 'Fire', 'Earth', 'Wind', 0)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        val confId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM confluence WHERE name = 'FirebrandX'").single().toLong()
        val fireId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation WHERE name = 'Fire'").single().toLong()
        val earthId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation WHERE name = 'Earth'").single().toLong()
        val windId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation WHERE name = 'Wind'").single().toLong()

        val row = selectColumn(
            driver,
            "SELECT CAST(confluence_id AS TEXT) || '#' || essence1_ref || '#' || essence2_ref || '#' || essence3_ref FROM confluence_set",
        ).single().split("#")
        assertEquals(confId.toString(), row[0])
        assertEquals("contr:$fireId", row[1])
        assertEquals("contr:$earthId", row[2])
        assertEquals("contr:$windId", row[3])
    }

    @Test
    fun `v5 to v6 encodes build refs to contributed entities as contr id`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('CustomA')", 0)
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('CustomE', 'Common', 'd', 0)", 0)
        driver.execute(null, "INSERT INTO character_build(name, race) VALUES ('MyBuild', 'Human')", 0)
        driver.execute(
            null,
            "INSERT INTO character_build_racial_ability(build_name, listing_name, ordinal) VALUES ('MyBuild', 'CustomA', 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_attribute(build_name, kind, essence_name) VALUES ('MyBuild', 'Power', 'CustomE')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_acquired_ability(build_name, attribute_kind, listing_name, rank, tier, progress, ordinal) " +
                "VALUES ('MyBuild', 'Power', 'CustomA', 'Iron', 0, 0.0, 0)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        val idA = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_listing WHERE name = 'CustomA'").single().toLong()
        val idE = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation WHERE name = 'CustomE'").single().toLong()
        assertEquals(
            listOf("contr:$idA"),
            selectColumn(driver, "SELECT listing_ref FROM character_build_racial_ability"),
        )
        assertEquals(
            listOf("contr:$idE"),
            selectColumn(driver, "SELECT essence_ref FROM character_build_attribute"),
        )
        assertEquals(
            listOf("contr:$idA"),
            selectColumn(driver, "SELECT listing_ref FROM character_build_acquired_ability"),
        )
        // Surrounding scalar fields on the acquired-ability row survive the rebuild.
        assertEquals(listOf("Iron"), selectColumn(driver, "SELECT rank FROM character_build_acquired_ability"))
        assertEquals(listOf("0"), selectColumn(driver, "SELECT CAST(tier AS TEXT) FROM character_build_acquired_ability"))
    }

    @Test
    fun `v5 to v6 encodes orphan build refs as canon name`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO character_build(name, race) VALUES ('ChicagoBuild', 'Human')", 0)
        driver.execute(
            null,
            "INSERT INTO character_build_racial_ability(build_name, listing_name, ordinal) VALUES ('ChicagoBuild', 'NonexistentAbility', 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_attribute(build_name, kind, essence_name) VALUES ('ChicagoBuild', 'Power', 'NonexistentEssence')",
            0,
        )

        migrate(driver, from = 5, to = 6)

        assertEquals(
            listOf("canon:NonexistentAbility"),
            selectColumn(driver, "SELECT listing_ref FROM character_build_racial_ability"),
        )
        assertEquals(
            listOf("canon:NonexistentEssence"),
            selectColumn(driver, "SELECT essence_ref FROM character_build_attribute"),
        )
    }

    @Test
    fun `v5 to v6 mixed refs encode independently`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('ExistingA')", 0)
        driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('ExistingE', 'Common', 'd', 0)", 0)
        driver.execute(null, "INSERT INTO character_build(name, race) VALUES ('MixedBuild', 'Human')", 0)
        driver.execute(
            null,
            "INSERT INTO character_build_racial_ability(build_name, listing_name, ordinal) VALUES ('MixedBuild', 'ExistingA', 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_racial_ability(build_name, listing_name, ordinal) VALUES ('MixedBuild', 'NoSuchAbility', 1)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_attribute(build_name, kind, essence_name) VALUES ('MixedBuild', 'Power', 'ExistingE')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO character_build_attribute(build_name, kind, essence_name) VALUES ('MixedBuild', 'Speed', 'NoSuchEssence')",
            0,
        )

        migrate(driver, from = 5, to = 6)

        val idA = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_listing WHERE name = 'ExistingA'").single().toLong()
        val idE = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM manifestation WHERE name = 'ExistingE'").single().toLong()
        assertEquals(
            listOf("contr:$idA", "canon:NoSuchAbility"),
            selectColumn(driver, "SELECT listing_ref FROM character_build_racial_ability ORDER BY ordinal"),
        )
        assertEquals(
            listOf("contr:$idE", "canon:NoSuchEssence"),
            selectColumn(driver, "SELECT essence_ref FROM character_build_attribute ORDER BY kind"),
        )
    }

    @Test
    fun `v5 to v6 leaves status tokens in ability descriptions verbatim`() {
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('Burner')", 0)
        driver.execute(
            null,
            "INSERT INTO ability_effect(listing_name, rank, type, cooldown_seconds, description, replacement_key, ordinal) " +
                "VALUES ('Burner', 'Iron', 'attack', 0, 'Inflicts {status:Burn} on the target.', NULL, 0)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        assertEquals(
            listOf("Inflicts {status:Burn} on the target."),
            selectColumn(driver, "SELECT description FROM ability_effect"),
        )
    }

    @Test
    fun `v5 to v6 preserves entity row counts`() {
        val driver = freshV5Driver()
        // 5 abilities
        listOf("a1", "a2", "a3", "a4", "a5").forEach { n ->
            driver.execute(null, "INSERT INTO ability_listing(name) VALUES ('$n')", 0)
        }
        // 4 essences (manifestations)
        listOf("m1", "m2", "m3", "m4").forEach { n ->
            driver.execute(null, "INSERT INTO manifestation(name, rarity, description, is_restricted) VALUES ('$n', 'Common', 'd', 0)", 0)
        }
        // 2 confluences
        listOf("c1", "c2").forEach { n ->
            driver.execute(null, "INSERT INTO confluence(name, is_restricted) VALUES ('$n', 0)", 0)
        }
        // 3 awakening stones
        listOf("s1", "s2", "s3").forEach { n ->
            driver.execute(null, "INSERT INTO awakening_stone(name, rarity) VALUES ('$n', 'Rare')", 0)
        }
        // 6 status effects
        listOf("se1", "se2", "se3", "se4", "se5", "se6").forEach { n ->
            driver.execute(null, "INSERT INTO status_effect(name, type, stackable, description, properties) VALUES ('$n', 'Affliction.Elemental', 0, 'd', '')", 0)
        }

        migrate(driver, from = 5, to = 6)

        assertEquals(5, countRows(driver, "ability_listing"))
        assertEquals(4, countRows(driver, "manifestation"))
        assertEquals(2, countRows(driver, "confluence"))
        assertEquals(3, countRows(driver, "awakening_stone"))
        assertEquals(6, countRows(driver, "status_effect"))
    }

    @Test
    fun `v5 to v6 creates the three build-ref indexes`() {
        val driver = freshV5Driver()

        migrate(driver, from = 5, to = 6)

        val indexes = selectColumn(driver, "SELECT name FROM sqlite_master WHERE type='index'")
        assertTrue("idx_racial_ability_listing_ref missing; got $indexes", "idx_racial_ability_listing_ref" in indexes)
        assertTrue("idx_acquired_ability_listing_ref missing; got $indexes", "idx_acquired_ability_listing_ref" in indexes)
        assertTrue("idx_attribute_essence_ref missing; got $indexes", "idx_attribute_essence_ref" in indexes)
    }

    @Test
    fun `v5 to v6 drops ability_effect rows whose listing_name has no matching ability_listing`() {
        // Documented behavior: orphan ability_effect rows are dropped during the
        // copy-rename rebuild because their listing_name can't satisfy the new
        // NOT NULL integer FK. The migration writer chose drop-over-fail so that
        // an out-of-band FK violation can't brick the device.
        val driver = freshV5Driver()
        // No matching ability_listing row -- this effect is an orphan.
        driver.execute(
            null,
            "INSERT INTO ability_effect(listing_name, rank, type, cooldown_seconds, description, replacement_key, ordinal) " +
                "VALUES ('Ghost', 'Iron', 'attack', 0, 'orphaned', NULL, 0)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        assertEquals(0, countRows(driver, "ability_effect"))
        // The migration must still succeed and produce the v6 shape.
        assertHasAllCurrentTables(driver)
    }

    @Test
    fun `v5 to v6 acquired ability rows without matching attribute parent are dropped`() {
        // The acquired-ability table's composite FK (build_name, attribute_kind)
        // -> character_build_attribute(build_name, kind) is unchanged from v5 to
        // v6. The migration text does NOT add an EXISTS guard against the
        // attribute parent (see character_build_acquired_ability_new INSERT in
        // 5.sqm). Orphan acquired-ability rows are carried through verbatim --
        // the FK enforcement is identical to v5, so what was tolerable in v5
        // (FK pragma off) remains tolerable in v6.
        //
        // We still seed an orphan and assert the row survives, because the prompt
        // documents this as a possible orphan-drop pathway and we want a
        // regression alarm if the migration ever starts silently filtering
        // these rows. If the actual behavior diverges from this expectation,
        // the assertion below is the canary.
        val driver = freshV5Driver()
        driver.execute(null, "INSERT INTO character_build(name, race) VALUES ('Lonely', 'Human')", 0)
        // No matching attribute row exists for ('Lonely', 'Power').
        driver.execute(
            null,
            "INSERT INTO character_build_acquired_ability(build_name, attribute_kind, listing_name, rank, tier, progress, ordinal) " +
                "VALUES ('Lonely', 'Power', 'SomeAbility', 'Iron', 0, 0.0, 0)",
            0,
        )

        migrate(driver, from = 5, to = 6)

        // The migration does not gate the acquired_ability copy on the existence
        // of a parent attribute row; the row passes through with its listing_ref
        // re-encoded. SQLDelight runs migrations with FK enforcement off, so
        // this is structurally consistent with v5.
        assertEquals(
            listOf("canon:SomeAbility"),
            selectColumn(driver, "SELECT listing_ref FROM character_build_acquired_ability"),
        )
    }

    @Test
    fun `upgrade from v1 (Essences-only) to v6 preserves manifestation rows`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedSampleEssenceData(driver)

        migrate(driver, from = 1, to = 6)

        assertHasAllCurrentTables(driver)
        assertEquals(listOf("UserEssence"), selectColumn(driver, "SELECT name FROM manifestation"))
        assertEquals(listOf("UserConfluence"), selectColumn(driver, "SELECT name FROM confluence"))
        // v6: each entity row now has an integer id.
        assertEquals(1, countRows(driver, "manifestation"))
        assertEquals(1, countRows(driver, "confluence"))
    }

    @Test
    fun `upgrade from v2 (post-1_sqm) to v6 preserves rows in all v2 tables`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        // ability_listing does not exist at v2, so an effect referencing a name has
        // no v6 listing to point at -- it would be dropped. We skip the effect for
        // this ladder test; v5->v6 row-preservation of effects is exercised above.

        migrate(driver, from = 2, to = 6)

        assertHasAllCurrentTables(driver)
        assertEquals(listOf("UserEssence"), selectColumn(driver, "SELECT name FROM manifestation"))
        assertEquals(listOf("UserConfluence"), selectColumn(driver, "SELECT name FROM confluence"))
        assertEquals(listOf("UserStone"), selectColumn(driver, "SELECT name FROM awakening_stone"))
        // awakening_stone now has an id column.
        assertEquals(1, countRows(driver, "awakening_stone"))
    }

    @Test
    fun `upgrade from v3 to v6 preserves rows in every pre-existing table`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        seedV1Schema(driver)
        seedV1ToV2Tables(driver)
        seedV2ToV3Tables(driver)
        seedSampleEssenceData(driver)
        seedSampleAwakeningStone(driver)
        seedSampleAbilityListing(driver)
        seedSampleAbilityEffect(driver, listingName = "UserListing")

        migrate(driver, from = 3, to = 6)

        assertHasAllCurrentTables(driver)
        assertEquals(listOf("UserEssence"), selectColumn(driver, "SELECT name FROM manifestation"))
        assertEquals(listOf("UserConfluence"), selectColumn(driver, "SELECT name FROM confluence"))
        assertEquals(listOf("UserStone"), selectColumn(driver, "SELECT name FROM awakening_stone"))
        assertEquals(listOf("UserListing"), selectColumn(driver, "SELECT name FROM ability_listing"))
        // ability_effect.listing_id now points at the surrogate id of UserListing.
        val listingId = selectColumn(driver, "SELECT CAST(id AS TEXT) FROM ability_listing WHERE name = 'UserListing'").single().toLong()
        assertEquals(
            listOf(listingId.toString()),
            selectColumn(driver, "SELECT CAST(listing_id AS TEXT) FROM ability_effect"),
        )
        assertEquals(listOf("AbilityEffectRank"), selectColumn(driver, "SELECT rank FROM ability_effect"))
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
