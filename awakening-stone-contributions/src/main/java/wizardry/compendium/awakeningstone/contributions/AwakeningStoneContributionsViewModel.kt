package wizardry.compendium.awakeningstone.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.awakeningstone.contributions.AwakeningStoneContributionsViewModel.Mode
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
    savedStateHandle: SavedStateHandle,
    private val awakeningStoneRepository: AwakeningStoneRepository,
) : ViewModel() {

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    init {
        if (editName != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val stone = awakeningStoneRepository.getAwakeningStones().find { it.name == editName }
                if (stone != null && awakeningStoneRepository.isContribution(stone.name)) {
                    _mode.emit(Mode.Edit.Ready(stone))
                } else {
                    _mode.emit(Mode.Edit.NotFound)
                }
            }
        }
    }

    fun saveAwakeningStone(name: String, rarity: Rarity) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val stone = AwakeningStone.of(name = name.trim(), rarity = rarity)
            val result = if (editName != null) {
                awakeningStoneRepository.updateAwakeningStoneContribution(stone)
            } else {
                awakeningStoneRepository.saveAwakeningStoneContribution(stone)
            }
            when (result) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun deleteContribution() {
        val target = (mode.value as? Mode.Edit.Ready)?.stone ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            when (val result = awakeningStoneRepository.deleteContribution(target.name)) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Deleted)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    sealed interface Mode {
        data object Create : Mode
        sealed interface Edit : Mode {
            data object Loading : Edit
            data object NotFound : Edit
            data class Ready(val stone: AwakeningStone) : Edit
        }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Deleted : SaveState
        data class Error(val message: String) : SaveState
    }
}
