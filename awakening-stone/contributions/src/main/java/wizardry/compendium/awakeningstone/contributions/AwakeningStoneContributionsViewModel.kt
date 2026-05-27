package wizardry.compendium.awakeningstone.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.awakeningstone.contributions.AwakeningStoneContributionsViewModel.Mode
import wizardry.compendium.repositories.AwakeningStoneRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.share.AwakeningStoneShareUseCase
import wizardry.compendium.share.DecodedSingle
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class AwakeningStoneContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val shareUseCase: AwakeningStoneShareUseCase,
) : ViewModel() {

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    private val _deleteImpact = MutableStateFlow<DeleteImpact?>(null)
    val deleteImpact: StateFlow<DeleteImpact?> = _deleteImpact.asStateFlow()

    private val _importEvents = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 1)
    val importEvents: SharedFlow<ImportEvent> = _importEvents.asSharedFlow()

    init {
        if (editName != null) {
            viewModelScope.launch(ioDispatcher) {
                val stone = awakeningStoneRepository.getAwakeningStones().find { it.name == editName }
                if (stone != null && awakeningStoneRepository.isContribution(stone.name)) {
                    _mode.emit(Mode.Edit.Ready(stone))
                } else {
                    _mode.emit(Mode.Edit.NotFound)
                }
            }
        }
    }

    fun saveAwakeningStone(name: String, rarity: Rarity) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _saveState.emit(SaveState.Saving)
            val stone = AwakeningStone.of(name = name.trim(), rarity = rarity)
            val result = if (editName != null) {
                awakeningStoneRepository.updateAwakeningStoneContribution(
                    originalName = editName,
                    stone = stone,
                )
            } else {
                awakeningStoneRepository.saveAwakeningStoneContribution(stone)
            }
            when (result) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    /**
     * Compute the delete impact and decide what to surface to the UI. If the
     * impact is empty the delete proceeds immediately; otherwise [_deleteImpact]
     * is populated and the screen renders the confirmation dialog. This
     * collapses what used to be a 4×-duplicated `if (impact.isEmpty) LaunchedEffect`
     * pattern in every contributions screen.
     */
    fun requestDelete() {
        val target = (mode.value as? Mode.Edit.Ready)?.stone ?: return
        viewModelScope.launch(ioDispatcher) {
            val impact = awakeningStoneRepository.checkDeleteImpact(target.name)
            if (impact.isEmpty) {
                deleteContributionInternal(target.name)
            } else {
                _deleteImpact.value = impact
            }
        }
    }

    fun cancelDelete() {
        _deleteImpact.value = null
    }

    fun confirmDelete() {
        val target = (mode.value as? Mode.Edit.Ready)?.stone ?: return
        _deleteImpact.value = null
        viewModelScope.launch(ioDispatcher) {
            deleteContributionInternal(target.name)
        }
    }

    private suspend fun deleteContributionInternal(name: String) {
        _saveState.emit(SaveState.Saving)
        when (val result = awakeningStoneRepository.deleteContribution(name)) {
            is ContributionResult.Success -> _saveState.emit(SaveState.Deleted)
            is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    fun requestImport(text: String) {
        viewModelScope.launch(ioDispatcher) {
            when (val r = shareUseCase.decodeSingleStone(text)) {
                is DecodedSingle.Loaded -> _importEvents.emit(ImportEvent.Loaded(r.model))
                is DecodedSingle.Failed -> _importEvents.emit(ImportEvent.Failed(r.reason))
            }
        }
    }

    sealed interface Mode {
        data object Create : Mode
        sealed interface Edit : Mode {
            data object Loading : Edit
            data object NotFound : Edit
            data class Ready(val stone: AwakeningStone) : Edit
        }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Deleted : SaveState
        data class Error(val message: String) : SaveState
    }

    sealed interface ImportEvent {
        data class Loaded(val stone: AwakeningStone) : ImportEvent
        data class Failed(val reason: String) : ImportEvent
    }
}
