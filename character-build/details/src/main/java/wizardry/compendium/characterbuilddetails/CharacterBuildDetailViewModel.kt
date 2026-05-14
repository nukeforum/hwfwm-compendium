package wizardry.compendium.characterbuilddetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.repositories.CharacterBuildRepository
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.share.CharacterBuildShareUseCase
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class CharacterBuildDetailViewModel @Inject constructor(
    private val repository: CharacterBuildRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val shareUseCase: CharacterBuildShareUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow<CharacterBuildDetailUiState>(CharacterBuildDetailUiState.Loading)
    val state = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

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

    fun requestShareAsText() {
        val success = state.value as? CharacterBuildDetailUiState.Success ?: return
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.EncodedAsText(shareUseCase.renderAsText(success.build, success.statusEffects)),
            )
        }
    }

    fun requestShareAsFile() {
        val success = state.value as? CharacterBuildDetailUiState.Success ?: return
        viewModelScope.launch {
            _shareEvents.emit(ShareEvent.Encoded(shareUseCase.encode(success.build)))
        }
    }

    sealed interface ShareEvent {
        data class Encoded(val text: String) : ShareEvent
        data class EncodedAsText(val text: String) : ShareEvent
    }
}
