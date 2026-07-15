package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.Rank
import java.io.ByteArrayInputStream
import java.io.InputStream

class AbilityListingCsvLoaderTest {

    private fun loader(csv: String) = AbilityListingCsvLoader(
        source = object : FileStreamSource {
            override fun getInputStreamFor(filename: String): InputStream {
                assertEquals("ability_listings.csv", filename)
                return ByteArrayInputStream(csv.toByteArray())
            }
        },
    )

    @Test
    fun `empty file loads as empty list`() = runBlocking {
        assertTrue(loader("").loadAbilityListingData().isEmpty())
    }

    @Test
    fun `name-only row loads a listing with no effects`() = runBlocking {
        val result = loader("Mystery Gift\n").loadAbilityListingData()

        assertEquals(listOf("Mystery Gift"), result.map { it.name })
        assertTrue(result.single().effects.isEmpty())
    }

    @Test
    fun `three-column row loads a single typed effect with the description`() = runBlocking {
        val result = loader(
            "Magic Affinity,Racial ability,Effects with the magic sub-type are enhanced.\n",
        ).loadAbilityListingData()

        val effect = result.single().effects.single()
        assertEquals(AbilityType.RacialAbility, effect.type)
        assertEquals(Rank.Unranked, effect.rank)
        assertEquals("Effects with the magic sub-type are enhanced.", effect.description)
    }

    @Test
    fun `description is the last column and may contain commas`() = runBlocking {
        val result = loader(
            "Dragon Breath,Racial ability,Special attack, specific form varies with dragon ancestry.\n",
        ).loadAbilityListingData()

        assertEquals(
            "Special attack, specific form varies with dragon ancestry.",
            result.single().effects.single().description,
        )
    }

    @Test
    fun `rows are sorted by name`() = runBlocking {
        val result = loader("Zeal\nAnchor\n").loadAbilityListingData()

        assertEquals(listOf("Anchor", "Zeal"), result.map { it.name })
    }

    @Test
    fun `unknown ability type token drops the whole load to empty`() = runBlocking {
        val result = loader("Weird,Not a type,desc\n").loadAbilityListingData()

        assertTrue(result.isEmpty())
    }
}
