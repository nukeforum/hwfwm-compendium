package wizardry.compendium.share

import org.junit.Assert.assertEquals
import org.junit.Test

class DecodedSingleTest {
    @Test
    fun `Loaded carries a model of the expected type`() {
        val result: DecodedSingle<String> = DecodedSingle.Loaded("hello")
        assertEquals("hello", (result as DecodedSingle.Loaded<String>).model)
    }

    @Test
    fun `Failed carries a reason`() {
        val result: DecodedSingle<String> = DecodedSingle.Failed("nope")
        assertEquals("nope", (result as DecodedSingle.Failed).reason)
    }
}
