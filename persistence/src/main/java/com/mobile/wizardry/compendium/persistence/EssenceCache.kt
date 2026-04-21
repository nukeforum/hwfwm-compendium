package com.mobile.wizardry.compendium.persistence

import com.mobile.wizardry.compendium.essences.model.Essence

interface EssenceCache {
    var contents: List<Essence>
}
