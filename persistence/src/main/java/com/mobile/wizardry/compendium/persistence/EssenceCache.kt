package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.Essence

interface EssenceCache {
    var contents: List<Essence>

    companion object {
        private var reference: EssenceCache? = null
        fun get(): EssenceCache {
            return reference
                ?: object : EssenceCache {
                    override var contents: List<Essence> = emptyList()
                }
        }
    }
}
