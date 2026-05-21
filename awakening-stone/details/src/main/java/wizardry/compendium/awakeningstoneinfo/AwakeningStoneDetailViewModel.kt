package wizardry.compendium.awakeningstoneinfo

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
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.share.AwakeningStoneShareUseCase
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneDetailViewModel @Inject constructor(
    private val awakeningStoneRepository: AwakeningStoneRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val shareUseCase: AwakeningStoneShareUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AwakeningStoneDetailUiState>(AwakeningStoneDetailUiState.Loading)
    val state = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            awakeningStoneRepository.awakeningStones.drop(1).collect { stones ->
                val current = currentlyLoadedStone ?: return@collect
                val refreshed = stones.find { it.name == current.name } ?: return@collect
                _state.emit(refreshed.toSuccess())
            }
        }
    }

    fun load(stoneName: String) {
        viewModelScope.launch(ioDispatcher) {
            _state.emit(AwakeningStoneDetailUiState.Loading)

            awakeningStoneRepository.getAwakeningStones().find { it.name == stoneName }
                ?.let { _state.emit(it.toSuccess()) }
                ?: _state.emit(
                    AwakeningStoneDetailUiState.Error(
                        IllegalArgumentException("no awakening stone found with name: $stoneName")
                    )
                )
        }
    }

    fun requestShareAsText(stone: AwakeningStone) {
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.EncodedAsText(
                    text = AwakeningStoneTextRenderer.renderAsText(stone),
                    title = "Share ${stone.name} awakening stone",
                ),
            )
        }
    }

    fun requestExport(stone: AwakeningStone) {
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.Encoded(
                    text = shareUseCase.encode(stone),
                    title = "Export ${stone.name} awakening stone",
                ),
            )
        }
    }

    private suspend fun AwakeningStone.toSuccess(): AwakeningStoneDetailUiState.Success =
        AwakeningStoneDetailUiState.Success(
            stone = this,
            isContribution = awakeningStoneRepository.isContribution(name),
        )

    private val currentlyLoadedStone: AwakeningStone?
        get() = (state.value as? AwakeningStoneDetailUiState.Success)?.stone

    sealed interface ShareEvent {
        val text: String
        val title: String
        data class Encoded(override val text: String, override val title: String) : ShareEvent
        data class EncodedAsText(override val text: String, override val title: String) : ShareEvent
    }
}
