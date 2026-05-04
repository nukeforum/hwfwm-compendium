package wizardry.compendium.statuseffect.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import javax.inject.Inject

@HiltViewModel
class StatusEffectContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StatusEffectRepository,
) : ViewModel() {

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    init {
        if (editName != null) {
            viewModelScope.launch {
                val effect = repository.getStatusEffects().find { it.name == editName }
                if (effect != null && repository.isContribution(effect.name)) {
                    _mode.emit(Mode.Edit.Ready(effect))
                } else {
                    _mode.emit(Mode.Edit.NotFound)
                }
            }
        }
    }

    fun save(
        name: String,
        type: StatusType,
        properties: List<Property>,
        stackable: Boolean,
        description: String,
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            val effect = StatusEffect(
                name = name.trim(),
                type = type,
                properties = properties,
                stackable = stackable,
                description = description,
            )
            val result = if (editName != null) {
                repository.updateStatusEffectContribution(effect)
            } else {
                repository.saveStatusEffectContribution(effect)
            }
            when (result) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun deleteContribution() {
        val target = (mode.value as? Mode.Edit.Ready)?.effect ?: return
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            when (val result = repository.deleteContribution(target.name)) {
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
            data class Ready(val effect: StatusEffect) : Edit
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
