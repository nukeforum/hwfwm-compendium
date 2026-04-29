package com.mobile.wizardry.compendium.contributions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.wizardry.compendium.essences.EssenceProvider
import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity
import com.mobile.wizardry.compendium.persistence.Contributions
import com.mobile.wizardry.compendium.persistence.EssenceCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContributionsViewModel @Inject constructor(
    @Contributions private val contributionsCache: EssenceCache,
    private val essenceProvider: EssenceProvider,
) : ViewModel() {

    private val _availableManifestations = MutableStateFlow<List<Essence.Manifestation>>(emptyList())
    val availableManifestations = _availableManifestations.asStateFlow()

    private val _availableConfluences = MutableStateFlow<List<Essence.Confluence>>(emptyList())
    val availableConfluences = _availableConfluences.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    init {
        refreshAvailable()
    }

    private fun refreshAvailable() {
        viewModelScope.launch(Dispatchers.IO) {
            val essences = essenceProvider.getEssences()
            _availableManifestations.emit(essences.filterIsInstance<Essence.Manifestation>())
            _availableConfluences.emit(essences.filterIsInstance<Essence.Confluence>())
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
            val trimmedName = name.trim()
            val existingNames = essenceProvider.getEssences()
                .filterIsInstance<Essence.Manifestation>()
                .map { it.name }
                .toSet()
            if (existingNames.any { it.equals(trimmedName, ignoreCase = true) }) {
                _saveState.emit(SaveState.Error("An essence named \"$trimmedName\" already exists"))
                return@launch
            }
            val newEssence = Essence.of(
                name = trimmedName,
                description = description.trim(),
                rarity = rarity,
                restricted = isRestricted,
            )
            val existing = contributionsCache.contents
            contributionsCache.contents = existing + newEssence
            _saveState.emit(SaveState.Success)
            refreshAvailable()
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
            val newConfluence = Essence.of(
                name = name.trim(),
                restricted = isRestricted,
                ConfluenceSet(manifestation1, manifestation2, manifestation3),
            )
            val existing = contributionsCache.contents
            // Ensure the 3 referenced manifestations exist in contributions.db for FK constraints.
            val existingNames = existing.map { it.name }.toSet()
            val referencedManifestations = listOf(manifestation1, manifestation2, manifestation3)
                .filter { it.name !in existingNames }
            contributionsCache.contents = existing + referencedManifestations + newConfluence
            _saveState.emit(SaveState.Success)
            refreshAvailable()
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

            val newCombinationNames = setOf(manifestation1.name, manifestation2.name, manifestation3.name)
            val allConfluences = essenceProvider.getEssences().filterIsInstance<Essence.Confluence>()
            val duplicateOwner = allConfluences.firstOrNull { conf ->
                conf.confluenceSets.any { it.set.map { m -> m.name }.toSet() == newCombinationNames }
            }
            if (duplicateOwner != null) {
                _saveState.emit(SaveState.Error("That combination already produces ${duplicateOwner.name}"))
                return@launch
            }

            val existing = contributionsCache.contents
            val withoutTarget = existing.filterNot { it.name == target.name }
            val sourceConfluence = (existing.firstOrNull { it.name == target.name } as? Essence.Confluence)
                ?: target
            val newSet = ConfluenceSet(manifestation1, manifestation2, manifestation3, isRestricted)
            val updatedConfluence = sourceConfluence.copy(
                confluenceSets = sourceConfluence.confluenceSets + newSet,
            )

            val existingNames = withoutTarget.map { it.name }.toSet()
            val manifestationsToAdd = updatedConfluence.confluenceSets
                .flatMap { it.set }
                .distinctBy { it.name }
                .filter { it.name !in existingNames }

            contributionsCache.contents = withoutTarget + manifestationsToAdd + updatedConfluence
            _saveState.emit(SaveState.Success)
            refreshAvailable()
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
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
