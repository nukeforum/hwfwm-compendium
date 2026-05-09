package wizardry.compendium.awakeningstoneinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.model.AwakeningStone
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneDetailViewModel
@Inject constructor(
    private val awakeningStoneRepository: AwakeningStoneRepository,
) : ViewModel() {
    /**
     * Background dispatcher for repository work. Visible for testing so unit
     * tests can substitute the runTest dispatcher; defaults to [Dispatchers.IO]
     * in production. Constructor stays Hilt-friendly by exposing this only as
     * a settable property.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val _state = MutableStateFlow<AwakeningStoneDetailUiState>(AwakeningStoneDetailUiState.Loading)
    val state = _state.asStateFlow()

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

    private suspend fun AwakeningStone.toSuccess(): AwakeningStoneDetailUiState.Success =
        AwakeningStoneDetailUiState.Success(
            stone = this,
            isContribution = awakeningStoneRepository.isContribution(name),
        )

    private val currentlyLoadedStone: AwakeningStone?
        get() = (state.value as? AwakeningStoneDetailUiState.Success)?.stone
}
