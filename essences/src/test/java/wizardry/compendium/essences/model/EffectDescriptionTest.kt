package wizardry.compendium.essences.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EffectDescriptionTest {

    private val bleeding = StatusEffect(
        name = "Bleeding",
        type = StatusType.Affliction.Wound,
        properties = listOf(Property.Blood),
        stackable = true,
        description = "Loses health over time.",
    )

    private val penance = StatusEffect(
        name = "Penance",
        type = StatusType.Affliction.Holy,
        properties = listOf(Property.Holy),
        stackable = false,
        description = "Inflicts pain when afflictions are cleansed.",
    )

    @Test
    fun `cost and cooldown tokens still parse without status list`() {
        val segments = parseDescription(
            template = "Costs {cost} on a {cooldown} cooldown.",
            costs = listOf(Cost.Upfront(Amount.Low, Resource.Mana)),
            cooldown = "30s",
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Costs "),
                DescriptionSegment.Resolved("{cost}", Cost.Upfront(Amount.Low, Resource.Mana).toString()),
                DescriptionSegment.Literal(" on a "),
                DescriptionSegment.Resolved("{cooldown}", "30s"),
                DescriptionSegment.Literal(" cooldown."),
            ),
            segments,
        )
    }

    @Test
    fun `status token resolves when status effect is in the supplied list`() {
        val segments = parseDescription(
            template = "Inflicts {status:Bleeding}.",
            costs = emptyList(),
            cooldown = "",
            statusEffects = listOf(bleeding),
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Inflicts "),
                DescriptionSegment.StatusLink(token = "{status:Bleeding}", name = "Bleeding", target = bleeding),
                DescriptionSegment.Literal("."),
            ),
            segments,
        )
    }

    @Test
    fun `status token is unresolved when name is not in the list`() {
        val segments = parseDescription(
            template = "Inflicts {status:Unknown}.",
            costs = emptyList(),
            cooldown = "",
            statusEffects = listOf(bleeding),
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Inflicts "),
                DescriptionSegment.Unresolved("{status:Unknown}"),
                DescriptionSegment.Literal("."),
            ),
            segments,
        )
    }

    @Test
    fun `status token is unresolved with empty status list`() {
        val segments = parseDescription(
            template = "Inflicts {status:Bleeding}.",
            costs = emptyList(),
            cooldown = "",
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Inflicts "),
                DescriptionSegment.Unresolved("{status:Bleeding}"),
                DescriptionSegment.Literal("."),
            ),
            segments,
        )
    }

    @Test
    fun `status name with whitespace and punctuation round-trips exactly`() {
        val curse = StatusEffect(
            name = "Foo Bar's Curse",
            type = StatusType.Affliction.Curse,
            properties = listOf(Property.Magic),
            stackable = false,
            description = "x",
        )
        val segments = parseDescription(
            template = "Applies {status:Foo Bar's Curse}!",
            costs = emptyList(),
            cooldown = "",
            statusEffects = listOf(curse),
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Applies "),
                DescriptionSegment.StatusLink("{status:Foo Bar's Curse}", "Foo Bar's Curse", curse),
                DescriptionSegment.Literal("!"),
            ),
            segments,
        )
    }

    @Test
    fun `whitespace-only status name is unresolved`() {
        val segments = parseDescription(
            template = "Bad: {status:   } end.",
            costs = emptyList(),
            cooldown = "",
            statusEffects = listOf(bleeding),
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Bad: "),
                DescriptionSegment.Unresolved("{status:   }"),
                DescriptionSegment.Literal(" end."),
            ),
            segments,
        )
    }

    @Test
    fun `mixed cost cooldown and status tokens preserve order and literals`() {
        val segments = parseDescription(
            template = "Costs {cost}, cooldown {cooldown}, inflicts {status:Penance}.",
            costs = listOf(Cost.Upfront(Amount.Low, Resource.Mana)),
            cooldown = "30s",
            statusEffects = listOf(penance),
        )
        assertEquals(
            listOf(
                DescriptionSegment.Literal("Costs "),
                DescriptionSegment.Resolved("{cost}", Cost.Upfront(Amount.Low, Resource.Mana).toString()),
                DescriptionSegment.Literal(", cooldown "),
                DescriptionSegment.Resolved("{cooldown}", "30s"),
                DescriptionSegment.Literal(", inflicts "),
                DescriptionSegment.StatusLink("{status:Penance}", "Penance", penance),
                DescriptionSegment.Literal("."),
            ),
            segments,
        )
    }
}
