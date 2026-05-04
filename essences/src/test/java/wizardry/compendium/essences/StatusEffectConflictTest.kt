package wizardry.compendium.essences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

class StatusEffectConflictTest {

    private fun effect(name: String) = StatusEffect(
        name = name,
        type = StatusType.Affliction.Curse,
        properties = emptyList(),
        stackable = false,
        description = "",
    )

    @Test
    fun `no contributions yields no conflicts`() {
        val result = detectStatusEffectConflicts(canonical = listOf(effect("Burn")), contributions = emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-overlapping names yield no conflicts`() {
        val result = detectStatusEffectConflicts(
            canonical = listOf(effect("Burn")),
            contributions = listOf(effect("Chill")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `name collision is detected case-insensitively`() {
        val canonical = effect("Burn")
        val contribution = effect("burn")
        val result = detectStatusEffectConflicts(
            canonical = listOf(canonical),
            contributions = listOf(contribution),
        )
        assertEquals(1, result.size)
        val collision = result.single() as StatusEffectConflict.NameCollision
        assertEquals(contribution, collision.contribution)
        assertEquals(canonical, collision.canonical)
    }
}
