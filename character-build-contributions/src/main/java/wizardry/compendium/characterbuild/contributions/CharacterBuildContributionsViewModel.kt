package wizardry.compendium.characterbuild.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
import wizardry.compendium.essences.model.ConfluenceSet
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
        val essence: Essence?,
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
        val target: Essence?,
    )

    data class ConfluenceSetPrompt(
        val slot: Slot,
        val confluence: Essence.Confluence,
        val sets: List<ConfluenceSet>,
    )

    data class SaveCombinationPrompt(
        val slot: Slot,
        val confluence: Essence.Confluence,
        val combination: List<Essence.Manifestation>,
    )

    data class ConfluencePickerRow(
        val confluence: Essence.Confluence,
        /** User's already-selected non-target essences that appear in any of this confluence's sets. Deduped, name-sorted. */
        val matchedEssences: List<Essence.Manifestation>,
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

    private val _confluenceSetPrompt = MutableStateFlow<ConfluenceSetPrompt?>(null)
    val confluenceSetPrompt = _confluenceSetPrompt.asStateFlow()

    private val _saveCombinationPrompt = MutableStateFlow<SaveCombinationPrompt?>(null)
    val saveCombinationPrompt = _saveCombinationPrompt.asStateFlow()

    private val _availableEssences = MutableStateFlow<List<Essence.Manifestation>>(emptyList())
    val availableEssences = _availableEssences.asStateFlow()

    private val _availableConfluences = MutableStateFlow<List<Essence.Confluence>>(emptyList())
    val availableConfluences = _availableConfluences.asStateFlow()

    private val _availableListings = MutableStateFlow<List<Ability.Listing>>(emptyList())
    val availableListings = _availableListings.asStateFlow()

    val confluenceWarning: StateFlow<Slot?> = _formState
        .map { computeConfluenceWarning(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun computeConfluenceWarning(form: FormState): Slot? {
        val essencesBySlot = form.attributes.mapValues { it.value.essence }
        if (essencesBySlot.values.any { it == null }) return null
        val confluenceSlots = essencesBySlot.filterValues { it is Essence.Confluence }
        if (confluenceSlots.size != 1) return null
        val (slot, essence) = confluenceSlots.entries.single()
        val confluence = essence as Essence.Confluence
        val manifestations = (essencesBySlot - slot).values
            .mapNotNull { it as? Essence.Manifestation }
            .toSet()
        val matches = confluence.confluenceSets.any { it.set == manifestations }
        return if (matches) null else slot
    }

    init {
        viewModelScope.launch {
            val essences = essenceRepository.getEssences()
            _availableEssences.emit(
                essences.filterIsInstance<Essence.Manifestation>().sortedBy { it.name },
            )
            _availableConfluences.emit(
                essences.filterIsInstance<Essence.Confluence>().sortedBy { it.name },
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

    /**
     * Apply a Confluence pick to [slot].
     *
     * - When [set] is non-null, fill empty non-target slots with leftover set members
     *   (alphabetical by essence name, walking Slot.entries order skipping the target).
     *   Non-target slots that already hold a set-member essence are left untouched.
     * - When [set] is null, no fill is performed; only the target slot is changed.
     * - If the target slot held abilities, route through the existing
     *   `requestEssenceChange` 3-way prompt (clear / keep / cancel) for that slot.
     */
    fun applyConfluence(slot: Slot, confluence: Essence.Confluence, set: ConfluenceSet?) {
        if (set != null) {
            val occupied = (Slot.entries - slot)
                .mapNotNull { _formState.value.attributes[it]?.essence }
                .toSet()
            val leftovers = set.set
                .filterNot { it in occupied }
                .sortedBy { it.name }
            val emptyOthers = (Slot.entries - slot).filter { _formState.value.attributes[it]?.essence == null }
            _formState.update { current ->
                var next = current
                emptyOthers.zip(leftovers).forEach { (emptySlot, fill) ->
                    next = next.withSlot(emptySlot) { it.copy(essence = fill) }
                }
                next
            }
        }
        requestEssenceChange(slot, confluence)
    }

    /**
     * Entry point for picking a Confluence from the essence picker. Routes to
     * one of: applyConfluence (1 compatible set or 0-compat-1or2-essences),
     * confluenceSetPrompt (2+ compatible sets), or saveCombinationPrompt
     * (0 compatible + exactly 3 other essences).
     */
    fun requestConfluencePick(slot: Slot, confluence: Essence.Confluence) {
        val compatible = compatibleSetsFor(slot, confluence)
        val others = otherSlotManifestations(slot)
        when {
            compatible.size == 1 -> applyConfluence(slot, confluence, compatible.single())
            compatible.size >= 2 -> {
                _confluenceSetPrompt.value = ConfluenceSetPrompt(slot, confluence, compatible)
            }
            others.size == 3 -> {
                _saveCombinationPrompt.value = SaveCombinationPrompt(
                    slot = slot,
                    confluence = confluence,
                    combination = others.sortedBy { it.name },
                )
            }
            else -> applyConfluence(slot, confluence, null)
        }
    }

    fun confirmConfluenceSetPick(set: ConfluenceSet) {
        val prompt = _confluenceSetPrompt.value ?: return
        _confluenceSetPrompt.value = null
        applyConfluence(prompt.slot, prompt.confluence, set)
    }

    fun cancelConfluenceSetPick() {
        _confluenceSetPrompt.value = null
    }

    /**
     * "Yes" path: persist the user's 3-essence combination as a new ConfluenceSet
     * on the target Confluence, then assign the Confluence to the target slot.
     */
    fun confirmSaveCombination() {
        val prompt = _saveCombinationPrompt.value ?: return
        _saveCombinationPrompt.value = null
        viewModelScope.launch {
            essenceRepository.addCombinationToConfluence(
                target = prompt.confluence,
                combination = ConfluenceSet(prompt.combination.toSet()),
            )
            applyConfluence(prompt.slot, prompt.confluence, null)
        }
    }

    /**
     * Surface the save-combination dialog seeded from the current warning state.
     * No-op if no warning is active.
     */
    fun resolveWarningSaveCombination() {
        val slot = confluenceWarning.value ?: return
        val form = _formState.value
        val confluence = form.attributes[slot]?.essence as? Essence.Confluence ?: return
        val combination = (Slot.entries - slot)
            .mapNotNull { form.attributes[it]?.essence as? Essence.Manifestation }
            .sortedBy { it.name }
        if (combination.size != 3) return
        _saveCombinationPrompt.value = SaveCombinationPrompt(slot, confluence, combination)
    }

    /**
     * [complete] = true is the "No" path (assign Confluence, don't write).
     * [complete] = false is the "Cancel" path (no slot change, no write).
     */
    fun dismissSaveCombinationPrompt(complete: Boolean) {
        val prompt = _saveCombinationPrompt.value ?: return
        _saveCombinationPrompt.value = null
        if (complete) applyConfluence(prompt.slot, prompt.confluence, null)
    }

    fun requestEssenceChange(slot: Slot, target: Essence?) {
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

    /**
     * Returns the Confluence-tab rows for the picker when [slot] is being changed.
     * Sort key: matchedEssences.size desc, then confluence.name asc.
     */
    fun confluencePickerRowsFor(slot: Slot): List<ConfluencePickerRow> {
        val others = otherSlotManifestations(slot)
        return _availableConfluences.value
            .map { confluence ->
                val matched = confluence.confluenceSets
                    .flatMap { it.set }
                    .filter { it in others }
                    .distinctBy { it.name }
                    .sortedBy { it.name }
                ConfluencePickerRow(confluence, matched)
            }
            .sortedWith(
                compareByDescending<ConfluencePickerRow> { it.matchedEssences.size }
                    .thenBy { it.confluence.name },
            )
    }

    /**
     * Sets from [confluence] whose 3-essence membership is a superset of the user's
     * existing non-target manifestation essences.
     */
    fun compatibleSetsFor(slot: Slot, confluence: Essence.Confluence): List<ConfluenceSet> {
        val others = otherSlotManifestations(slot)
        return confluence.confluenceSets.filter { it.set.containsAll(others) }
    }

    /** Manifestation essences in slots other than [slot]. Excludes Confluences and nulls. */
    private fun otherSlotManifestations(slot: Slot): Set<Essence.Manifestation> {
        val attributes = _formState.value.attributes
        return (Slot.entries - slot)
            .mapNotNull { attributes[it]?.essence as? Essence.Manifestation }
            .toSet()
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
                essence = absorbed?.essence,
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
