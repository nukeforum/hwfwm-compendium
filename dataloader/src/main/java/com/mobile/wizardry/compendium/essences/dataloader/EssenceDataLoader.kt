package com.mobile.wizardry.compendium.essences.dataloader

import com.mobile.wizardry.compendium.essences.model.Essence

interface EssenceDataLoader {
    suspend fun loadEssenceData(): List<Essence>
}
