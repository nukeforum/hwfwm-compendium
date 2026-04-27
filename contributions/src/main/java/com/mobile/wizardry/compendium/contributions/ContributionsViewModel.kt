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

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState = _saveState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val essences = essenceProvider.getEssences()
            _availableManifestations.emit(essences.filterIsInstance<Essence.Manifestation>())
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
            val newEssence = Essence.of(
                name = name.trim(),
                description = description.trim(),
                rarity = rarity,
                restricted = isRestricted,
            )
            val existing = contributionsCache.contents
            contributionsCache.contents = existing + newEssence
            _saveState.emit(SaveState.Success)
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
        if (manifestation1 == manifestation2 || manifestation1 == manifestation3 || manifestation2 == manifestation3) {
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
        }
    }

    fun clearSaveState() {
        viewModelScope.launch { _saveState.emit(SaveState.Idle) }
    }

    sealed interface SaveState {
        data object Idle : SaveState
        data object Saving : SaveState
        data object Success : SaveState
        data class Error(val message: String) : SaveState
    }
}
