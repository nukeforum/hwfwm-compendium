package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface StatusEffectContributionsToggleFlow {
    val statusEffectContributionsEnabled: Flow<Boolean>
}
