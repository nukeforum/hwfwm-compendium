package wizardry.compendium.essenceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.repositories.EssenceRepository
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.share.EssenceShareUseCase
import wizardry.compendium.ui.coroutines.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EssenceDetailViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val shareUseCase: EssenceShareUseCase,
) : ViewModel() {

    // Owned by the thread that calls load() and goBack(), which is the main
    // thread. Only those two functions touch it; the coroutines they launch on
    // ioDispatcher are handed the essences they need and never see the deque.
    private val history = ArrayDeque<Essence>()
    private val _state = MutableStateFlow<EssenceDetailUiState>(EssenceDetailUiState.Loading)
    val state = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            // Re-resolve the current essence by name when the data set changes.
            // drop(1) avoids double-loading on first composition since load() is called
            // by the composable via LaunchedEffect.
            essenceRepository.essences.drop(1).collect { essences ->
                val current = state.value as? EssenceDetailUiState.Success ?: return@collect
                val refreshed = essences.find { it.name == current.essence.name } ?: return@collect
                buildState(refreshed, previous = current.previousEssence)
            }
        }
    }

    fun load(essenceName: String) {
        val previous = pushEssenceOnScreen()
        viewModelScope.launch(ioDispatcher) {
            _state.emit(EssenceDetailUiState.Loading)

            essenceRepository.getEssences().find { it.name == essenceName }
                ?.let { essence -> buildState(essence, previous) }
                ?: _state.emit(EssenceDetailUiState.Error(IllegalArgumentException("no essence found with name: $essenceName")))
        }
    }

    fun load(essence: Essence) {
        val previous = pushEssenceOnScreen()
        viewModelScope.launch(ioDispatcher) {
            _state.emit(EssenceDetailUiState.Loading)

            buildState(essence, previous)
        }
    }

    /** Pushes the essence on screen, if any, and returns the new top of the history. */
    private fun pushEssenceOnScreen(): Essence? {
        currentlyLoadedEssence?.let { history.addFirst(it) }
        return history.firstOrNull()
    }

    fun requestShareAsText(essence: Essence) {
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.EncodedAsText(
                    text = EssenceTextRenderer.renderAsText(essence),
                    title = "Share ${essence.name} essence",
                ),
            )
        }
    }

    fun requestExport(essence: Essence) {
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.Encoded(
                    text = shareUseCase.encode(essence),
                    title = "Export ${essence.name} essence",
                ),
            )
        }
    }

    private suspend fun buildState(essence: Essence, previous: Essence?) {
        when (essence) {
            is Essence.Manifestation -> buildManifestationState(essence, previous)
            is Essence.Confluence -> buildConfluenceState(essence, previous)
        }
    }

    private suspend fun buildConfluenceState(essence: Essence.Confluence, previous: Essence?) {
        EssenceDetailUiState.Success.ConfluenceUiState(
            essence = essence,
            previousEssence = previous as? Essence.Manifestation,
            isContribution = essenceRepository.isContribution(essence.name),
        ).emit()
    }

    private suspend fun buildManifestationState(essence: Essence.Manifestation, previous: Essence?) {
        val confluences = essenceRepository.getEssences()
            .filterIsInstance<Essence.Confluence>()
            .filter { it.isProducedBy(essence) }

        EssenceDetailUiState.Success.ManifestationUiState(
            essence = essence,
            previousEssence = previous as? Essence.Confluence,
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
        val essence = history.removeFirst()
        val previous = history.firstOrNull()
        viewModelScope.launch(ioDispatcher) {
            buildState(essence, previous)
        }
    }

    sealed interface ShareEvent {
        val text: String
        val title: String
        data class Encoded(override val text: String, override val title: String) : ShareEvent
        data class EncodedAsText(override val text: String, override val title: String) : ShareEvent
    }
}
