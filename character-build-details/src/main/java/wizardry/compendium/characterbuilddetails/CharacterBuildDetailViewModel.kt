package wizardry.compendium.characterbuilddetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.model.CharacterBuild
import javax.inject.Inject

@HiltViewModel
class CharacterBuildDetailViewModel @Inject constructor(
    private val repository: CharacterBuildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<CharacterBuildDetailUiState>(CharacterBuildDetailUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.builds.drop(1).collect { builds ->
                val current = currentBuild ?: return@collect
                val refreshed = builds.find { it.name == current.name } ?: return@collect
                _state.emit(CharacterBuildDetailUiState.Success(refreshed))
            }
        }
    }

    fun load(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(CharacterBuildDetailUiState.Loading)
            val match = repository.getBuild(name)
            if (match == null) {
                _state.emit(CharacterBuildDetailUiState.Error(IllegalArgumentException("no build named $name")))
            } else {
                _state.emit(CharacterBuildDetailUiState.Success(match))
            }
        }
    }

    private val currentBuild: CharacterBuild?
        get() = (state.value as? CharacterBuildDetailUiState.Success)?.build
}
