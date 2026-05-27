package wizardry.compendium.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.essences.dataloader.StatusEffectDataLoader
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.Effect
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.persistence.AbilityListingCache
import wizardry.compendium.persistence.IdentifiedListing
import wizardry.compendium.persistence.IdentifiedStatusEffect
import wizardry.compendium.persistence.StatusEffectCache
import wizardry.compendium.preferences.StatusEffectContributionsToggle
import wizardry.compendium.preferences.StatusEffectContributionsToggleFlow
import kotlin.time.Duration.Companion.seconds

class DefaultStatusEffectRepositoryTest {

    private fun effect(name: String) = StatusEffect(
        name = name,
        type = StatusType.Affliction.Curse,
        properties = emptyList(),
        stackable = false,
        description = "",
    )

    private fun abilityEffect(description: String) = Effect.AbilityEffect(
        rank = Rank.Iron,
        type = AbilityType.Spell,
        properties = emptyList(),
        cost = listOf(Cost.None),
        cooldown = 0.seconds,
        description = description,
    )

    private fun listing(name: String, vararg descriptions: String): Ability.Listing =
        Ability.Listing(
            name = name,
            effects = descriptions.map { abilityEffect(it) },
        )

    // ──────────────────────────────────────────────────────────────────────────
    // Fake StatusEffectCache
    // ──────────────────────────────────────────────────────────────────────────

