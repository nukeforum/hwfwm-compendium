package wizardry.compendium.essences.model

sealed interface Cost {
    val amount: Amount
    val resource: Resource

    data class Ongoing(
        override val amount: Amount,
        override val resource: Resource,
    ) : Cost {
        override fun toString(): String {
            return "Ongoing $amount $resource"
        }
    }

    data class Upfront(
        override val amount: Amount,
        override val resource: Resource,
    ) : Cost {
        override fun toString(): String {
            return "$amount $resource"
        }
    }

    data object None : Cost {
        override val amount: Amount = Amount.None
        override val resource: Resource = Resource.Mana
    }
}
