package com.mobile.wizardry.compendium.essences.model

sealed interface Resource {
    object Mana : Resource {
        override fun toString(): String = "mana"
    }
    object Stamina : Resource {
        override fun toString(): String = "stamina"
    }
    object Health : Resource {
        override fun toString(): String = "health"
    }
}