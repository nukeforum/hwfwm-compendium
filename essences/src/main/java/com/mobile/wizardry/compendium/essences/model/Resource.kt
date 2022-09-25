package com.mobile.wizardry.compendium.essences.model

// TODO: Generify such that stacks of something can be consumed as a resource
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
