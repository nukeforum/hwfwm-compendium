package com.mobile.wizardry.compendium

import com.mobile.wizardry.compendium.data.Essence

interface EssenceProvider {
    suspend fun getEssences(): List<Essence>
}
