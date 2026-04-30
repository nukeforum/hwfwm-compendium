package wizardry.compendium.essences

import kotlinx.coroutines.flow.Flow

interface ContributionsToggleFlow {
    val contributionsEnabled: Flow<Boolean>
}
