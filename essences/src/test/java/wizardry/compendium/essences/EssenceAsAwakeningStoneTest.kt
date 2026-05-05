package wizardry.compendium.essences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity

class EssenceAsAwakeningStoneTest {

    @Test
    fun `toAwakeningStone copies name, rarity and description from manifestation`() {
        val essence = manifestation("Magma", rarity = Rarity.Uncommon, description = "Hot rock")
        val stone = essence.toAwakeningStone()

        assertEquals("Magma", stone.name)
        assertEquals(Rarity.Uncommon, stone.rarity)
        assertEquals("Hot rock", stone.description)
    }

    @Test
    fun `toAwakeningStone marks rank Unranked and properties Consumable+Essence`() {
        val stone = manifestation("Sword").toAwakeningStone()

        assertEquals(Rank.Unranked, stone.rank)
        assertTrue(Property.Consumable in stone.properties)
        assertTrue(Property.Essence in stone.properties)
    }

    @Test
    fun `toAwakeningStone leaves effects empty`() {
        val stone = manifestation("Wing").toAwakeningStone()

        assertEquals(emptyList<Any>(), stone.effects)
    }

    @Test
    fun `manifestationsNotMatchingStones returns all essences when no stones overlap`() {
        val essences = listOf(manifestation("Magma"), manifestation("Wing"))
        val stones = listOf(stone("Granite"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "Magma" })
        assertTrue(result.any { it.name == "Wing" })
    }

    @Test
    fun `manifestationsNotMatchingStones drops exact name match`() {
        val essences = listOf(manifestation("Granite"), manifestation("Wing"))
        val stones = listOf(stone("Granite"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(listOf("Wing"), result.map { it.name })
    }

    @Test
    fun `manifestationsNotMatchingStones is case insensitive`() {
        val essences = listOf(manifestation("granite"))
        val stones = listOf(stone("GRANITE"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(emptyList<Essence.Manifestation>(), result)
    }

    @Test
    fun `manifestationsNotMatchingStones trims whitespace`() {
        val essences = listOf(manifestation("  Granite  "))
        val stones = listOf(stone("Granite"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(emptyList<Essence.Manifestation>(), result)
    }

    @Test
    fun `manifestationsNotMatchingStones drops essence when stone name extends it (Dark vs Darkness)`() {
        val essences = listOf(manifestation("Dark"))
        val stones = listOf(stone("Darkness"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(emptyList<Essence.Manifestation>(), result)
    }

    @Test
    fun `manifestationsNotMatchingStones drops essence when essence name extends stone (Magma vs Mag)`() {
        val essences = listOf(manifestation("Magma"))
        val stones = listOf(stone("Mag"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(emptyList<Essence.Manifestation>(), result)
    }

    @Test
    fun `manifestationsNotMatchingStones requires at least 3 chars to prefix-match`() {
        // Two-char names shouldn't prefix-match every stone starting with those letters.
        val essences = listOf(manifestation("Ai"))
        val stones = listOf(stone("Aim"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(listOf("Ai"), result.map { it.name })
    }

    @Test
    fun `manifestationsNotMatchingStones does not match unrelated names sharing a prefix-of-prefix`() {
        // "Fire" is not a prefix of "Wildfire" and vice versa, so they should not collapse.
        val essences = listOf(manifestation("Fire"))
        val stones = listOf(stone("Wildfire"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(listOf("Fire"), result.map { it.name })
    }

    @Test
    fun `manifestationsNotMatchingStones evaluates each essence independently`() {
        val essences = listOf(
            manifestation("Magma"),     // dropped — exact match
            manifestation("Dark"),      // dropped — prefix of "Darkness"
            manifestation("Wing"),      // kept   — no overlap
            manifestation("Fire"),      // kept   — only "Wildfire" present
        )
        val stones = listOf(stone("Magma"), stone("Darkness"), stone("Wildfire"))

        val result = manifestationsNotMatchingStones(essences, stones)

        assertEquals(listOf("Wing", "Fire"), result.map { it.name })
    }

    @Test
    fun `manifestationsNotMatchingStones returns empty when essence list is empty`() {
        val result = manifestationsNotMatchingStones(emptyList(), listOf(stone("Granite")))

        assertEquals(emptyList<Essence.Manifestation>(), result)
    }

    @Test
    fun `manifestationsNotMatchingStones keeps everything when stone list is empty`() {
        val essences = listOf(manifestation("Magma"), manifestation("Wing"))

        val result = manifestationsNotMatchingStones(essences, emptyList())

        assertEquals(essences, result)
    }
}

private fun manifestation(
    name: String,
    rarity: Rarity = Rarity.Common,
    description: String = "$name essence",
): Essence.Manifestation = Essence.of(
    name = name,
    description = description,
    rarity = rarity,
    restricted = false,
)

private fun stone(name: String): AwakeningStone = AwakeningStone.of(name, Rarity.Common)
