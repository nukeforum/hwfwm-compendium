package wizardry.compendium.characterbuilddetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.ability.preview.AbilityTextRenderer
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.ui.coroutines.IoDispatcher
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.WireExporter
import javax.inject.Inject

@HiltViewModel
class CharacterBuildDetailViewModel @Inject constructor(
    private val repository: CharacterBuildRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val wireExporter: WireExporter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

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

    /**
     * Renders the current Success state's build as plain text suitable for
     * an ACTION_SEND share intent. Returns empty string if the VM is not in
     * Success state (the UI should hide the button in that case, but this
     * keeps the contract safe).
     */
    fun shareText(): String {
        val success = state.value as? CharacterBuildDetailUiState.Success ?: return ""
        return AbilityTextRenderer.renderBuild(success.build, success.statusEffects)
    }

    /**
     * Encodes the current Success state's build into the wire envelope and
     * returns the base64 text. Used by the Export-to-file button. Returns
     * empty string if the VM is not in Success state.
     */
    fun encodeFile(): String {
        val success = state.value as? CharacterBuildDetailUiState.Success ?: return ""
        return EnvelopeCodec.encode(wireExporter.exportSingle(success.build)).text
    }
}
