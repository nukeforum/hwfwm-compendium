package wizardry.compendium.repositories

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.AbsorbedEssence
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Attribute
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.persistence.AbilityListingDatabase
import wizardry.compendium.persistence.BuildRefResolver
import wizardry.compendium.persistence.CharacterBuildDatabase
import wizardry.compendium.persistence.CompendiumDatabase
import wizardry.compendium.persistence.EssenceDatabase
import kotlin.time.Duration

class DefaultIntegritySweepTest {

    private data class TestEnv(
        val sweep: DefaultIntegritySweep,
        val cbd: CharacterBuildDatabase,
        val ald: AbilityListingDatabase,
        val ed: EssenceDatabase,
    )

    private fun newEnv(
        canonicalListings: List<Ability.Listing> = emptyList(),
        canonicalEssences: List<Essence> = emptyList(),
        canonicalStatusEffects: List<StatusEffect> = emptyList(),
    ): TestEnv {
        val cbDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(cbDriver)
        val cbd = CharacterBuildDatabase(cbDriver)

        val aldDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(aldDriver)
        val ald = AbilityListingDatabase(aldDriver)

        val edDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CompendiumDatabase.Schema.create(edDriver)
        val ed = EssenceDatabase(edDriver)

        val sweep = DefaultIntegritySweep(
            abilityListingContributionsCache = ald,
            essenceContributionsCache = ed,
            characterBuildDatabase = cbd,
            abilityListingRepository = StubAbilityRepo(canonicalListings),
            essenceRepository = StubEssenceRepo(canonicalEssences),
            statusEffectRepository = StubStatusEffectRepo(canonicalStatusEffects),
        )
        return TestEnv(sweep, cbd, ald, ed)
    }

    private object CanonOnlyResolver : BuildRefResolver {
        override fun encodeListing(listing: Ability.Listing): String = "canon:${listing.name}"
        override fun encodeEssence(essence: Essence): String = "canon:${essence.name}"
    }

    @Test
    fun `empty environment produces zero issues`() = runBlocking {
        val env = newEnv()
        assertEquals(emptyList<IntegrityIssue>(), env.sweep.run())
    }

    @Test
    fun `canonical refs that resolve produce zero issues`() = runBlocking {
        val canonicalAbility = Ability.Listing.of("FireBolt")
        val canonicalEssence = manifestation("Fire")
        val env = newEnv(
            canonicalListings = listOf(canonicalAbility),
            canonicalEssences = listOf(canonicalEssence),
        )
        env.cbd.writeAll(
            listOf(buildWith("Pyro", powerEssence = canonicalEssence, powerAbilities = listOf(canonicalAbility))),
            CanonOnlyResolver,
        )

        assertEquals(emptyList<IntegrityIssue>(), env.sweep.run())
    }

    @Test
    fun `canonical ability ref pointing at unknown name produces OrphanedCanonicalRef`() = runBlocking {
        val env = newEnv(
            canonicalEssences = listOf(manifestation("Fire")),
            // "Missing" is intentionally absent from canonicalListings
        )
        env.cbd.writeAll(
            listOf(buildWith("Lost", powerEssence = manifestation("Fire"), powerAbilities = listOf(Ability.Listing.of("Missing")))),
            CanonOnlyResolver,
        )

        val issues = env.sweep.run()
        val orphans = issues.filterIsInstance<IntegrityIssue.OrphanedCanonicalRef>()
        assertTrue(orphans.any { it.name == "Missing" && it.kind == IntegrityIssue.OrphanedCanonicalRef.Kind.Ability })
    }

    @Test
    fun `contributed ability ref pointing at unknown id produces OrphanedContributedRef`() = runBlocking {
        val env = newEnv()
        val essenceId = env.ed.insertManifestation(manifestation("E"))
        val listing = Ability.Listing.of("Custom")
        val listingId = env.ald.insert(listing)

        env.cbd.writeAll(
            listOf(buildWith("X", powerEssence = manifestation("E"), powerAbilities = listOf(listing))),
            object : BuildRefResolver {
                override fun encodeListing(l: Ability.Listing) = "contr:$listingId"
                override fun encodeEssence(e: Essence) = "contr:$essenceId"
            },
        )
        // Delete the listing from the cache — leaves an orphan ref in the build
        env.ald.deleteById(listingId)

        val issues = env.sweep.run()
        val orphans = issues.filterIsInstance<IntegrityIssue.OrphanedContributedRef>()
        assertTrue(orphans.any { it.id == listingId && it.kind == IntegrityIssue.OrphanedContributedRef.Kind.Ability })
    }

