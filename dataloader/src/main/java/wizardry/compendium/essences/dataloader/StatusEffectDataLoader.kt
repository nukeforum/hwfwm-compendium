package wizardry.compendium.essences.dataloader

import wizardry.compendium.domain.model.StatusEffect

interface StatusEffectDataLoader {
    suspend fun loadStatusEffectData(): List<StatusEffect>
}
