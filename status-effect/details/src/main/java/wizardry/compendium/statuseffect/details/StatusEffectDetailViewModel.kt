package wizardry.compendium.statuseffect.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.share.StatusEffectShareUseCase
import javax.inject.Inject

@HiltViewModel
class StatusEffectDetailViewModel @Inject constructor(
    private val repository: StatusEffectRepository,
    private val shareUseCase: StatusEffectShareUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<StatusEffectDetailUiState>(StatusEffectDetailUiState.Loading)
    val state = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.statusEffects.drop(1).collect { effects ->
                val current = currentlyLoaded ?: return@collect
                val refreshed = effects.find { it.name == current.name } ?: return@collect
                _state.emit(refreshed.toSuccess())
            }
        }
    }

    fun load(effectName: String) {
        viewModelScope.launch {
            _state.emit(StatusEffectDetailUiState.Loading)
            repository.getStatusEffects().find { it.name == effectName }
                ?.let { _state.emit(it.toSuccess()) }
                ?: _state.emit(
                    StatusEffectDetailUiState.Error(
                        IllegalArgumentException("no status effect found with name: $effectName")
                    )
                )
        }
    }

    fun requestShare(effect: StatusEffect) {
        viewModelScope.launch {
            _shareEvents.emit(ShareEvent.Encoded(shareUseCase.encode(effect)))
        }
    }

    private suspend fun StatusEffect.toSuccess() = StatusEffectDetailUiState.Success(
        effect = this,
        isContribution = repository.isContribution(name),
    )

    private val currentlyLoaded: StatusEffect?
        get() = (state.value as? StatusEffectDetailUiState.Success)?.effect

    sealed interface ShareEvent {
        data class Encoded(val text: String) : ShareEvent
    }
}
