package wizardry.compendium.essences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity

class EssenceConflictDetectionTest {

    @Test
    fun `no contributions returns empty`() {
        val canonical = listOf(manifestation("Wind"))
        assertEquals(emptyList<EssenceConflict>(), detectEssenceConflicts(canonical, emptyList()))
    }

    @Test
    fun `contribution that does not match canonical returns empty`() {
        val canonical = listOf(manifestation("Wind"))
        val contributions = listOf(manifestation("Sin"))
        assertEquals(emptyList<EssenceConflict>(), detectEssenceConflicts(canonical, contributions))
    }

    @Test
    fun `manifestation with same name as canonical manifestation is a name collision`() {
        val canonicalWind = manifestation("Wind")
        val contributedWind = manifestation("Wind")
        val conflicts = detectEssenceConflicts(listOf(canonicalWind), listOf(contributedWind))
        assertEquals(1, conflicts.size)
        val collision = conflicts.first() as EssenceConflict.NameCollision
        assertEquals(contributedWind, collision.contribution)
        assertEquals(canonicalWind, collision.canonical)
    }

    @Test
    fun `confluence with same name as canonical confluence is a name collision`() {
        val canonicalDoom = confluence("Doom", setOf(set("A", "B", "C")))
        val contributedDoom = confluence("Doom", setOf(set("D", "E", "F")))
        val conflicts = detectEssenceConflicts(listOf(canonicalDoom), listOf(contributedDoom))
        // name collision raised; combination collision suppressed because the canonical confluence
        // sharing the name is excluded from combination matching
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.first() is EssenceConflict.NameCollision)
    }

    @Test
    fun `manifestation contribution with same name as canonical confluence is a name collision`() {
        val canonical = listOf(confluence("Doom", setOf(set("A", "B", "C"))))
        val contributions = listOf(manifestation("Doom"))
        val conflicts = detectEssenceConflicts(canonical, contributions)
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.first() is EssenceConflict.NameCollision)
    }

    @Test
    fun `name comparison is normalized`() {
        val canonical = listOf(manifestation("Wind"))
        val contributions = listOf(manifestation("  wind  "))
        val conflicts = detectEssenceConflicts(canonical, contributions)
        assertEquals(1, conflicts.size)
    }

    @Test
    fun `combination conflict when contribution combination is owned by different canonical confluence`() {
        val canonicalTempest = confluence("Tempest", setOf(set("Wind", "Rain", "Storm")))
        val contributedDoom = confluence("Doom", setOf(set("Wind", "Rain", "Storm")))
        val conflicts = detectEssenceConflicts(listOf(canonicalTempest), listOf(contributedDoom))
        assertEquals(1, conflicts.size)
        val collision = conflicts.first() as EssenceConflict.CombinationCollision
        assertEquals(contributedDoom, collision.contribution)
        assertEquals(canonicalTempest, collision.canonicalOwner)
    }

    @Test
    fun `combination order does not matter`() {
        val canonical = listOf(confluence("Tempest", setOf(set("Wind", "Rain", "Storm"))))
        val contribution = listOf(confluence("Doom", setOf(set("Storm", "Wind", "Rain"))))
        assertEquals(1, detectEssenceConflicts(canonical, contribution).size)
    }

    @Test
    fun `same combination producing same confluence name is not a combination conflict`() {
        val canonicalDoom = confluence("Doom", setOf(set("A", "B", "C")))
        val contributedDoom = confluence("Doom", setOf(set("A", "B", "C")))
        val conflicts = detectEssenceConflicts(listOf(canonicalDoom), listOf(contributedDoom))
        // only the name collision; no combination collision (same name, same content)
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.first() is EssenceConflict.NameCollision)
    }

    @Test
    fun `multiple combinations detect each non-matching conflict separately`() {
        val canonicalTempest = confluence("Tempest", setOf(set("A", "B", "C")))
        val canonicalSin = confluence("Sin", setOf(set("D", "E", "F")))
        val contributedDoom = confluence(
            "Doom",
            setOf(set("A", "B", "C"), set("D", "E", "F"), set("G", "H", "I")),
        )
        val conflicts = detectEssenceConflicts(
            listOf(canonicalTempest, canonicalSin),
            listOf(contributedDoom),
        )
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.all { it is EssenceConflict.CombinationCollision })
    }

    @Test
    fun `name and combination conflicts on the same contribution can both surface`() {
        val canonicalDoom = confluence("Doom", setOf(set("A", "B", "C")))
        val canonicalTempest = confluence("Tempest", setOf(set("X", "Y", "Z")))
        val contributedDoom = confluence(
            "Doom",
            setOf(set("D", "E", "F"), set("X", "Y", "Z")),
        )
        val conflicts = detectEssenceConflicts(
            listOf(canonicalDoom, canonicalTempest),
            listOf(contributedDoom),
        )
        // name collision (Doom=Doom) + combination collision (XYZ owned by Tempest)
        assertEquals(2, conflicts.size)
        assertTrue(conflicts.any { it is EssenceConflict.NameCollision })
        assertTrue(conflicts.any { it is EssenceConflict.CombinationCollision })
    }
}

