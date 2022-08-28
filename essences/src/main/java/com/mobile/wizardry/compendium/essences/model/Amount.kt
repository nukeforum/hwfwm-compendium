package com.mobile.wizardry.compendium.essences.model

sealed interface Amount {
    object None : Amount {
        override fun toString(): String = "None"
    }

    object Low : Amount {
        override fun toString(): String = "low"
    }

    object Moderate : Amount {
        override fun toString(): String = "moderate"
    }

    object High : Amount {
        override fun toString(): String = "high"
    }

    object VeryHigh : Amount {
        override fun toString(): String = "very high"
    }

    object Extreme : Amount {
        override fun toString(): String = "extreme"
    }
}
