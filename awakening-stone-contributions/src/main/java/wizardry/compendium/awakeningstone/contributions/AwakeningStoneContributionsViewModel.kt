package wizardry.compendium.awakeningstone.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.AwakeningStoneProvider
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.persistence.AwakeningStoneCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneContributionsViewModel @Inject constructor(
    private val cache: AwakeningStoneCache,
    private val provider: AwakeningStoneProvider,
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
            val trimmedName = name.trim()
            val existing = provider.getAwakeningStones()
            if (existing.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                _saveState.emit(SaveState.Error("An awakening stone named \"$trimmedName\" already exists"))
                return@launch
            }
            val newStone = AwakeningStone.of(name = trimmedName, rarity = rarity)
            cache.contents = existing + newStone
            _saveState.emit(SaveState.Success)
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
