package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnvelopeCodecTest {

    @Test
    fun `empty envelope round trips`() {
        val original = Envelope(version = EnvelopeCodec.CurrentVersion)
        val encoded = EnvelopeCodec.encode(original)
        val decoded = EnvelopeCodec.decode(encoded.text)
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope with one manifestation round trips`() {
        val original = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = listOf(
                Manifestation(
                    name = "Wind",
                    rankIndex = 1,
                    rarityIndex = 0,
                    description = "A flowing breath of air.",
                ),
            ),
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original).text)
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope with confluence using tuple-encoded combinations round trips`() {
        val original = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            confluences = listOf(
                Confluence(
                    name = "Doom",
                    combinations = listOf(
                        ConfluenceSet("Sin", "Blood", "Dark", restrictedFlag = 0),
                        ConfluenceSet("Sin", "Blood", "Karma", restrictedFlag = 1),
                    ),
                ),
            ),
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original).text)
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope with listing and effects with cost tuples round trips`() {
        val original = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            listings = listOf(
                Listing(
                    name = "Frost Bolt",
                    effects = listOf(
                        Effect(
                            rankIndex = 1,
                            typeIndex = 3,
                            propertyIndices = listOf(19, 27),
                            costs = listOf(
                                Cost(kind = Cost.KIND_UPFRONT, amountIndex = 2, resourceIndex = 0),
                            ),
                            description = "Hurls a bolt of frost at the target.",
                            cooldown = "5s",
                        ),
                    ),
                ),
            ),
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original).text)
        assertEquals(original, decoded)
    }

    @Test
    fun `envelope with all four domains round trips`() {
        val original = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            manifestations = listOf(Manifestation("Wind", 1, 0)),
            confluences = listOf(Confluence("Doom", listOf(ConfluenceSet("A", "B", "C", 0)))),
            stones = listOf(Stone("Volcano", rarityIndex = 3)),
            listings = listOf(
                Listing(
                    "Frost",
                    listOf(Effect(rankIndex = 1, typeIndex = 3, description = "...")),
                ),
            ),
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(original).text)
        assertEquals(original, decoded)
    }

    @Test
    fun `omit-on-default keeps the wire form compact`() {
        val tiny = Envelope(
            version = EnvelopeCodec.CurrentVersion,
            stones = listOf(Stone("Quake", rarityIndex = 2)),
        )
        val encoded = EnvelopeCodec.encode(tiny)
        // Empty domain lists, default booleans, etc. should be omitted.
        // We don't pin a specific size, but the base64 string should be
        // small (well under a few hundred chars) for a one-stone payload.
        assertTrue(
            "encoded form unexpectedly long: ${encoded.byteSize} chars",
            encoded.byteSize < 200,
        )
        // And the round-trip still recovers the original.
        assertEquals(tiny, EnvelopeCodec.decode(encoded.text))
    }

    @Test
    fun `decode rejects future version`() {
        val futureVersion = EnvelopeCodec.CurrentVersion + 1
        val rawJson = """{"v":$futureVersion,"e":[]}"""
        val gzipBase64 = encodeJsonAsWireBase64(rawJson)
        try {
            EnvelopeCodec.decode(gzipBase64)
            fail("Expected WireVersionUnsupported for future version")
        } catch (e: WireVersionUnsupported) {
            assertEquals(futureVersion, e.incoming)
            assertEquals(EnvelopeCodec.CurrentVersion, e.current)
        }
    }

    @Test
    fun `decode rejects garbage as WireDecodeException`() {
        val garbage = "this-is-not-base64-encoded-anything-meaningful!!!@@@"
        try {
            EnvelopeCodec.decode(garbage)
            fail("Expected WireDecodeException")
        } catch (e: WireDecodeException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `decode rejects valid base64 of non-gzip`() {
        val notGzip = java.util.Base64.getEncoder().encodeToString("hello world".toByteArray())
        try {
            EnvelopeCodec.decode(notGzip)
            fail("Expected WireDecodeException")
        } catch (e: WireDecodeException) {
            assertTrue(e.message!!.contains("Gzip"))
        }
    }

    @Test
    fun `share size limit is exposed and respected by encoder`() {
        // Build an envelope that is plenty small, confirm the result fits.
        val small = Envelope(version = EnvelopeCodec.CurrentVersion)
        val r = EnvelopeCodec.encode(small)
        assertTrue("small envelope must fit in share limit", r.fitsInShareLimit)
        assertTrue("limit constant is 100KB", EnvelopeCodec.ShareSizeLimitBytes == 100 * 1024)
    }

    @Test
    fun `assertion-only test to silence unused warning`() {
        // Demonstrates that decoding a valid empty envelope yields an
        // Envelope with default-empty domain lists.
        val raw = EnvelopeCodec.encode(Envelope(version = EnvelopeCodec.CurrentVersion))
        val decoded = EnvelopeCodec.decode(raw.text)
        assertEquals(emptyList<Manifestation>(), decoded.manifestations)
        assertEquals(emptyList<Confluence>(), decoded.confluences)
        assertEquals(emptyList<Stone>(), decoded.stones)
        assertEquals(emptyList<Listing>(), decoded.listings)
        assertNull(null)
    }
}

private fun encodeJsonAsWireBase64(jsonText: String): String {
    val gzipped = java.io.ByteArrayOutputStream().apply {
        java.util.zip.GZIPOutputStream(this).use { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
    }.toByteArray()
    return java.util.Base64.getEncoder().encodeToString(gzipped)
}
