package wizardry.compendium.characterbuilddetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.CharacterBuild
import javax.inject.Inject

@HiltViewModel
class CharacterBuildDetailViewModel @Inject constructor(
    private val repository: CharacterBuildRepository,
    private val statusEffectRepository: StatusEffectRepository,
) : ViewModel() {

    /**
     * Background dispatcher for repository work. Visible for testing so unit
     * tests can substitute the runTest dispatcher; defaults to [Dispatchers.IO]
     * in production. Constructor stays Hilt-friendly by exposing this only as
     * a settable property.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow<CharacterBuildDetailUiState>(CharacterBuildDetailUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            repository.builds.drop(1).collect { builds ->
                val current = currentBuild ?: return@collect
                val refreshed = builds.find { it.name == current.name } ?: return@collect
                _state.emit(refreshed.toSuccess())
            }
        }
        viewModelScope.launch(ioDispatcher) {
            statusEffectRepository.statusEffects.drop(1).collect { effects ->
                val currentSuccess = state.value as? CharacterBuildDetailUiState.Success ?: return@collect
                _state.emit(currentSuccess.copy(statusEffects = effects))
            }
        }
    }

    fun load(name: String) {
        viewModelScope.launch(ioDispatcher) {
            _state.emit(CharacterBuildDetailUiState.Loading)
            val match = repository.getBuild(name)
            if (match == null) {
                _state.emit(CharacterBuildDetailUiState.Error(IllegalArgumentException("no build named $name")))
            } else {
                _state.emit(match.toSuccess())
            }
        }
    }

    private suspend fun CharacterBuild.toSuccess(): CharacterBuildDetailUiState.Success =
        CharacterBuildDetailUiState.Success(
            build = this,
            statusEffects = statusEffectRepository.getStatusEffects(),
        )

    private val currentBuild: CharacterBuild?
        get() = (state.value as? CharacterBuildDetailUiState.Success)?.build
}
