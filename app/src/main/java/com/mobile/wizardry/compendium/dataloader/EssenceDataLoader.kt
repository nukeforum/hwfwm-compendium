package com.mobile.wizardry.compendium.dataloader

import com.mobile.wizardry.compendium.data.Essence

interface EssenceDataLoader {
    suspend fun loadEssenceData(): List<Essence>
}
