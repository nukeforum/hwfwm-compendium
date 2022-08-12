package com.mobile.wizardry.compendium.essences.model

sealed interface Property {
    object Consumable : Property {
        override fun toString(): String {
            return "consumable"
        }
    }

    object Essence : Property {
        override fun toString(): String {
            return "essence"
        }
    }
}
