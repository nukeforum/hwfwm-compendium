package wizardry.compendium.wire

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeBuildShapeTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `envelope serializes builds under key b`() {
        val build = Build(
            name = "Tank",
            race = "Human",
            attributes = List(4) { BuildAttribute() },
        )
        val envelope = Envelope(version = 2, builds = listOf(build))
        val encoded = json.encodeToString(Envelope.serializer(), envelope)
        // The serialized form should contain the "b" key and a single Build entry
        // with default-empty attribute objects for each of the 4 slots.
        assertEquals("""{"v":2,"b":[{"n":"Tank","r":"Human","a":[{},{},{},{}]}]}""", encoded)
    }

    @Test
    fun `CurrentVersion is 2`() {
        assertEquals(2, EnvelopeCodec.CurrentVersion)
    }
}
