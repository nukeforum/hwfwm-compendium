package wizardry.compendium.wire

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuildAbilitySerializerTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun `encodes as 4-tuple`() {
        val ability = BuildAbility(name = "Frost Bolt", rankIndex = 2, tier = 3, progress = 0.5f)
        val encoded = json.encodeToString(BuildAbility.serializer(), ability)
        assertEquals("""["Frost Bolt",2,3,0.5]""", encoded)
    }

    @Test
    fun `decodes from 4-tuple`() {
        val decoded = json.decodeFromString(BuildAbility.serializer(), """["Frost Bolt",2,3,0.5]""")
        assertEquals(BuildAbility(name = "Frost Bolt", rankIndex = 2, tier = 3, progress = 0.5f), decoded)
    }

    @Test
    fun `rejects wrong tuple length`() {
        val err = assertThrows(IllegalArgumentException::class.java) {
            json.decodeFromString(BuildAbility.serializer(), """["Frost Bolt",2,3]""")
        }
        assertEquals("BuildAbility wire tuple must have 4 elements, got 3", err.message)
    }
}
