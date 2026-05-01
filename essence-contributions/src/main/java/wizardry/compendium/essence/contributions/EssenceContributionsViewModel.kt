package wizardry.compendium.essence.contributions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import wizardry.compendium.essences.ContributionResult
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rarity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EssenceContributionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val essenceRepository: EssenceRepository,
) : ViewModel() {

    private val editName: String? = savedStateHandle.get<String>("name")

    private val _availableManifestations = MutableStateFlow<List<Essence.Manifestation>>(emptyList())
    val availableManifestations = _availableManifestations.asStateFlow()

    private val _availableConfluences = MutableStateFlow<List<Essence.Confluence>>(emptyList())
    val availableConfluences = _availableConfluences.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    private val _mode = MutableStateFlow<Mode>(if (editName == null) Mode.Create else Mode.Edit.Loading)
    val mode = _mode.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            essenceRepository.essences.collect { essences ->
                _availableManifestations.emit(essences.filterIsInstance<Essence.Manifestation>())
                _availableConfluences.emit(essences.filterIsInstance<Essence.Confluence>())
            }
        }
        if (editName != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val essence = essenceRepository.getEssences().find { it.name == editName }
                val isContribution = essence != null && essenceRepository.isContribution(essence.name)
                if (essence == null || !isContribution) {
                    _mode.emit(Mode.Edit.NotFound)
                } else when (essence) {
                    is Essence.Manifestation -> _mode.emit(Mode.Edit.ManifestationReady(essence))
                    is Essence.Confluence -> _mode.emit(Mode.Edit.ConfluenceReady(essence))
                }
            }
        }
    }

    fun saveManifestation(
        name: String,
        rarity: Rarity,
        description: String,
        isRestricted: Boolean,
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val manifestation = Essence.of(
                name = name.trim(),
                description = description.trim(),
                rarity = rarity,
                restricted = isRestricted,
            )
            val result = if (editName != null) {
                essenceRepository.updateManifestationContribution(manifestation)
            } else {
                essenceRepository.saveManifestationContribution(manifestation)
            }
            result.emit()
        }
    }

    fun updateConfluence(
        name: String,
        isRestricted: Boolean,
    ) {
        val source = (mode.value as? Mode.Edit.ConfluenceReady)?.confluence ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val updated = source.copy(name = name.trim(), isRestricted = isRestricted)
            essenceRepository.updateConfluenceContribution(updated).emit()
        }
    }

    fun saveConfluence(
        name: String,
        manifestation1: Essence.Manifestation,
        manifestation2: Essence.Manifestation,
        manifestation3: Essence.Manifestation,
        isRestricted: Boolean,
    ) {
        if (name.isBlank()) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("Name cannot be empty")) }
            return
        }
        if (!areAllDifferent(manifestation1, manifestation2, manifestation3)) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("All three essences must be different")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val confluence = Essence.of(
                name = name.trim(),
                restricted = isRestricted,
                ConfluenceSet(manifestation1, manifestation2, manifestation3),
            )
            essenceRepository.saveConfluenceContribution(
                confluence = confluence,
                referencedManifestations = listOf(manifestation1, manifestation2, manifestation3),
            ).emit()
        }
    }

    fun addCombinationToConfluence(
        target: Essence.Confluence,
        manifestation1: Essence.Manifestation,
        manifestation2: Essence.Manifestation,
        manifestation3: Essence.Manifestation,
        isRestricted: Boolean,
    ) {
        if (!areAllDifferent(manifestation1, manifestation2, manifestation3)) {
            viewModelScope.launch { _saveState.emit(SaveState.Error("All three essences must be different")) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            val combination = ConfluenceSet(manifestation1, manifestation2, manifestation3, isRestricted)
            essenceRepository.addCombinationToConfluence(target, combination).emit()
        }
    }

    fun deleteContribution() {
        val name = when (val m = mode.value) {
            is Mode.Edit.ManifestationReady -> m.manifestation.name
            is Mode.Edit.ConfluenceReady -> m.confluence.name
            else -> return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.emit(SaveState.Saving)
            when (val result = essenceRepository.deleteContribution(name)) {
                is ContributionResult.Success -> _saveState.emit(SaveState.Deleted)
                is ContributionResult.Failure -> _saveState.emit(SaveState.Error(result.message))
            }
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    private suspend fun ContributionResult.emit() {
        when (this) {
            is ContributionResult.Success -> _saveState.emit(SaveState.Success)
            is ContributionResult.Failure -> _saveState.emit(SaveState.Error(message))
        }
    }

    private fun areAllDifferent(
        m1: Essence.Manifestation,
        m2: Essence.Manifestation,
        m3: Essence.Manifestation,
    ): Boolean = m1.name != m2.name && m1.name != m3.name && m2.name != m3.name

    sealed interface Mode {
        data object Create : Mode
        sealed interface Edit : Mode {
            data object Loading : Edit
            data object NotFound : Edit
            data class ManifestationReady(val manifestation: Essence.Manifestation) : Edit
            data class ConfluenceReady(val confluence: Essence.Confluence) : Edit
        }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data object Deleted : SaveState
        data class Error(val message: String) : SaveState
    }
}
