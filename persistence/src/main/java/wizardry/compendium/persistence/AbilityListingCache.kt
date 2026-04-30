package wizardry.compendium.persistence

import wizardry.compendium.essences.model.Ability

interface AbilityListingCache {
    var contents: List<Ability.Listing>
}
