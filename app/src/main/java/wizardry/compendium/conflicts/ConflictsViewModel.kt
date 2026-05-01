package wizardry.compendium.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceConflict
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import javax.inject.Inject

data class ConflictsState(
    val essence: List<EssenceConflict> = emptyList(),
    val awakeningStone: List<AwakeningStoneConflict> = emptyList(),
    val abilityListing: List<AbilityListingConflict> = emptyList(),
) {
    val total: Int = essence.size + awakeningStone.size + abilityListing.size
}

@HiltViewModel
class ConflictsViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    val state: StateFlow<ConflictsState> = combine(
        essenceRepository.conflicts,
        awakeningStoneRepository.conflicts,
        abilityListingRepository.conflicts,
    ) { e, a, ab ->
        ConflictsState(essence = e, awakeningStone = a, abilityListing = ab)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConflictsState())

    fun deleteEssenceContribution(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            essenceRepository.deleteContribution(name)
        }
    }

    fun deleteAwakeningStoneContribution(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            awakeningStoneRepository.deleteContribution(name)
        }
    }

    fun deleteAbilityListingContribution(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            abilityListingRepository.deleteContribution(name)
        }
    }

    fun removeCombinationFromContribution(
        contribution: Essence.Confluence,
        combination: ConfluenceSet,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val remaining = contribution.confluenceSets - combination
            if (remaining.isEmpty()) {
                essenceRepository.deleteContribution(contribution.name)
            } else {
                essenceRepository.updateConfluenceContribution(
                    contribution.copy(confluenceSets = remaining),
                )
            }
        }
    }
}
