package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface StatusEffectContributionsToggleFlow {
    val statusEffectContributionsEnabled: Flow<Boolean>
}
