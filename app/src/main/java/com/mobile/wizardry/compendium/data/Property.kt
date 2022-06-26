package com.mobile.wizardry.compendium.data

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
