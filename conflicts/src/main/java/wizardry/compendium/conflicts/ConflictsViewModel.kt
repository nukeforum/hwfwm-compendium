package wizardry.compendium.conflicts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wizardry.compendium.repositories.AbilityListingConflict
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.AwakeningStoneConflict
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.EssenceConflict
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.repositories.StatusEffectConflict
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

data class ConflictsState(
    val essence: List<EssenceConflict> = emptyList(),
    val awakeningStone: List<AwakeningStoneConflict> = emptyList(),
    val abilityListing: List<AbilityListingConflict> = emptyList(),
    val statusEffect: List<StatusEffectConflict> = emptyList(),
) {
    val total: Int = essence.size + awakeningStone.size + abilityListing.size + statusEffect.size
}

@HiltViewModel
class ConflictsViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<ConflictsState> = combine(
        essenceRepository.conflicts,
        awakeningStoneRepository.conflicts,
        abilityListingRepository.conflicts,
        statusEffectRepository.conflicts,
    ) { e, a, ab, s ->
        ConflictsState(essence = e, awakeningStone = a, abilityListing = ab, statusEffect = s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConflictsState())

    fun deleteEssenceContribution(name: String) {
        viewModelScope.launch(ioDispatcher) {
            essenceRepository.deleteContribution(name)
        }
    }

    fun deleteAwakeningStoneContribution(name: String) {
        viewModelScope.launch(ioDispatcher) {
            awakeningStoneRepository.deleteContribution(name)
        }
    }

    fun deleteAbilityListingContribution(name: String) {
        viewModelScope.launch(ioDispatcher) {
            abilityListingRepository.deleteContribution(name)
        }
    }

    fun deleteStatusEffectContribution(name: String) {
        viewModelScope.launch(ioDispatcher) {
            statusEffectRepository.deleteContribution(name)
        }
    }

    fun removeCombinationFromContribution(
        contribution: Essence.Confluence,
        combination: ConfluenceSet,
    ) {
        viewModelScope.launch(ioDispatcher) {
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
