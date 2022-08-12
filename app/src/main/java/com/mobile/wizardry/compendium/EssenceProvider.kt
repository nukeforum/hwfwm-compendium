package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.essences.model.Essence

interface EssenceProvider {
    suspend fun getEssences(): List<Essence>
}
