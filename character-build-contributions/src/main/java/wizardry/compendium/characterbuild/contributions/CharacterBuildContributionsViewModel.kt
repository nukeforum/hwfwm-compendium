package wizardry.compendium.characterbuild.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import javax.inject.Inject

@HiltViewModel
class CharacterBuildContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val buildRepository: CharacterBuildRepository,
    private val essenceRepository: EssenceRepository,
    private val abilityListingRepository: AbilityListingRepository,
) : ViewModel() {

    enum class Slot { Power, Speed, Spirit, Recovery }

    sealed interface Mode {
        data object Create : Mode
        sealed interface Edit : Mode {
            data object Loading : Edit
            data object NotFound : Edit
            data class Ready(val build: CharacterBuild) : Edit
        }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Deleted : SaveState
        data class Error(val message: String) : SaveState
    }

    data class SlotState(
        val essence: Essence.Manifestation?,
        val abilities: List<Ability.Listing>,
    )

    data class FormState(
        val name: String = "",
        val race: String = "",
        val racialAbilities: List<Ability.Listing> = emptyList(),
        val attributes: Map<Slot, SlotState> = Slot.entries.associateWith { SlotState(null, emptyList()) },
    )

    data class EssenceChangePrompt(
        val slot: Slot,
        val target: Essence.Manifestation?,
    )

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    private val _formState = MutableStateFlow(FormState())
    val formState = _formState.asStateFlow()

    private val _essenceChangePrompt = MutableStateFlow<EssenceChangePrompt?>(null)
    val essenceChangePrompt = _essenceChangePrompt.asStateFlow()

    private val _availableEssences = MutableStateFlow<List<Essence.Manifestation>>(emptyList())
    val availableEssences = _availableEssences.asStateFlow()

    private val _availableListings = MutableStateFlow<List<Ability.Listing>>(emptyList())
    val availableListings = _availableListings.asStateFlow()

    init {
        viewModelScope.launch {
            _availableEssences.emit(
                essenceRepository.getEssences().filterIsInstance<Essence.Manifestation>().sortedBy { it.name },
            )
            _availableListings.emit(abilityListingRepository.getAbilityListings().sortedBy { it.name })
        }

        if (editName != null) {
            viewModelScope.launch {
                val match = buildRepository.getBuild(editName)
                if (match == null) {
                    _mode.emit(Mode.Edit.NotFound)
                } else {
                    _formState.emit(match.toForm())
                    _mode.emit(Mode.Edit.Ready(match))
                }
            }
        }
    }

    fun setName(value: String) { _formState.update { it.copy(name = value) } }
    fun setRace(value: String) { _formState.update { it.copy(race = value) } }

    fun addRacialAbility(listing: Ability.Listing) {
        _formState.update { current ->
            if (current.racialAbilities.size >= 6 || current.racialAbilities.any { it.name == listing.name }) current
            else current.copy(racialAbilities = current.racialAbilities + listing)
        }
    }

    fun removeRacialAbility(name: String) {
        _formState.update { it.copy(racialAbilities = it.racialAbilities.filterNot { ability -> ability.name == name }) }
    }

    fun addAbilityToSlot(slot: Slot, listing: Ability.Listing) {
        _formState.update { current ->
            val slotState = current.attributes[slot] ?: return@update current
            if (slotState.essence == null) return@update current
            if (slotState.abilities.size >= 5) return@update current
            if (slotState.abilities.any { it.name == listing.name }) return@update current
            current.withSlot(slot) { it.copy(abilities = it.abilities + listing) }
        }
    }

    fun removeAbilityFromSlot(slot: Slot, name: String) {
        _formState.update { current ->
            current.withSlot(slot) { it.copy(abilities = it.abilities.filterNot { ability -> ability.name == name }) }
        }
    }

    fun requestEssenceChange(slot: Slot, target: Essence.Manifestation?) {
        val current = _formState.value.attributes[slot] ?: return
        if (current.abilities.isEmpty()) {
            _formState.update { it.withSlot(slot) { existing -> existing.copy(essence = target) } }
        } else {
            _essenceChangePrompt.value = EssenceChangePrompt(slot = slot, target = target)
        }
    }

    fun confirmEssenceChangeClearingAbilities() {
        val prompt = _essenceChangePrompt.value ?: return
        _formState.update {
            it.withSlot(prompt.slot) { existing -> existing.copy(essence = prompt.target, abilities = emptyList()) }
        }
        _essenceChangePrompt.value = null
    }

    fun confirmEssenceChangeKeepingAbilities() {
        val prompt = _essenceChangePrompt.value ?: return
        _formState.update {
            it.withSlot(prompt.slot) { existing -> existing.copy(essence = prompt.target) }
        }
        _essenceChangePrompt.value = null
    }

    fun cancelEssenceChange() { _essenceChangePrompt.value = null }

    fun racialAbilityCandidates(): List<Ability.Listing> =
        _availableListings.value.filter { listing -> listing.effects.all { it.type == AbilityType.RacialAbility } }

    fun slotAbilityCandidates(): List<Ability.Listing> =
        _availableListings.value.filter { listing -> listing.effects.none { it.type == AbilityType.RacialAbility } }

    fun save() {
        val form = _formState.value
        if (form.name.isBlank() || form.race.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name and race are required")) }
            return
        }
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            if (editName == null) {
                val collision = buildRepository.getBuild(form.name)
                if (collision != null) {
                    _saveState.emit(SaveState.Error("A build named \"${form.name}\" already exists. Open it from the list to edit, or pick a different name."))
                    return@launch
                }
            }
            val build = form.toBuild()
            val result = buildRepository.saveBuildContribution(build)
            when (result) {
                ContributionResult.Success -> _saveState.emit(SaveState.Success)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun deleteContribution() {
        val name = (mode.value as? Mode.Edit.Ready)?.build?.name ?: return
        viewModelScope.launch {
            _saveState.emit(SaveState.Saving)
            when (val result = buildRepository.deleteContribution(name)) {
                ContributionResult.Success -> _saveState.emit(SaveState.Deleted)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    private fun CharacterBuild.toForm(): FormState {
        val slotMap = Slot.entries.associateWith { slot ->
            val attribute = when (slot) {
                Slot.Power -> Power
                Slot.Speed -> Speed
                Slot.Spirit -> Spirit
                Slot.Recovery -> Recovery
            }
            val absorbed = attribute.essence
            SlotState(
                essence = absorbed?.essence as? Essence.Manifestation,
                abilities = absorbed?.abilities?.map { Ability.Listing.of(it.name) } ?: emptyList(),
            )
        }
        return FormState(
            name = name,
            race = race,
            racialAbilities = racialAbilities,
            attributes = slotMap,
        )
    }

    private fun FormState.toBuild(): CharacterBuild {
        val attrSet = Slot.entries.map { slot ->
            val state = attributes[slot] ?: SlotState(null, emptyList())
            val absorbed = state.essence?.let { essence ->
                AbsorbedEssence(
                    essence = essence,
                    abilities = state.abilities.map { it.acquire(essence) },
                )
            }
            when (slot) {
                Slot.Power -> Attribute.Power(essence = absorbed)
                Slot.Speed -> Attribute.Speed(essence = absorbed)
                Slot.Spirit -> Attribute.Spirit(essence = absorbed)
                Slot.Recovery -> Attribute.Recovery(essence = absorbed)
            }
        }.toSet()
        return CharacterBuild(name = name.trim(), race = race.trim(), racialAbilities = racialAbilities, attributes = attrSet)
    }

    private fun FormState.withSlot(slot: Slot, mutator: (SlotState) -> SlotState): FormState {
        val current = attributes[slot] ?: return this
        return copy(attributes = attributes + (slot to mutator(current)))
    }
}