    private class FakeStatusEffectCache(initial: List<StatusEffect> = emptyList()) : StatusEffectCache {
        private val rows = initial.mapIndexed { i, e -> IdentifiedStatusEffect(i.toLong(), e) }.toMutableList()
        private var nextId = initial.size.toLong()
        override val identified: List<IdentifiedStatusEffect> get() = rows.toList()
        override fun insert(statusEffect: StatusEffect): Long {
            val id = nextId++; rows.add(IdentifiedStatusEffect(id, statusEffect)); return id
        }
        override fun update(id: Long, statusEffect: StatusEffect) {
            val idx = rows.indexOfFirst { it.id == id }
            if (idx >= 0) rows[idx] = IdentifiedStatusEffect(id, statusEffect)
        }
        override fun deleteById(id: Long) { rows.removeAll { it.id == id } }
        override fun findIdByName(name: String): Long? =
            rows.firstOrNull { it.statusEffect.name == name }?.id
        override fun replaceAll(statusEffects: List<StatusEffect>) {
            rows.clear(); nextId = 0
            statusEffects.forEach { rows.add(IdentifiedStatusEffect(nextId++, it)) }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Fake AbilityListingCache — supports effect-level id tracking so the
    // bulkRewriteStatusTokens cascade can be exercised end-to-end.
    // ──────────────────────────────────────────────────────────────────────────

    private class FakeAbilityListingCache : AbilityListingCache {
        // effectId -> (listingId, description)
        private val effectRows = mutableMapOf<Long, Pair<Long, String>>()
        private val listingRows = mutableMapOf<Long, Ability.Listing>()
        private var nextListingId = 0L
        private var nextEffectId = 0L

        override val identified: List<IdentifiedListing>
            get() = listingRows.entries.map { (id, listing) ->
                // Reconstruct listing with up-to-date descriptions from effectRows
                val updatedEffects = listing.effects.mapIndexed { idx, effect ->
                    val effectId = effectRows.entries.firstOrNull { (_, v) ->
                        v.first == id && listing.effects.indexOf(effect) == idx
                    }?.key
                    if (effectId != null) {
                        effect.copy(description = effectRows[effectId]!!.second)
                    } else effect
                }
                IdentifiedListing(id, listing.copy(effects = updatedEffects))
            }

        override fun insert(listing: Ability.Listing): Long {
            val listingId = nextListingId++
            listingRows[listingId] = listing
            listing.effects.forEach { effect ->
                val effectId = nextEffectId++
                effectRows[effectId] = listingId to effect.description
            }
            return listingId
        }

        override fun update(id: Long, listing: Ability.Listing) {
            // Remove old effects for this listing
            effectRows.entries.removeIf { it.value.first == id }
            listingRows[id] = listing
            listing.effects.forEach { effect ->
                val effectId = nextEffectId++
                effectRows[effectId] = id to effect.description
            }
        }

        override fun deleteById(id: Long) {
            listingRows.remove(id)
            effectRows.entries.removeIf { it.value.first == id }
        }

        override fun findIdByName(name: String): Long? =
            listingRows.entries.firstOrNull { it.value.name == name }?.key

        override fun replaceAll(listings: List<Ability.Listing>) {
            listingRows.clear()
            effectRows.clear()
            nextListingId = 0
            nextEffectId = 0
            listings.forEach { insert(it) }
        }

        override fun bulkRewriteStatusTokens(
            rewrite: (effectId: Long, description: String) -> String?,
        ): Int {
            val tokenRegex = Regex("""\{status:[^}]+\}""", RegexOption.IGNORE_CASE)
            var updated = 0
            for ((effectId, entry) in effectRows.toMap()) {
                if (!tokenRegex.containsMatchIn(entry.second)) continue
                val rewritten = rewrite(effectId, entry.second) ?: continue
                effectRows[effectId] = entry.first to rewritten
                updated++
            }
            return updated
        }

        /** Get the current description for an effect by its id — test helper. */
        fun descriptionForEffect(effectId: Long): String? = effectRows[effectId]?.second

        /** Get all effect descriptions for a listing by name — test helper. */
        fun descriptionsForListing(listingName: String): List<String> {
            val listingId = findIdByName(listingName) ?: return emptyList()
            return effectRows.entries
                .filter { it.value.first == listingId }
                .map { it.value.second }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private class FakeLoader(private val data: List<StatusEffect>) : StatusEffectDataLoader {
        override suspend fun loadStatusEffectData(): List<StatusEffect> = data
    }

    private fun toggleOn() = object : StatusEffectContributionsToggle {
        override val isStatusEffectContributionsEnabled = true
    }

    private fun toggleOff() = object : StatusEffectContributionsToggle {
        override val isStatusEffectContributionsEnabled = false
    }

    private fun toggleFlowOn() = object : StatusEffectContributionsToggleFlow {
        override val statusEffectContributionsEnabled = MutableStateFlow(true)
    }

    private fun makeRepo(
        canonical: List<StatusEffect> = emptyList(),
        contributed: List<StatusEffect> = emptyList(),
        abilityListingContributionsCache: FakeAbilityListingCache = FakeAbilityListingCache(),
        toggle: StatusEffectContributionsToggle = toggleOn(),
    ): DefaultStatusEffectRepository = DefaultStatusEffectRepository(
        dataLoader = FakeLoader(canonical),
        canonicalCache = FakeStatusEffectCache(),
        contributionsCache = FakeStatusEffectCache(contributed),
        abilityListingContributionsCache = abilityListingContributionsCache,
        toggle = toggle,
        toggleFlow = toggleFlowOn(),
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Existing tests (updated to use helpers)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `getStatusEffects returns canonical only when toggle is off`() = runTest {
        val canonical = listOf(effect("Burn"))
        val contribs = listOf(effect("Chill"))
        val repo = makeRepo(canonical = canonical, contributed = contribs, toggle = toggleOff())
        assertEquals(canonical, repo.getStatusEffects())
    }

    @Test
    fun `getStatusEffects merges canonical and contributions when toggle is on`() = runTest {
        val canonical = listOf(effect("Burn"))
        val contribs = listOf(effect("Chill"))
        val repo = makeRepo(canonical = canonical, contributed = contribs)
        val merged = repo.getStatusEffects().map { it.name }
        assertEquals(listOf("Burn", "Chill"), merged)
    }

    @Test
    fun `getStatusEffects returns canonical only when contribution conflicts exist`() = runTest {
        val burn = effect("Burn")
        val repo = makeRepo(
            canonical = listOf(burn),
            contributed = listOf(burn.copy(description = "user")),
        )
        assertEquals(listOf(burn), repo.getStatusEffects())
    }

    @Test
    fun `save then delete round-trip`() = runTest {
        val canonical = listOf(effect("Burn"))
        val abilityCache = FakeAbilityListingCache()
        val statusCache = FakeStatusEffectCache()
        val repo = DefaultStatusEffectRepository(
            dataLoader = FakeLoader(canonical),
            canonicalCache = FakeStatusEffectCache(),
            contributionsCache = statusCache,
            abilityListingContributionsCache = abilityCache,
            toggle = toggleOn(),
            toggleFlow = toggleFlowOn(),
        )
        val chill = effect("Chill")
        assertEquals(ContributionResult.Success, repo.saveStatusEffectContribution(chill))
        assertTrue(repo.isContribution("chill"))
        assertEquals(ContributionResult.Success, repo.deleteContribution("Chill"))
        assertTrue(statusCache.contents.isEmpty())
    }

    @Test
    fun `save fails when name collides with canonical`() = runTest {
        val repo = makeRepo(canonical = listOf(effect("Burn")))
        val result = repo.saveStatusEffectContribution(effect("burn"))
        assertTrue(result is ContributionResult.Failure)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // update / rename tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `update preserves id across rename`() = runTest {
        val repo = makeRepo(contributed = listOf(effect("Burn")))
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Inferno"),
        )
        assertEquals(ContributionResult.Success, result)
        val contribs = repo.getContributions()
        assertEquals(1, contribs.size)
        assertEquals("Inferno", contribs.single().name)
    }

    @Test
    fun `update fails when new name collides with canonical`() = runTest {
        val repo = makeRepo(
            canonical = listOf(effect("Chill")),
            contributed = listOf(effect("Burn")),
        )
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("chill"),
        )
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `update fails when new name collides with another contribution`() = runTest {
        val repo = makeRepo(contributed = listOf(effect("Burn"), effect("Chill")))
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Chill"),
        )
        assertTrue(result is ContributionResult.Failure)
    }

    @Test
    fun `update no-op rename succeeds with same name`() = runTest {
        val repo = makeRepo(contributed = listOf(effect("Burn")))
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Burn"),
        )
        assertEquals(ContributionResult.Success, result)
    }

    @Test
    fun `update fails when originalName not found`() = runTest {
        val repo = makeRepo(contributed = listOf(effect("Burn")))
        val result = repo.updateStatusEffectContribution(
            originalName = "NoSuchEffect",
            effect = effect("Inferno"),
        )
        assertTrue(result is ContributionResult.Failure)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Cascade-rewrite tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `rename rewrites status tokens in contributed ability descriptions`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:Burn} on target."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Inferno"),
        )

