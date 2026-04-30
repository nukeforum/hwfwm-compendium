package wizardry.compendium.essences.dataloader

import wizardry.compendium.essences.model.Ability

interface AbilityListingDataLoader {
    suspend fun loadAbilityListingData(): List<Ability.Listing>
}
