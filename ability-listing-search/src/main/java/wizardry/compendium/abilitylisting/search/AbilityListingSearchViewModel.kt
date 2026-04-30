package wizardry.compendium.abilitylisting.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.model.Ability
import javax.inject.Inject

@HiltViewModel
class AbilityListingSearchViewModel
@Inject constructor(
    abilityListingRepository: AbilityListingRepository,
) : ViewModel() {
    private val listingsFlow = MutableStateFlow(emptyList<Ability.Listing>())
    private val filterTermFlow = MutableStateFlow("")

    private val _state = MutableStateFlow<AbilityListingSearchUiState>(AbilityListingSearchUiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                listingsFlow,
                filterTermFlow,
            ) { listings, filterTerm ->
                AbilityListingSearchUiState.Success(
                    listings = listings.filterWith(filterTerm),
                    filterTerm = filterTerm,
                )
            }
                .onEach { _state.emit(it) }
                .collect()
        }

        viewModelScope.launch(Dispatchers.IO) {
            abilityListingRepository.abilityListings.collect { listingsFlow.emit(it) }
        }
    }

    fun setFilterTerm(term: String) {
        viewModelScope.launch { filterTermFlow.emit(term) }
    }

    private fun List<Ability.Listing>.filterWith(term: String): List<Ability.Listing> = filter { listing ->
        listing.name.contains(term, ignoreCase = true)
    }
}
