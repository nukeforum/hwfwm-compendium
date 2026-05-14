package wizardry.compendium.randomizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class RandomizerViewModel @Inject constructor(
    essenceRepository: EssenceRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val manifestations = MutableStateFlow(emptyList<Essence.Manifestation>())
    private val confluences = MutableStateFlow(emptyList<Essence.Confluence>())

    private val _state = MutableStateFlow<RandomizerUiState>(RandomizerUiState.Loading)
    val state get() = _state.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            essenceRepository.essences.collect { essences ->
                manifestations.emit(essences.filterIsInstance<Essence.Manifestation>())
                confluences.emit(essences.filterIsInstance<Essence.Confluence>())
                RandomizerUiState.Success(emptySet(), null).emit()
            }
        }
    }

    fun randomize() {
        viewModelScope.launch(ioDispatcher) {
            _state.emit(RandomizerUiState.Loading)

            val currentManifestations = manifestations.value
            if (currentManifestations.isEmpty()) {
                _state.emit(
                    RandomizerUiState.Error(
                        IllegalStateException("No essences are available to randomize.")
                    )
                )
                return@launch
            }

            val set = mutableSetOf<Essence.Manifestation>()
            while (set.size < 3) {
                set.add(currentManifestations.random())
            }

            val confluence = confluences.value.find { it.confluenceSets.contains(ConfluenceSet(set)) }

            RandomizerUiState.Success(set, confluence).emit()
        }
    }

    private suspend fun RandomizerUiState.emit() {
        _state.emit(this)
    }
}
