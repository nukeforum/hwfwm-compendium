package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.essences.dataloader.EssenceDataLoader
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.EssenceRef
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.domain.model.RefCodec
import wizardry.compendium.persistence.EssenceCache
import wizardry.compendium.persistence.IdentifiedConfluence
import wizardry.compendium.persistence.IdentifiedManifestation
import wizardry.compendium.persistence.RawConfluenceSet
import wizardry.compendium.preferences.EssenceContributionsToggle
import wizardry.compendium.preferences.EssenceContributionsToggleFlow

class DefaultEssenceRepositoryConflictTest {

    @Test
    fun `toggle off returns canonical regardless of conflicts`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Wind")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = false)

        assertEquals(canonical, repo.getEssences())
    }

    @Test
    fun `toggle on with no conflicts merges contributions`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Sin")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        val result = repo.getEssences()
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Wind" })
        assertTrue(result.any { it.name == "Sin" })
    }

    @Test
    fun `toggle on with name conflict returns canonical only`() = runTest {
        val canonical = listOf(manifestation("Wind"))
        val contribution = manifestation("Wind")
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        assertEquals(canonical, repo.getEssences())
    }

    @Test
    fun `toggle on with combination conflict returns canonical only`() = runTest {
        val wind = manifestation("Wind")
        val rain = manifestation("Rain")
        val storm = manifestation("Storm")
        val canonicalTempest = Essence.Confluence(
            name = "Tempest",
            confluenceSets = setOf(ConfluenceSet(setOf(wind, rain, storm))),
            isRestricted = false,
        )
        val contributedDoom = Essence.Confluence(
            name = "Doom",
            confluenceSets = setOf(ConfluenceSet(setOf(wind, rain, storm))),
            isRestricted = false,
        )
        // Include explicit manifestations so the canonical list matches what the repo returns.
        val canonical = listOf(wind, rain, storm, canonicalTempest)
        val repo = repository(
            canonical = canonical,
            contributions = listOf(contributedDoom),
            toggle = true,
        )

        val result = repo.getEssences()
        // When conflicts exist, canonical is returned. Check that the confluence is present
        // and no contributions leaked in.
        assertTrue(result.any { it is Essence.Confluence && it.name == "Tempest" })
        assertFalse(result.any { it is Essence.Confluence && it.name == "Doom" })
        // Sorted canonical: Rain, Storm, Tempest, Wind — check count.
        assertEquals(canonical.size, result.size)
    }

    @Test
    fun `getConflicts surfaces both name and combination conflicts`() = runTest {
        // Use Confluence-vs-Confluence name collisions; same-name Manifestation
        // contributions are stripped by the self-heal pass.
        val canonical = listOf(
            confluence("Aurora", setOf(set("M", "N", "O"))),
            confluence("Tempest", setOf(set("A", "B", "C"))),
        )
        val contributions = listOf(
            // Same-name Confluence that drops a canonical set → real NameCollision
            confluence("Aurora", setOf(set("P", "Q", "R"))),
            // Different name but claims a canonical's combination → CombinationCollision
            confluence("Doom", setOf(set("A", "B", "C"))),
        )
        val repo = repository(canonical = canonical, contributions = contributions, toggle = true)

        val conflicts = repo.getConflicts()
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.any { it is EssenceConflict.NameCollision })
        assertTrue(conflicts.any { it is EssenceConflict.CombinationCollision })
    }

    @Test
    fun `deleting the conflicting contribution clears the conflict and re-enables merge`() = runTest {
        val m = manifestation("M")
        val n = manifestation("N")
        val o = manifestation("O")
        val aurora = Essence.Confluence(
            name = "Aurora",
            confluenceSets = setOf(ConfluenceSet(setOf(m, n, o))),
            isRestricted = false,
        )
        // Include the member manifestations so the canonical list matches the repo result.
        val canonical = listOf(m, n, o, aurora)
        val contribution = confluence("Aurora", setOf(set("P", "Q", "R")))
        val repo = repository(canonical = canonical, contributions = listOf(contribution), toggle = true)

        // Initially gated to canonical because of the conflict — result includes M, N, O, Aurora.
        val gatedResult = repo.getEssences()
        assertEquals(canonical.size, gatedResult.size)
        assertTrue(gatedResult.any { it.name == "Aurora" })
        assertEquals(1, repo.getConflicts().size)

        repo.deleteContribution("Aurora")

        assertEquals(0, repo.getConflicts().size)
        // After deleting, the Aurora conflict is gone; Aurora from canonical is still present.
        val afterDelete = repo.getEssences()
        assertTrue(afterDelete.any { it is Essence.Confluence && it.name == "Aurora" })
        assertFalse(repo.isContribution("Aurora"))
    }

    @Test
    fun `addCombinationToConfluence on canonical produces no conflict and merges into the canonical entry`() = runTest {
        val canonicalDoom = confluence("Doom", setOf(set("A", "B", "C")))
        val repo = repository(
            canonical = listOf(canonicalDoom),
            contributions = emptyList(),
            toggle = true,
        )

        // Initially canonical is the only entry, no conflicts.
        assertEquals(0, repo.getConflicts().size)

        // Save a brand-new combination on the canonical Confluence.
        val newCombo = set("D", "E", "F")
        repo.addCombinationToConfluence(canonicalDoom, newCombo)

        assertEquals(0, repo.getConflicts().size)
        val merged = repo.getEssences()
        val doomInMerged = merged.first { it.name == "Doom" } as Essence.Confluence
        // The merged Doom should contain both canonical's set AND the user's addition.
        assertEquals(setOf(set("A", "B", "C"), set("D", "E", "F")), doomInMerged.confluenceSets)
    }

    @Test
    fun `addCombinationToConfluence does not mirror canonical Manifestations into contributions`() = runTest {
        // Real-world shape: canonical Confluence references canonical Manifestations.
        val a = manifestation("A")
        val b = manifestation("B")
        val c = manifestation("C")
        val d = manifestation("D")
        val e = manifestation("E")
        val f = manifestation("F")
        val canonicalDoom = Essence.Confluence(
            name = "Doom",
            confluenceSets = setOf(ConfluenceSet(setOf(a, b, c))),
            isRestricted = false,
        )
        val repo = repository(
            canonical = listOf(a, b, c, d, e, f, canonicalDoom),
            contributions = emptyList(),
            toggle = true,
        )

        // The user adds {D, E, F} — all canonical Manifestations.
        repo.addCombinationToConfluence(canonicalDoom, ConfluenceSet(setOf(d, e, f)))

        // No NameCollision should fire; the contributions cache must NOT contain
        // canonical-named Manifestation entries.
        assertEquals(emptyList<EssenceConflict>(), repo.getConflicts())
        val contributedManifestationNames = repo.getContributions()
            .filterIsInstance<Essence.Manifestation>()
            .map { it.name }
        assertEquals(emptyList<String>(), contributedManifestationNames)
    }

    @Test
    fun `removing a single conflicting combination keeps the rest of the contribution`() = runTest {
        val canonicalTempest = confluence("Tempest", setOf(set("A", "B", "C")))
        val originalDoom = confluence(
            "Doom",
            setOf(set("A", "B", "C"), set("D", "E", "F")),
        )
        val repo = repository(
            canonical = listOf(canonicalTempest),
            contributions = listOf(originalDoom),
            toggle = true,
        )

        assertEquals(1, repo.getConflicts().size)

        // Remove the offending combination only
        val cleanedDoom = originalDoom.copy(
            confluenceSets = originalDoom.confluenceSets.filterNot { it == set("A", "B", "C") }.toSet(),
        )
        repo.updateConfluenceContribution(cleanedDoom)

        assertEquals(0, repo.getConflicts().size)
        // Doom (cleaned) should now appear in merged results
        val merged = repo.getEssences()
        val doomInMerged = merged.firstOrNull { it.name == "Doom" } as? Essence.Confluence
        assertEquals(setOf(set("D", "E", "F")), doomInMerged?.confluenceSets)
    }
}

