package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface AbilityListingContributionsToggleFlow {
    val abilityListingContributionsEnabled: Flow<Boolean>
}
