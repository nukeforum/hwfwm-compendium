package com.mobile.wizardry.compendium.essences.model

sealed interface Amount {
    val ongoing: Boolean
    object None : Amount {
        override val ongoing: Boolean = false
    }
    class Low(override val ongoing: Boolean) : Amount {
        override fun toString(): String = "low"
    }
    class Moderate(override val ongoing: Boolean) : Amount {
        override fun toString(): String = "moderate"
    }
    class High(override val ongoing: Boolean) : Amount {
        override fun toString(): String = "high"
    }
    class Extrem(override val ongoing: Boolean) : Amount {
        override fun toString(): String = "extreme"
    }
}