private fun repository(
    canonical: List<Essence>,
    contributions: List<Essence>,
    toggle: Boolean,
): DefaultEssenceRepository {
    // Collect all names present in the canonical list (both manifestations and confluence members)
    // so the contributions FakeEssenceCache encodes references to them as canon: refs instead of
    // duplicating them as contr: rows.
    val canonicalMemberNames: Set<String> = buildSet {
        canonical.filterIsInstance<Essence.Manifestation>().forEach { add(it.name) }
        canonical.filterIsInstance<Essence.Confluence>().forEach { c ->
            c.confluenceSets.forEach { set -> set.set.forEach { add(it.name) } }
        }
    }
    val canonicalCache = FakeEssenceCache(canonical)
    val contributionsCache = FakeEssenceCache(contributions, externalCanonicalNames = canonicalMemberNames)
    val toggleSource = FakeEssenceToggle(toggle)
    val toggleFlow = FakeEssenceToggleFlow(toggle)
    val loader = FakeEssenceDataLoader(canonical)
    return DefaultEssenceRepository(
        dataLoader = loader,
        canonicalCache = canonicalCache,
        contributionsCache = contributionsCache,
        toggle = toggleSource,
        toggleFlow = toggleFlow,
    )
}

/**
 * Fake EssenceCache for tests.
 *
 * Explicit [Essence.Manifestation] entries in the initial list become `identified`
 * rows (visible to the repository as standalone essences). Confluence member
 * manifestations not already in the list are interned:
 *   - Names in [externalCanonicalNames] are encoded as `canon:<name>` refs so that
 *     `readCacheAsEssences` resolves them via the fallback canonical map, and they
 *     do NOT appear as standalone identified manifestations in this cache.
 *   - All other names are encoded as `contr:<id>` refs and added to
 *     [identifiedManifestations].
 *
 * Pass [externalCanonicalNames] when building a *contributions* cache whose confluences
 * reference canonical manifestations (so those names aren't accidentally duplicated as
 * contributed rows). Leave it empty (default) for the canonical cache, where all members
 * should be stored as real rows.
 */
