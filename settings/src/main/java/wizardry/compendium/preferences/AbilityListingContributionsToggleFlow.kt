package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface AbilityListingContributionsToggleFlow {
    val abilityListingContributionsEnabled: Flow<Boolean>
}
