package wizardry.compendium.essences.dataloader

import wizardry.compendium.domain.model.Ability

interface AbilityListingDataLoader {
    suspend fun loadAbilityListingData(): List<Ability.Listing>
}
