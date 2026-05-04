package wizardry.compendium.essences.dataloader

import wizardry.compendium.essences.model.StatusEffect

interface StatusEffectDataLoader {
    suspend fun loadStatusEffectData(): List<StatusEffect>
}
