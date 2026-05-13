package wizardry.compendium.abilitylistinginfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Rank
import javax.inject.Inject

@HiltViewModel
class AbilityListingDetailViewModel(
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        abilityListingRepository: AbilityListingRepository,
        statusEffectRepository: StatusEffectRepository,
    ) : this(abilityListingRepository, statusEffectRepository, Dispatchers.IO)

    private val _state = MutableStateFlow<AbilityListingDetailUiState>(AbilityListingDetailUiState.Loading)
    val state = _state.asStateFlow()

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

    private val currentlyLoadedListing: Ability.Listing?
        get() = (state.value as? AbilityListingDetailUiState.Success)?.listing
}