    @Test
    fun `contributed essence ref pointing at unknown id produces OrphanedContributedRef`() = runBlocking {
        val env = newEnv()
        val essenceId = env.ed.insertManifestation(manifestation("E"))

        env.cbd.writeAll(
            listOf(buildWith("X", powerEssence = manifestation("E"))),
            object : BuildRefResolver {
                override fun encodeListing(l: Ability.Listing) = "canon:${l.name}"
                override fun encodeEssence(e: Essence) = "contr:$essenceId"
            },
        )
        // Delete essence from cache — leaves orphan in build
        env.ed.deleteManifestationById(essenceId)

        val issues = env.sweep.run()
        val orphans = issues.filterIsInstance<IntegrityIssue.OrphanedContributedRef>()
        assertTrue(orphans.any { it.id == essenceId && it.kind == IntegrityIssue.OrphanedContributedRef.Kind.Essence })
    }

    @Test
    fun `status token referencing unknown effect produces OrphanedStatusToken`() = runBlocking {
        val env = newEnv() // empty status effects
        env.ald.insert(
            Ability.Listing(
                name = "Burner",
                effects = listOf(
                    Effect.AbilityEffect(
                        rank = Rank.Iron,
                        type = AbilityType.Spell,
                        properties = listOf(Property.Fire),
                        cost = listOf(Cost.None),
                        cooldown = Duration.ZERO,
                        description = "Inflicts {status:Burn} on target.",
                        replacementKey = null,
                    ),
                ),
            ),
        )

        val issues = env.sweep.run()
        val orphanTokens = issues.filterIsInstance<IntegrityIssue.OrphanedStatusToken>()
        assertEquals(1, orphanTokens.size)
        assertEquals("Burner", orphanTokens.single().abilityName)
        assertEquals("Burn", orphanTokens.single().missingStatusName)
    }

