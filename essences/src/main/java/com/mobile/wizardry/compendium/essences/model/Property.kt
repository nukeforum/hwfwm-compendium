package com.mobile.wizardry.compendium.essences.model

sealed interface Property {
    object Consumable : Property {
        override fun toString(): String = "consumable"
    }

    object Essence : Property {
        override fun toString(): String = "essence"
    }

    object Holy : Property {
        override fun toString(): String = "holy"
    }

    object Unholy : Property {
        override fun toString(): String = "unholy"
    }

    object Dimension : Property {
        override fun toString(): String = "dimension"
    }

    object Nature : Property {
        override fun toString(): String = "nature"
    }

    object Recovery : Property {
        override fun toString(): String = "recovery"
    }

    object Cleanse : Property {
        override fun toString(): String = "cleanse"
    }

    object Drain : Property {
        override fun toString(): String = "drain"
    }

    object Dark : Property {
        override fun toString(): String = "dark"
    }

    object Melee : Property {
        override fun toString(): String = "melee"
    }

    object Curse : Property {
        override fun toString(): String = "curse"
    }

    object Blood : Property {
        override fun toString(): String = "blood"
    }

    object Darkness : Property {
        override fun toString(): String = "darkness"
    }

    object Light : Property {
        override fun toString(): String = "light"
    }

    object Teleport : Property {
        override fun toString(): String = "teleport"
    }
}
