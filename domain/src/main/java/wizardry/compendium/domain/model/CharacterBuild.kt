package wizardry.compendium.domain.model

import kotlin.collections.filter

public data class CharacterBuild(
    public val name: String,
    public val race: String,
    public val racialAbilities: List<Ability.Listing>,
    public val attributes: Set<Attribute> = setOf(
        Attribute.Power(),
        Attribute.Speed(),
        Attribute.Spirit(),
        Attribute.Recovery(),
    ),
) {
    init {
        assert(racialAbilities.all { it.effects.all { it.type == AbilityType.RacialAbility } })
        assert(racialAbilities.count() <= 6)
    }

    public val rank: Rank get() = attributes.minOf { it.rank }
    public val progression: Double get() {
        val essences = attributes.mapNotNull { it.essence }.takeIf { it.isNotEmpty() }
            ?: return 0.0

        if (rank == Rank.Unranked) {
            return essences.count() / 4.0
        } else {
            val abilities = essences.flatMap { it.abilities }.takeIf { it.count() == 20 }
                ?: return 0.0

            return abilities.count { it.rank == Rank.next(rank) } / 20.0
        }
    }

    val Power get() = attributes.first { it is Attribute.Power }
    val Speed get() = attributes.first { it is Attribute.Speed }
    val Spirit get() = attributes.first { it is Attribute.Spirit }
    val Recovery get() = attributes.first { it is Attribute.Recovery }
}

public sealed interface Attribute {
    val essence: AbsorbedEssence?
    val rank: Rank get() {
        val theEssence = essence ?: return Rank.Unranked

        val abilities = theEssence.abilities.takeIf { it.isNotEmpty() } ?: return Rank.Iron

        return abilities.minOf { it.rank }
    }

    public data class Power(
        override val essence: AbsorbedEssence? = null,
        override val rank: Rank = Rank.Unranked,
    ) : Attribute

    public data class Speed(
        override val essence: AbsorbedEssence? = null,
        override val rank: Rank = Rank.Unranked,
    ) : Attribute

    public data class Spirit(
        override val essence: AbsorbedEssence? = null,
        override val rank: Rank = Rank.Unranked,
    ) : Attribute

    public data class Recovery(
        override val essence: AbsorbedEssence? = null,
        override val rank: Rank = Rank.Unranked,
    ) : Attribute
}

public data class AbsorbedEssence(
    val essence: Essence,
    val abilities: List<Ability.Acquired> = emptyList()
) {
    val hasOpenSlots get() = abilities.count() < 5
}
