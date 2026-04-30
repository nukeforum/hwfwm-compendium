package wizardry.compendium.abilitylisting.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

@HiltViewModel
class AbilityListingContributionsViewModel @Inject constructor(
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    fun saveAbilityListing(name: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val listing = Ability.Listing.of(name = name.trim())
            when (val result = abilityListingRepository.saveAbilityListingContribution(listing)) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data class Error(val message: String) : SaveState
    }
}
