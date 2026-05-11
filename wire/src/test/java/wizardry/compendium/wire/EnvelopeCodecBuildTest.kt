package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Test

class EnvelopeCodecBuildTest {

    @Test
    fun `build envelope round trips through codec`() {
        val build = Build(
            name = "Tank",
            race = "Human",
            racials = listOf("Tough Skin"),
            attributes = listOf(
                BuildAttribute(
                    essenceName = "Stone",
                    abilities = listOf(BuildAbility("Wall", 1, 0, 0.0f)),
                ),
                BuildAttribute(),
                BuildAttribute(),
                BuildAttribute(),
            ),
        )
        val envelope = Envelope(version = EnvelopeCodec.CurrentVersion, builds = listOf(build))
        val encoded = EnvelopeCodec.encode(envelope).text
        val decoded = EnvelopeCodec.decode(encoded)
        assertEquals(envelope, decoded)
    }
}
