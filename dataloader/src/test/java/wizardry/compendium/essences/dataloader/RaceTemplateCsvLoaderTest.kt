package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class RaceTemplateCsvLoaderTest {

    private fun loader(csv: String) = RaceTemplateCsvLoader(
        source = object : FileStreamSource {
            override fun getInputStreamFor(filename: String): InputStream {
                assertEquals("races.csv", filename)
                return ByteArrayInputStream(csv.toByteArray())
            }
        },
    )

    @Test
    fun `empty file loads as empty list`() = runBlocking {
        assertTrue(loader("").loadRaceTemplateData().isEmpty())
    }

    @Test
    fun `row loads a race with its six racial ability names in column order`() = runBlocking {
        val result = loader(
            "Smoulder,Earth Affinity,Fire Affinity,Flame Investiture,Heart of the Earth,Life Fire,Earth Born\n",
        ).loadRaceTemplateData()

        val race = result.single()
        assertEquals("Smoulder", race.name)
        assertEquals(
            listOf(
                "Earth Affinity", "Fire Affinity", "Flame Investiture",
                "Heart of the Earth", "Life Fire", "Earth Born",
            ),
            race.racialAbilities.map { it.name },
        )
        assertTrue(race.racialAbilities.all { it.effects.isEmpty() })
    }

    @Test
    fun `races are sorted by name`() = runBlocking {
        val result = loader(
            "Runic,a,b,c,d,e,f\nElf,a,b,c,d,e,f\n",
        ).loadRaceTemplateData()

        assertEquals(listOf("Elf", "Runic"), result.map { it.name })
    }

    @Test
    fun `a row without exactly seven columns drops the whole load to empty`() = runBlocking {
        val result = loader("Outworlder,Astral Affinity\n").loadRaceTemplateData()

        assertTrue(result.isEmpty())
    }
}