class AwakeningStoneConflictDetectionTest {

    @Test
    fun `no contributions returns empty`() {
        assertEquals(
            emptyList<AwakeningStoneConflict>(),
            detectAwakeningStoneConflicts(listOf(stone("Granite")), emptyList()),
        )
    }

    @Test
    fun `name collision is detected`() {
        val canonical = stone("Granite")
        val contribution = stone("Granite")
        val conflicts = detectAwakeningStoneConflicts(listOf(canonical), listOf(contribution))
        assertEquals(1, conflicts.size)
        val collision = conflicts.first() as AwakeningStoneConflict.NameCollision
        assertEquals(contribution, collision.contribution)
        assertEquals(canonical, collision.canonical)
    }

    @Test
    fun `name comparison is normalized for awakening stones`() {
        val canonical = stone("Granite")
        val contribution = stone(" GRANITE ")
        assertEquals(1, detectAwakeningStoneConflicts(listOf(canonical), listOf(contribution)).size)
    }

    @Test
    fun `non-matching contribution returns empty`() {
        assertEquals(
            emptyList<AwakeningStoneConflict>(),
            detectAwakeningStoneConflicts(listOf(stone("Granite")), listOf(stone("Marble"))),
        )
    }
}

class AbilityListingConflictDetectionTest {

    @Test
    fun `no contributions returns empty`() {
        assertEquals(
            emptyList<AbilityListingConflict>(),
            detectAbilityListingConflicts(listOf(listing("Fireball")), emptyList()),
        )
    }

    @Test
    fun `name collision is detected`() {
        val canonical = listing("Fireball")
        val contribution = listing("Fireball")
        val conflicts = detectAbilityListingConflicts(listOf(canonical), listOf(contribution))
        assertEquals(1, conflicts.size)
        val collision = conflicts.first() as AbilityListingConflict.NameCollision
        assertEquals(contribution, collision.contribution)
        assertEquals(canonical, collision.canonical)
    }

    @Test
    fun `name comparison is normalized for ability listings`() {
        val canonical = listing("Fireball")
        val contribution = listing("  fireball")
        assertEquals(1, detectAbilityListingConflicts(listOf(canonical), listOf(contribution)).size)
    }
}

private fun manifestation(name: String): Essence.Manifestation =
    Essence.of(name = name, description = "", rarity = Rarity.Common, restricted = false)

private fun confluence(name: String, combinations: Set<ConfluenceSet>): Essence.Confluence =
    Essence.Confluence(name = name, confluenceSets = combinations, isRestricted = false)

private fun set(a: String, b: String, c: String): ConfluenceSet =
    ConfluenceSet(manifestation(a), manifestation(b), manifestation(c))

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)

private fun listing(name: String): Ability.Listing = Ability.Listing(name = name, effects = emptyList())
