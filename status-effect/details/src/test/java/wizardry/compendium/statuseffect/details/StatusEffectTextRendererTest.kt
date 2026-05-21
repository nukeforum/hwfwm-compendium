package wizardry.compendium.statuseffect.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType

class StatusEffectTextRendererTest {

    private val burn = StatusEffect(
        name = "Burn",
        type = StatusType.Affliction.Elemental,
        properties = listOf(Property.DamageOverTime, Property.Fire),
        stackable = true,
        description = "Deals fire damage over time.",
    )

    @Test
    fun `render includes name type description properties and stackable marker`() {
        val text = StatusEffectTextRenderer.renderAsText(burn)

        assertTrue("missing name in $text", text.contains("Burn"))
        assertTrue("missing type label in $text", text.contains("Affliction · Elemental"))
        assertTrue("missing description in $text", text.contains("Deals fire damage over time."))
        assertTrue("missing Properties: line in $text", text.contains("Properties: "))
        assertTrue("missing damage-over-time property in $text", text.contains("damage-over-time"))
        assertTrue("missing stackable marker in $text", text.contains("Stackable"))
    }

    @Test
    fun `untyped affliction renders as Untyped without backing-class name`() {
        val effect = burn.copy(type = StatusType.Affliction.UnTyped)

        val text = StatusEffectTextRenderer.renderAsText(effect)

        assertTrue("expected 'Affliction · Untyped' in $text", text.contains("Affliction · Untyped"))
        assertEquals(-1, text.indexOf("UnTyped"))
    }

    @Test
    fun `boon types render with Boon prefix`() {
        val haste = StatusEffect(
            name = "Haste",
            type = StatusType.Boon.Magic,
            properties = emptyList(),
            stackable = false,
            description = "Move faster.",
        )

        val text = StatusEffectTextRenderer.renderAsText(haste)

        assertTrue("expected 'Boon · Magic' in $text", text.contains("Boon · Magic"))
    }

    @Test
    fun `non-stackable effect omits Stackable line`() {
        val effect = burn.copy(stackable = false)

        val text = StatusEffectTextRenderer.renderAsText(effect)

        assertEquals(-1, text.indexOf("Stackable"))
    }
}
