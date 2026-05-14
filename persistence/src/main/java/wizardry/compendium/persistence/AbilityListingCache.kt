package wizardry.compendium.persistence

import wizardry.compendium.domain.model.Ability

interface AbilityListingCache {
    var contents: List<Ability.Listing>
}
