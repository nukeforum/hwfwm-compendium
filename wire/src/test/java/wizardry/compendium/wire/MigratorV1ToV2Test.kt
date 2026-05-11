package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigratorV1ToV2Test {

    @Test
    fun `v1 envelope with only stones decodes through migrator chain into v2 with empty builds`() {
        // Hand-craft a v1 envelope JSON (the v1 codec emitted "v":1; v2 wire shape
        // is a superset, so the v1 form omits `b`). We can't use EnvelopeCodec.encode
        // (it now hard-requires v=2), so we run the gzip+base64 transport manually.
        val v1Json = """{"v":1,"s":[{"n":"Old Stone","r":2}]}"""
        val gzip = java.io.ByteArrayOutputStream().apply {
            java.util.zip.GZIPOutputStream(this).use { it.write(v1Json.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val base64 = java.util.Base64.getEncoder().encodeToString(gzip)

        val decoded = EnvelopeCodec.decode(base64)
        assertEquals(EnvelopeCodec.CurrentVersion, decoded.version)
        assertEquals(1, decoded.stones.size)
        assertEquals("Old Stone", decoded.stones.single().name)
        assertTrue(decoded.builds.isEmpty())
    }
}
