package com.mobile.wizardry.compendium.essences.model

sealed interface AbilityType {
    val description: String

    object SpecialAttack : AbilityType {
        override val description: String
            get() = "Special attack"
    }

    object SpecialAbility : AbilityType {
        override val description: String
            get() = "Special ability"
    }

    object RacialAbility : AbilityType {
        override val description: String
            get() = "Racial ability"
    }

    object Spell : AbilityType {
        override val description: String
            get() = "Spell"
    }

    object Aura : AbilityType {
        override val description: String
            get() = "Aura"
    }

    object Conjuration : AbilityType {
        override val description: String
            get() = "Conjuration"
    }

    object Familiar : AbilityType {
        override val description: String
            get() = "Special attack"
    }
}
