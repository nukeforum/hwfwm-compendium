package wizardry.compendium.racetemplate.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.AbilityType
import wizardry.compendium.domain.model.RaceTemplate
import wizardry.compendium.repositories.AbilityListingRepository
import wizardry.compendium.repositories.ContributionResult
import wizardry.compendium.repositories.RaceTemplateRepository
import javax.inject.Inject

@HiltViewModel
class RaceTemplateContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RaceTemplateRepository,
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    sealed interface Mode {
        data object Create : Mode
        sealed interface Edit : Mode {
            data object Loading : Edit
            data object NotFound : Edit
            data class Ready(val template: RaceTemplate) : Edit
        }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Deleted : SaveState
        data class Error(val message: String) : SaveState
    }

    data class FormState(
        val name: String = "",
        val racialAbilities: List<Ability.Listing> = emptyList(),
    ) {
        /** A race template may only be saved once it holds exactly six racial abilities. */
        val isComplete: Boolean get() = racialAbilities.size == RaceTemplate.RACIAL_ABILITY_COUNT
    }

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    private val _formState = MutableStateFlow(FormState())
    val formState = _formState.asStateFlow()

    private val _availableListings = MutableStateFlow<List<Ability.Listing>>(emptyList())
    val availableListings = _availableListings.asStateFlow()

    init {
        viewModelScope.launch {
            _availableListings.emit(abilityListingRepository.getAbilityListings().sortedBy { it.name })
        }

        if (editName != null) {
            viewModelScope.launch {
                val match = repository.getRaceTemplate(editName)
                // Canonical (seeded) templates are read-only — same gate the
                // ability-listing editor applies via isContribution.
                if (match == null || !repository.isContribution(editName)) {
                    _mode.emit(Mode.Edit.NotFound)
                } else {
                    _formState.emit(match.toForm())
                    _mode.emit(Mode.Edit.Ready(match))
                }
            }
        }
    }

    fun setName(value: String) { _formState.update { it.copy(name = value) } }

    fun addRacialAbility(listing: Ability.Listing) {
        _formState.update { current ->
            if (current.racialAbilities.size >= RaceTemplate.RACIAL_ABILITY_COUNT ||
                current.racialAbilities.any { it.name == listing.name }
            ) {
                current
            } else {
                current.copy(racialAbilities = current.racialAbilities + listing)
            }
        }
    }

    fun removeRacialAbility(name: String) {
        _formState.update { it.copy(racialAbilities = it.racialAbilities.filterNot { ability -> ability.name == name }) }
    }

    /**
     * Only listings whose effects are all [AbilityType.RacialAbility] are
     * offered — the same "racial" filter the character build editor uses
     * (`CharacterBuildContributionsViewModel.racialAbilityCandidates`). Race
     * templates cannot reference non-racial abilities.
     */
    fun racialAbilityCandidates(): List<Ability.Listing> =
        _availableListings.value.filter { listing -> listing.effects.all { it.type == AbilityType.RacialAbility } }

    fun save() {
        val form = _formState.value
        if (form.name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name is required")) }
            return
        }
        if (form.racialAbilities.size != RaceTemplate.RACIAL_ABILITY_COUNT) {
            viewModelScope.launch {
                _saveState.emit(
                    SaveState.Error(
                        "A race template needs exactly ${RaceTemplate.RACIAL_ABILITY_COUNT} racial abilities " +
                            "(currently ${form.racialAbilities.size}).",
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            if (editName == null) {
                val collision = repository.getRaceTemplate(form.name.trim())
                if (collision != null) {
                    _saveState.emit(
                        SaveState.Error(
                            "A race template named \"${form.name.trim()}\" already exists. " +
                                "Open it from the list to edit, or pick a different name.",
                        ),
                    )
                    return@launch
                }
            }
            val result = repository.saveRaceTemplateContribution(form.toTemplate())
            when (result) {
                ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun deleteContribution() {
        val name = (mode.value as? Mode.Edit.Ready)?.template?.name ?: return
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            when (val result = repository.deleteContribution(name)) {
                ContributionResult.Success -> _saveState.emit(SaveState.Deleted)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    private fun RaceTemplate.toForm(): FormState =
        FormState(name = name, racialAbilities = racialAbilities)

    private fun FormState.toTemplate(): RaceTemplate =
        RaceTemplate(name = name.trim(), racialAbilities = racialAbilities)
}
