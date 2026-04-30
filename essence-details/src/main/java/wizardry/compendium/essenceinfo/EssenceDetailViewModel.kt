package wizardry.compendium.essenceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Essence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EssenceDetailViewModel
@Inject constructor(
    private val essenceRepository: EssenceRepository,
) : ViewModel() {
    private val history = ArrayDeque<Essence>()
    private val _state = MutableStateFlow<EssenceDetailUiState>(EssenceDetailUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Re-resolve the current essence by name when the data set changes.
            // drop(1) avoids double-loading on first composition since load() is called
            // by the composable via LaunchedEffect.
            essenceRepository.essences.drop(1).collect { essences ->
                val currentEssence = currentlyLoadedEssence ?: return@collect
                val refreshed = essences.find { it.name == currentEssence.name } ?: return@collect
                buildState(refreshed)
            }
        }
    }

    fun load(essenceName: String) {
        currentlyLoadedEssence?.let { history.addFirst(it) }
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(EssenceDetailUiState.Loading)

            essenceRepository.getEssences().find { it.name == essenceName }
                ?.let { essence -> buildState(essence) }
                ?: _state.emit(EssenceDetailUiState.Error(IllegalArgumentException("no essence found with name: $essenceName")))
        }
    }

    fun load(essence: Essence) {
        currentlyLoadedEssence?.let { history.addFirst(it) }
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(EssenceDetailUiState.Loading)

            buildState(essence)
        }
    }

    private suspend fun buildState(essence: Essence) {
        when (essence) {
            is Essence.Manifestation -> buildManifestationState(essence)
            is Essence.Confluence -> buildConfluenceState(essence)
        }
    }

    private suspend fun buildConfluenceState(essence: Essence.Confluence) {
        EssenceDetailUiState.Success.ConfluenceUiState(
            essence = essence,
            previousEssence = history.firstOrNull() as? Essence.Manifestation,
            isContribution = essenceRepository.isContribution(essence.name),
        ).emit()
    }

    private suspend fun buildManifestationState(essence: Essence.Manifestation) {
        val confluences = essenceRepository.getEssences()
            .filterIsInstance<Essence.Confluence>()
            .filter { it.isProducedBy(essence) }

        EssenceDetailUiState.Success.ManifestationUiState(
            essence = essence,
            previousEssence = history.firstOrNull() as? Essence.Confluence,
            isContribution = essenceRepository.isContribution(essence.name),
            knownConfluences = confluences,
        ).emit()
    }

    private fun Essence.Confluence.isProducedBy(selectedEssence: Essence): Boolean {
        return confluenceSets.any { confluenceSet -> confluenceSet.set.any { essence -> essence == selectedEssence } }
    }

    private suspend fun EssenceDetailUiState.emit() {
        _state.emit(this)
    }

    private val currentlyLoadedEssence: Essence?
        get() {
            return (state.value as? EssenceDetailUiState.Success)?.essence
        }

    fun goBack() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val essence = history.removeFirst()) {
                is Essence.Manifestation -> buildManifestationState(essence)
                is Essence.Confluence -> buildConfluenceState(essence)
            }
        }
    }
}
