package wizardry.compendium.awakeningstoneinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val _state = MutableStateFlow<AwakeningStoneDetailUiState>(AwakeningStoneDetailUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            awakeningStoneRepository.awakeningStones.drop(1).collect { stones ->
                val current = currentlyLoadedStone ?: return@collect
                val refreshed = stones.find { it.name == current.name } ?: return@collect
                _state.emit(AwakeningStoneDetailUiState.Success(refreshed))
            }
        }
    }

    fun load(stoneName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(AwakeningStoneDetailUiState.Loading)

            awakeningStoneRepository.getAwakeningStones().find { it.name == stoneName }
                ?.let { _state.emit(AwakeningStoneDetailUiState.Success(it)) }
                ?: _state.emit(
                    AwakeningStoneDetailUiState.Error(
                        IllegalArgumentException("no awakening stone found with name: $stoneName")
                    )
                )
        }
    }

    private val currentlyLoadedStone: AwakeningStone?
        get() = (state.value as? AwakeningStoneDetailUiState.Success)?.stone
}
