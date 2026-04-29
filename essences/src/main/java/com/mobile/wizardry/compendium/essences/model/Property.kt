package com.mobile.wizardry.compendium.essences.model

sealed interface Property {
    object Affliction : Property {
        override fun toString(): String = "affliction"
    }

    object Blood : Property {
        override fun toString(): String = "blood"
    }

    object Boon : Property {
        override fun toString(): String = "boon"
    }

    object Channel : Property {
        override fun toString(): String = "channel"
    }

    object Cleanse : Property {
        override fun toString(): String = "cleanse"
    }

    object Combination : Property {
        override fun toString(): String = "combination"
    }

    object Conjuration : Property {
        override fun toString(): String = "conjuration"
    }

    object Consumable : Property {
        override fun toString(): String = "consumable"
    }

    object CounterExecute : Property {
        override fun toString(): String = "counter-execute"
    }

    object Curse : Property {
        override fun toString(): String = "curse"
    }

    object DamageOverTime : Property {
        override fun toString(): String = "damage-over-time"
    }

    object Dark : Property {
        override fun toString(): String = "dark"
    }

    object Darkness : Property {
        override fun toString(): String = "darkness"
    }

    object Dimension : Property {
        override fun toString(): String = "dimension"
    }

    object Disease : Property {
        override fun toString(): String = "disease"
    }

    object Drain : Property {
        override fun toString(): String = "drain"
    }

    object Elemental : Property {
        override fun toString(): String = "elemental"
    }

    object Essence : Property {
        override fun toString(): String = "essence"
    }

    object Execute : Property {
        override fun toString(): String = "execute"
    }

    object Fire : Property {
        override fun toString(): String = "fire"
    }

    object HealOverTime : Property {
        override fun toString(): String = "heal-over-time"
    }

    object Healing : Property {
        override fun toString(): String = "healing"
    }

    object Holy : Property {
        override fun toString(): String = "holy"
    }

    object Ice : Property {
        override fun toString(): String = "ice"
    }

    object Illusion : Property {
        override fun toString(): String = "illusion"
    }

    object Light : Property {
        override fun toString(): String = "light"
    }

    object Lightning : Property {
        override fun toString(): String = "lightning"
    }

    object Magic : Property {
        override fun toString(): String = "magic"
    }

    object ManaOverTime : Property {
        override fun toString(): String = "mana-over-time"
    }

    object Melee : Property {
        override fun toString(): String = "melee"
    }

    object Momentum : Property {
        override fun toString(): String = "momentum"
    }

    object Movement : Property {
        override fun toString(): String = "movement"
    }

    object Nature : Property {
        override fun toString(): String = "nature"
    }

    object Perception : Property {
        override fun toString(): String = "perception"
    }

    object Poison : Property {
        override fun toString(): String = "poison"
    }

    object Recovery : Property {
        override fun toString(): String = "recovery"
    }

    object Restoration : Property {
        override fun toString(): String = "restoration"
    }

    object Retributive : Property {
        override fun toString(): String = "retributive"
    }

    object Ritual : Property {
        override fun toString(): String = "ritual"
    }

    object Sacrifice : Property {
        override fun toString(): String = "sacrifice"
    }

    object ShapeChange : Property {
        override fun toString(): String = "shape-change"
    }

    object Signal : Property {
        override fun toString(): String = "signal"
    }

    object Stacking : Property {
        override fun toString(): String = "stacking"
    }

    object StaminaOverTime : Property {
        override fun toString(): String = "stamina-over-time"
    }

    object Summon : Property {
        override fun toString(): String = "summon"
    }

    object Teleport : Property {
        override fun toString(): String = "teleport"
    }

    object Tracking : Property {
        override fun toString(): String = "tracking"
    }

    object Trap : Property {
        override fun toString(): String = "trap"
    }

    object Unholy : Property {
        override fun toString(): String = "unholy"
    }

    object Vehicle : Property {
        override fun toString(): String = "vehicle"
    }

    object Wounding : Property {
        override fun toString(): String = "wounding"
    }

    object Zone : Property {
        override fun toString(): String = "zone"
    }
}