private class FakeEssenceCache(
    initial: List<Essence>,
    private val externalCanonicalNames: Set<String> = emptySet(),
) : EssenceCache {
    private val manifestationRows: MutableList<IdentifiedManifestation> = mutableListOf()
    // contr-id ref lookup for members (only non-canonical interned ones have rows in manifestationRows)
    private val internMap: MutableMap<String, Long> = mutableMapOf() // name -> id
    private val confluenceRows: MutableList<IdentifiedConfluence> = mutableListOf()
    private var nextId = 0L

    init {
        initial.filterIsInstance<Essence.Manifestation>().forEach { m ->
            val id = nextId++
            manifestationRows.add(IdentifiedManifestation(id, m))
            internMap[m.name] = id
        }
        initial.filterIsInstance<Essence.Confluence>().forEach { c ->
            val encodedSets = c.confluenceSets.map { cs ->
                val sorted = cs.set.sortedBy { it.name }
                require(sorted.size == 3) { "Test confluences must have exactly 3 members" }
                RawConfluenceSet(
                    essence1Ref = encodeRef(sorted[0]),
                    essence2Ref = encodeRef(sorted[1]),
                    essence3Ref = encodeRef(sorted[2]),
                    isRestricted = cs.isRestricted,
                )
            }
            confluenceRows.add(IdentifiedConfluence(nextId++, c.name, c.isRestricted, encodedSets))
        }
    }

    /**
     * Encode a ref for a confluence member.
     * - If the member's name is in [externalCanonicalNames], use `canon:<name>`.
     * - Otherwise, allocate/reuse a contr id and add a row to [manifestationRows] if new.
     */
    private fun encodeRef(m: Essence.Manifestation): String {
        if (m.name in externalCanonicalNames) {
            return RefCodec.encodeEssenceRef(EssenceRef.Canonical(m.name))
        }
        val existing = internMap[m.name]
        if (existing != null) return RefCodec.encodeEssenceRef(EssenceRef.Contributed(existing))
        val id = nextId++
        internMap[m.name] = id
        manifestationRows.add(IdentifiedManifestation(id, m))
        return RefCodec.encodeEssenceRef(EssenceRef.Contributed(id))
    }

    override val identifiedManifestations: List<IdentifiedManifestation> get() = manifestationRows.toList()
    override val identifiedConfluences: List<IdentifiedConfluence> get() = confluenceRows.toList()

    override fun insertManifestation(manifestation: Essence.Manifestation): Long {
        val id = nextId++
        manifestationRows.add(IdentifiedManifestation(id, manifestation))
        internMap[manifestation.name] = id
        return id
    }
    override fun updateManifestation(id: Long, manifestation: Essence.Manifestation) {
        val idx = manifestationRows.indexOfFirst { it.id == id }
        if (idx >= 0) {
            internMap.remove(manifestationRows[idx].manifestation.name)
            manifestationRows[idx] = IdentifiedManifestation(id, manifestation)
            internMap[manifestation.name] = id
        }
    }
    override fun deleteManifestationById(id: Long) {
        val row = manifestationRows.firstOrNull { it.id == id }
        if (row != null) {
            internMap.remove(row.manifestation.name)
            manifestationRows.remove(row)
        }
    }
    override fun findManifestationIdByName(name: String): Long? =
        manifestationRows.firstOrNull { it.manifestation.name == name }?.id

    override fun insertConfluence(name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>): Long {
        val id = nextId++; confluenceRows.add(IdentifiedConfluence(id, name, isRestricted, sets)); return id
    }
    override fun updateConfluence(id: Long, name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>) {
        val idx = confluenceRows.indexOfFirst { it.id == id }
        if (idx >= 0) confluenceRows[idx] = IdentifiedConfluence(id, name, isRestricted, sets)
    }
    override fun deleteConfluenceById(id: Long) { confluenceRows.removeAll { it.id == id } }
    override fun findConfluenceIdByName(name: String): Long? =
        confluenceRows.firstOrNull { it.name == name }?.id

    override fun replaceAll(essences: List<Essence>) {
        manifestationRows.clear(); confluenceRows.clear(); internMap.clear(); nextId = 0
        essences.filterIsInstance<Essence.Manifestation>().forEach {
            val id = nextId++
            manifestationRows.add(IdentifiedManifestation(id, it))
            internMap[it.name] = id
        }
        essences.filterIsInstance<Essence.Confluence>().forEach { c ->
            val encodedSets = c.confluenceSets.map { cs ->
                val sorted = cs.set.sortedBy { it.name }
                require(sorted.size == 3) { "Test confluences must have exactly 3 members" }
                RawConfluenceSet(
                    essence1Ref = encodeRef(sorted[0]),
                    essence2Ref = encodeRef(sorted[1]),
                    essence3Ref = encodeRef(sorted[2]),
                    isRestricted = cs.isRestricted,
                )
            }
            confluenceRows.add(IdentifiedConfluence(nextId++, c.name, c.isRestricted, encodedSets))
        }
    }
}

private class FakeEssenceToggle(override val isEssenceContributionsEnabled: Boolean) :
    EssenceContributionsToggle

private class FakeEssenceToggleFlow(initial: Boolean) : EssenceContributionsToggleFlow {
    private val state = MutableStateFlow(initial)
    override val essenceContributionsEnabled: Flow<Boolean> = state
}

private class FakeEssenceDataLoader(private val data: List<Essence>) : EssenceDataLoader {
    override suspend fun loadEssenceData(): List<Essence> = data
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun confluence(name: String, combinations: Set<ConfluenceSet>): Essence.Confluence =
    Essence.Confluence(name = name, confluenceSets = combinations, isRestricted = false)

private fun set(a: String, b: String, c: String): ConfluenceSet =
    ConfluenceSet(manifestation(a), manifestation(b), manifestation(c))
