package wizardry.compendium.awakeningstone.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneContributionsViewModel @Inject constructor(
    private val awakeningStoneRepository: AwakeningStoneRepository,
) : ViewModel() {

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    fun saveAwakeningStone(name: String, rarity: Rarity) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val stone = AwakeningStone.of(name = name.trim(), rarity = rarity)
            when (val result = awakeningStoneRepository.saveAwakeningStoneContribution(stone)) {
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