        assertEquals(ContributionResult.Success, result)
        val descriptions = abilityCache.descriptionsForListing("Pyro")
        assertEquals("Inflicts {status:Inferno} on target.", descriptions.single())
    }

    @Test
    fun `rename preserves the new name exact casing in rewritten token`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Deals {status:burn} damage."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("InFerNo"),
        )

        val descriptions = abilityCache.descriptionsForListing("Pyro")
        assertEquals("Deals {status:InFerNo} damage.", descriptions.single())
    }

    @Test
    fun `rename is idempotent A to B to A returns descriptions to original state`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:Burn} on target."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)

        // A → B
        repo.updateStatusEffectContribution(originalName = "Burn", effect = effect("Inferno"))
        // B → A
        repo.updateStatusEffectContribution(originalName = "Inferno", effect = effect("Burn"))

        val descriptions = abilityCache.descriptionsForListing("Pyro")
        assertEquals("Inflicts {status:Burn} on target.", descriptions.single())
    }

    @Test
    fun `rename Burn does not affect {status_Burning} tokens`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:Burning} on target."))

        val repo = makeRepo(
            contributed = listOf(effect("Burn"), effect("Burning")),
            abilityListingContributionsCache = abilityCache,
        )
        repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Inferno"),
        )

        // {status:Burning} must remain unchanged — only exact matches are rewritten
        val descriptions = abilityCache.descriptionsForListing("Pyro")
        assertEquals("Inflicts {status:Burning} on target.", descriptions.single())
    }

    @Test
    fun `rename does NOT affect canonical ability descriptions — only contributed`() = runTest {
        // The abilityListingContributionsCache only contains contributed abilities;
        // canonical ones are a separate cache and are not touched.
        val abilityCache = FakeAbilityListingCache()
        // nothing inserted — simulates canonical cache being separate

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        val result = repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Inferno"),
        )

        assertEquals(ContributionResult.Success, result)
        // No contributed abilities to rewrite — nothing to check, just no crash
    }

    @Test
    fun `rename rewrites multiple tokens across multiple abilities`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Deals {status:Burn} damage."))
        abilityCache.insert(listing("ArcaneFlame", "Applies {status:Burn} and then {status:Burn} again."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Inferno"),
        )

        assertEquals("Deals {status:Inferno} damage.", abilityCache.descriptionsForListing("Pyro").single())
        assertEquals(
            "Applies {status:Inferno} and then {status:Inferno} again.",
            abilityCache.descriptionsForListing("ArcaneFlame").single(),
        )
    }

    @Test
    fun `no rename does not rewrite tokens`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Deals {status:Burn} damage."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        // Same name — no rewrite should happen
        repo.updateStatusEffectContribution(
            originalName = "Burn",
            effect = effect("Burn").copy(description = "updated description"),
        )

        // Token unchanged
        val descriptions = abilityCache.descriptionsForListing("Pyro")
        assertEquals("Deals {status:Burn} damage.", descriptions.single())
    }

    // ──────────────────────────────────────────────────────────────────────────
    // checkDeleteImpact tests
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `checkDeleteImpact returns ability names whose descriptions reference this status effect`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:Burn} on target."))
        abilityCache.insert(listing("Frostbolt", "Applies {status:Chill} to the target."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        val impact = repo.checkDeleteImpact("Burn")

        assertEquals(listOf("Pyro"), impact.referencingAbilityListings)
    }

    @Test
    fun `checkDeleteImpact returns empty when no abilities reference this status effect`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Frostbolt", "Applies {status:Chill} to the target."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        val impact = repo.checkDeleteImpact("Burn")

        assertTrue(impact.isEmpty)
    }

    @Test
    fun `checkDeleteImpact is case-insensitive`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:BURN} on target."))

        val repo = makeRepo(contributed = listOf(effect("Burn")), abilityListingContributionsCache = abilityCache)
        val impact = repo.checkDeleteImpact("burn")

        assertEquals(listOf("Pyro"), impact.referencingAbilityListings)
    }

    @Test
    fun `checkDeleteImpact prefix safety — Burn does not match Burning`() = runTest {
        val abilityCache = FakeAbilityListingCache()
        abilityCache.insert(listing("Pyro", "Inflicts {status:Burning} on target."))

        val repo = makeRepo(
            contributed = listOf(effect("Burn"), effect("Burning")),
            abilityListingContributionsCache = abilityCache,
        )
        val impact = repo.checkDeleteImpact("Burn")

        // {status:Burning} is NOT a reference to "Burn"
        assertTrue(impact.isEmpty)
    }
}
