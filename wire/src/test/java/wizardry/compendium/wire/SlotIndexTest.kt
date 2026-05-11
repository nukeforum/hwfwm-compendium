package wizardry.compendium.wire

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotIndexTest {

    @Test
    fun `slot order is Power Speed Spirit Recovery`() {
        assertEquals(listOf("Power", "Speed", "Spirit", "Recovery"), SlotIndex.orderedNames)
        assertEquals(4, SlotIndex.SLOT_COUNT)
    }
}
