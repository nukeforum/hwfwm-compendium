package com.mobile.wizardry.compendium.essences.model

sealed interface AbilityType {
    object SpecialAttack : AbilityType {
        override fun toString(): String = "Special attack"
    }

    object SpecialAbility : AbilityType {
        override fun toString(): String = "Special ability"
    }

    object RacialAbility : AbilityType {
        override fun toString(): String = "Racial ability"
    }

    object Spell : AbilityType {
        override fun toString(): String = "Spell"
    }

    object Aura : AbilityType {
        override fun toString(): String = "Aura"
    }

    object Conjuration : AbilityType {
        override fun toString(): String = "Conjuration"
    }

    object Familiar : AbilityType {
        override fun toString(): String = "Special attack"
    }
}
