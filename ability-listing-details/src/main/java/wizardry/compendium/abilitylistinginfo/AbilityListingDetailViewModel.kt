package wizardry.compendium.abilitylistinginfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

@HiltViewModel
class AbilityListingDetailViewModel
@Inject constructor(
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AbilityListingDetailUiState>(AbilityListingDetailUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            abilityListingRepository.abilityListings.drop(1).collect { listings ->
                val current = currentlyLoadedListing ?: return@collect
                val refreshed = listings.find { it.name == current.name } ?: return@collect
                _state.emit(AbilityListingDetailUiState.Success(refreshed))
            }
        }
    }

    fun load(listingName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.emit(AbilityListingDetailUiState.Loading)

            abilityListingRepository.getAbilityListings().find { it.name == listingName }
                ?.let { _state.emit(AbilityListingDetailUiState.Success(it)) }
                ?: _state.emit(
                    AbilityListingDetailUiState.Error(
                        IllegalArgumentException("no ability listing found with name: $listingName")
                    )
                )
        }
    }

    private val currentlyLoadedListing: Ability.Listing?
        get() = (state.value as? AbilityListingDetailUiState.Success)?.listing
}