    @Test
    fun `status token referencing known canonical effect produces zero issues`() = runBlocking {
        val knownStatus = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire),
            stackable = true,
            description = "fire dot",
        )
        val env = newEnv(canonicalStatusEffects = listOf(knownStatus))
        env.ald.insert(
            Ability.Listing(
                name = "Burner",
                effects = listOf(
                    Effect.AbilityEffect(
                        rank = Rank.Iron,
                        type = AbilityType.Spell,
                        properties = listOf(Property.Fire),
                        cost = listOf(Cost.None),
                        cooldown = Duration.ZERO,
                        description = "Inflicts {status:Burn} on target.",
                        replacementKey = null,
                    ),
                ),
            ),
        )

        val issues = env.sweep.run()
        assertEquals(0, issues.filterIsInstance<IntegrityIssue.OrphanedStatusToken>().size)
    }

    @Test
    fun `status token case-insensitive match produces zero issues`() = runBlocking {
        val knownStatus = StatusEffect(
            name = "Burn",
            type = StatusType.Affliction.Elemental,
            properties = listOf(Property.Fire),
            stackable = true,
            description = "fire dot",
        )
        val env = newEnv(canonicalStatusEffects = listOf(knownStatus))
        env.ald.insert(
            Ability.Listing(
                name = "Burner",
                effects = listOf(
                    Effect.AbilityEffect(
                        rank = Rank.Iron,
                        type = AbilityType.Spell,
                        properties = listOf(Property.Fire),
                        cost = listOf(Cost.None),
                        cooldown = Duration.ZERO,
                        description = "Inflicts {status:burn} on target.",
                        replacementKey = null,
                    ),
                ),
            ),
        )

        val issues = env.sweep.run()
        assertEquals(0, issues.filterIsInstance<IntegrityIssue.OrphanedStatusToken>().size)
    }

    @Test
    fun `no issues for build with no attributes referencing essences`() = runBlocking {
        val env = newEnv()
        env.cbd.writeAll(
            listOf(
                CharacterBuild(
                    name = "Empty",
                    race = "Human",
                    racialAbilities = emptyList(),
                    attributes = setOf(
                        Attribute.Power(), Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery(),
                    ),
                ),
            ),
            CanonOnlyResolver,
        )

        assertEquals(emptyList<IntegrityIssue>(), env.sweep.run())
    }

    // --- helpers ---

    private fun manifestation(name: String) = Essence.Manifestation(
        name = name,
        rank = Rank.Unranked,
        rarity = Rarity.Common,
        properties = emptyList(),
        description = "",
        isRestricted = false,
    )

    private fun buildWith(
        name: String,
        race: String = "Human",
        racial: List<Ability.Listing> = emptyList(),
        powerEssence: Essence? = null,
        powerAbilities: List<Ability.Listing> = emptyList(),
    ): CharacterBuild {
        val power: Attribute = if (powerEssence == null) {
            Attribute.Power()
        } else {
            val acquired = powerAbilities.map { listing ->
                Ability.Acquired(
                    name = listing.name,
                    effects = listing.effects,
                    rank = Rank.Iron,
                    tier = 0,
                    progress = 0f,
                    boundEssence = powerEssence,
                    listing = listing,
                )
            }
            Attribute.Power(essence = AbsorbedEssence(powerEssence, acquired))
        }
        return CharacterBuild(
            name = name,
            race = race,
            racialAbilities = racial,
            attributes = setOf(power, Attribute.Speed(), Attribute.Spirit(), Attribute.Recovery()),
        )
    }

    private class StubAbilityRepo(private val canonical: List<Ability.Listing>) : AbilityListingRepository {
        override val abilityListings: Flow<List<Ability.Listing>> = MutableStateFlow(canonical)
        override val conflicts: Flow<List<AbilityListingConflict>> = MutableStateFlow(emptyList())
        override suspend fun getAbilityListings() = canonical
        override suspend fun getContributions() = emptyList<Ability.Listing>()
        override suspend fun getConflicts() = emptyList<AbilityListingConflict>()
        override suspend fun saveAbilityListingContribution(listing: Ability.Listing): ContributionResult = error("not used")
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
        override suspend fun updateAbilityListingContribution(originalName: String, listing: Ability.Listing): ContributionResult = error("not used")
        override suspend fun checkDeleteImpact(name: String): wizardry.compendium.repositories.DeleteImpact = wizardry.compendium.repositories.DeleteImpact()
    }

    private class StubEssenceRepo(private val canonical: List<Essence>) : EssenceRepository {
        override val essences: Flow<List<Essence>> = MutableStateFlow(canonical)
        override val conflicts: Flow<List<EssenceConflict>> = MutableStateFlow(emptyList())
        override suspend fun getEssences() = canonical
        override suspend fun getContributions() = emptyList<Essence>()
        override suspend fun getConflicts() = emptyList<EssenceConflict>()
        override suspend fun saveManifestationContribution(manifestation: Essence.Manifestation): ContributionResult = error("not used")
        override suspend fun saveConfluenceContribution(
            confluence: Essence.Confluence,
            referencedManifestations: List<Essence.Manifestation>,
        ): ContributionResult = error("not used")
        override suspend fun addCombinationToConfluence(
            target: Essence.Confluence,
            combination: ConfluenceSet,
        ): ContributionResult = error("not used")
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
        override suspend fun updateManifestationContribution(originalName: String, manifestation: Essence.Manifestation): ContributionResult = error("not used")
        override suspend fun updateConfluenceContribution(originalName: String, confluence: Essence.Confluence): ContributionResult = error("not used")
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }

    private class StubStatusEffectRepo(private val canonical: List<StatusEffect>) : StatusEffectRepository {
        override val statusEffects: Flow<List<StatusEffect>> = MutableStateFlow(canonical)
        override val conflicts: Flow<List<StatusEffectConflict>> = MutableStateFlow(emptyList())
        override suspend fun getStatusEffects() = canonical
        override suspend fun getContributions() = emptyList<StatusEffect>()
        override suspend fun getConflicts() = emptyList<StatusEffectConflict>()
        override suspend fun saveStatusEffectContribution(effect: StatusEffect): ContributionResult = error("not used")
        override suspend fun isContribution(name: String) = false
        override suspend fun deleteContribution(name: String): ContributionResult = error("not used")
        override suspend fun updateStatusEffectContribution(originalName: String, effect: StatusEffect): ContributionResult = error("not used")
        override suspend fun checkDeleteImpact(name: String): DeleteImpact = DeleteImpact()
    }
}
