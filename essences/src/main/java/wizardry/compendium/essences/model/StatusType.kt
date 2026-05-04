package wizardry.compendium.essences.model

sealed interface StatusType {
    sealed interface Affliction : StatusType {
        object Curse : Affliction
        object Disease : Affliction
        object Elemental : Affliction
        object Holy : Affliction
        object Magic : Affliction
        object Poison : Affliction
        object Unholy : Affliction
        object Wound : Affliction
        object UnTyped : Affliction
    }

    sealed interface Boon : StatusType {
        object Holy : Boon
        object Magic : Boon
        object Unholy : Boon
        object UnTyped : Boon
    }
}
