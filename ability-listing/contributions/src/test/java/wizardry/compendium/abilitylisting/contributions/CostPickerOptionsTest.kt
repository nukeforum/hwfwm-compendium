package wizardry.compendium.abilitylisting.contributions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Amount

class CostPickerOptionsTest {

    @Test
    fun `AmountOptions excludes Amount None`() {
        assertFalse(
            "Amount.None must not appear in the cost picker; saving zero costs " +
                "is the user's path to no-cost effects.",
            Amount.None in AmountOptions,
        )
    }

    @Test
    fun `AmountOptions includes the meaningful amounts`() {
        assertTrue(Amount.VeryLow in AmountOptions)
        assertTrue(Amount.Low in AmountOptions)
        assertTrue(Amount.Moderate in AmountOptions)
        assertTrue(Amount.High in AmountOptions)
        assertTrue(Amount.VeryHigh in AmountOptions)
        assertTrue(Amount.Extreme in AmountOptions)
        assertTrue(Amount.BeyondExtreme in AmountOptions)
    }
}
