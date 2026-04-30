package wizardry.compendium.contributions

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
class ContributionsViewModel @Inject constructor(
    private val essenceRepository: EssenceRepository,
) : ViewModel() {

    private val _availableManifestations = MutableStateFlow<List<Essence.Manifestation>>(emptyList())
    val availableManifestations = _availableManifestations.asStateFlow()

    private val _availableConfluences = MutableStateFlow<List<Essence.Confluence>>(emptyList())
    val availableConfluences = _availableConfluences.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            essenceRepository.essences.collect { essences ->
                _availableManifestations.emit(essences.filterIsInstance<Essence.Manifestation>())
                _availableConfluences.emit(essences.filterIsInstance<Essence.Confluence>())
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
            essenceRepository.saveManifestationContribution(manifestation).emit()
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

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data class Error(val message: String) : SaveState
    }
}
