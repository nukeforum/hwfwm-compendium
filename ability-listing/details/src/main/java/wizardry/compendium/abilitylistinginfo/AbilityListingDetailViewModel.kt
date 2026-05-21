package wizardry.compendium.abilitylistinginfo

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
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.StatusEffectRepository
import wizardry.compendium.ability.preview.AbilityTextRenderer
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.Rank
import wizardry.compendium.share.AbilityListingShareUseCase
import wizardry.compendium.ui.coroutines.IoDispatcher
import javax.inject.Inject

@HiltViewModel
class AbilityListingDetailViewModel @Inject constructor(
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val shareUseCase: AbilityListingShareUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<AbilityListingDetailUiState>(AbilityListingDetailUiState.Loading)
    val state = _state.asStateFlow()

    private val _shareEvents = MutableSharedFlow<ShareEvent>(extraBufferCapacity = 1)
    val shareEvents: SharedFlow<ShareEvent> = _shareEvents.asSharedFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            abilityListingRepository.abilityListings.drop(1).collect { listings ->
                val current = currentlyLoadedListing ?: return@collect
                val refreshed = listings.find { it.name == current.name } ?: return@collect
                _state.emit(refreshed.toSuccess())
            }
        }
        viewModelScope.launch(ioDispatcher) {
            statusEffectRepository.statusEffects.drop(1).collect { effects ->
                val currentSuccess = state.value as? AbilityListingDetailUiState.Success ?: return@collect
                _state.emit(currentSuccess.copy(statusEffects = effects))
            }
        }
    }

    fun load(listingName: String) {
        viewModelScope.launch(ioDispatcher) {
            _state.emit(AbilityListingDetailUiState.Loading)

            val listing = abilityListingRepository.getAbilityListings().find { it.name == listingName }
            if (listing == null) {
                _state.emit(
                    AbilityListingDetailUiState.Error(
                        IllegalArgumentException("no ability found with name: $listingName"),
                    ),
                )
                return@launch
            }
            _state.emit(listing.toSuccess())
        }
    }

    private suspend fun Ability.Listing.toSuccess(): AbilityListingDetailUiState.Success =
        AbilityListingDetailUiState.Success(
            listing = this,
            isContribution = abilityListingRepository.isContribution(name),
            statusEffects = statusEffectRepository.getStatusEffects(),
        )

    fun selectRank(rank: Rank?) {
        viewModelScope.launch {
            val current = _state.value as? AbilityListingDetailUiState.Success ?: return@launch
            _state.emit(current.copy(selectedRank = rank))
        }
    }

    fun requestShareAsText(listing: Ability.Listing) {
        val success = state.value as? AbilityListingDetailUiState.Success ?: return
        viewModelScope.launch {
            _shareEvents.emit(
                ShareEvent.EncodedAsText(
                    AbilityTextRenderer.renderAbilityReport(
                        ability = listing,
                        rankCeiling = null,
                        statusEffects = success.statusEffects,
                    ),
                ),
            )
        }
    }

    fun requestExport(listing: Ability.Listing) {
        viewModelScope.launch {
            _shareEvents.emit(ShareEvent.Encoded(shareUseCase.encode(listing)))
        }
    }

    private val currentlyLoadedListing: Ability.Listing?
        get() = (state.value as? AbilityListingDetailUiState.Success)?.listing

    sealed interface ShareEvent {
        data class Encoded(val text: String) : ShareEvent
        data class EncodedAsText(val text: String) : ShareEvent
    }
}
