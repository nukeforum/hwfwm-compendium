package wizardry.compendium.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RefCodecTest {

    @Test
    fun `ability ref canonical round trip`() {
        val ref = AbilityRef.Canonical("Flame Bolt")
        assertEquals(ref, RefCodec.decodeAbilityRef(RefCodec.encodeAbilityRef(ref)))
    }

    @Test
    fun `ability ref contributed round trip`() {
        val ref = AbilityRef.Contributed(42L)
        assertEquals(ref, RefCodec.decodeAbilityRef(RefCodec.encodeAbilityRef(ref)))
    }

    @Test
    fun `essence ref canonical round trip`() {
        val ref = EssenceRef.Canonical("Fire")
        assertEquals(ref, RefCodec.decodeEssenceRef(RefCodec.encodeEssenceRef(ref)))
    }

    @Test
    fun `essence ref contributed manifestation round trip`() {
        val ref = EssenceRef.Contributed.Manifestation(7L)
        assertEquals(ref, RefCodec.decodeEssenceRef(RefCodec.encodeEssenceRef(ref)))
    }

    @Test
    fun `essence ref contributed confluence round trip`() {
        val ref = EssenceRef.Contributed.Confluence(7L)
        assertEquals(ref, RefCodec.decodeEssenceRef(RefCodec.encodeEssenceRef(ref)))
    }

    @Test
    fun `essence contributed manifestation encodes with mcontr prefix`() {
        assertEquals("mcontr:42", RefCodec.encodeEssenceRef(EssenceRef.Contributed.Manifestation(42L)))
    }

    @Test
    fun `essence contributed confluence encodes with ccontr prefix`() {
        assertEquals("ccontr:42", RefCodec.encodeEssenceRef(EssenceRef.Contributed.Confluence(42L)))
    }

    @Test
    fun `essence contributed prefixes are disjoint -- same id different kind decodes to different variants`() {
        val asManifestation = RefCodec.decodeEssenceRef("mcontr:1")
        val asConfluence = RefCodec.decodeEssenceRef("ccontr:1")
        assertEquals(EssenceRef.Contributed.Manifestation(1L), asManifestation)
        assertEquals(EssenceRef.Contributed.Confluence(1L), asConfluence)
    }

    @Test
    fun `essence decode rejects bare contr prefix at v7 -- legacy v6 form is migrated`() {
        // The bare "contr:<id>" form is the v6 wire format; the v6 -> v7
        // migration rewrites every such ref to mcontr: or ccontr:. After
        // migration, encountering a bare "contr:" string is a bug, not a
        // valid encoding.
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("contr:42")
        }
    }

    @Test
    fun `canonical encodes with canon prefix`() {
        assertEquals("canon:Flame Bolt", RefCodec.encodeAbilityRef(AbilityRef.Canonical("Flame Bolt")))
    }

    @Test
    fun `contributed encodes with contr prefix and decimal id`() {
        assertEquals("contr:42", RefCodec.encodeAbilityRef(AbilityRef.Contributed(42L)))
    }

    @Test
    fun `canonical name may contain colons in the suffix`() {
        val ref = AbilityRef.Canonical("Foo:Bar")
        assertEquals(ref, RefCodec.decodeAbilityRef("canon:Foo:Bar"))
    }

    @Test
    fun `malformed missing prefix throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("Flame Bolt")
        }
    }

    @Test
    fun `malformed unknown prefix throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("bogus:Flame Bolt")
        }
    }

    @Test
    fun `malformed contributed with non-numeric id throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("contr:abc")
        }
    }

    @Test
    fun `malformed empty string throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("")
        }
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("")
        }
    }

    @Test
    fun `malformed canon prefix with empty name throws MalformedRefException for ability ref`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("canon:")
        }
    }

    @Test
    fun `malformed canon prefix with empty name throws MalformedRefException for essence ref`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("canon:")
        }
    }

    @Test
    fun `malformed contr prefix with empty id throws MalformedRefException for ability ref`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeAbilityRef("contr:")
        }
    }

    @Test
    fun `malformed mcontr prefix with empty id throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("mcontr:")
        }
    }

    @Test
    fun `malformed ccontr prefix with empty id throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("ccontr:")
        }
    }

    @Test
    fun `malformed mcontr prefix with non-numeric id throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("mcontr:abc")
        }
    }

    @Test
    fun `malformed ccontr prefix with non-numeric id throws MalformedRefException`() {
        assertThrows(MalformedRefException::class.java) {
            RefCodec.decodeEssenceRef("ccontr:abc")
        }
    }
}
