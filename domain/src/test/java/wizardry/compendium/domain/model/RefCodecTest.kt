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
    fun `essence ref contributed round trip`() {
        val ref = EssenceRef.Contributed(7L)
        assertEquals(ref, RefCodec.decodeEssenceRef(RefCodec.encodeEssenceRef(ref)))
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
}
