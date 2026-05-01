package wizardry.compendium.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.preferences.PreferencesRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    essenceRepository: EssenceRepository,
    awakeningStoneRepository: AwakeningStoneRepository,
    abilityListingRepository: AbilityListingRepository,
) : ViewModel() {
    val essenceContributionsEnabled = preferencesRepository.essenceContributionsEnabled
    val awakeningStoneContributionsEnabled = preferencesRepository.awakeningStoneContributionsEnabled
    val abilityListingContributionsEnabled = preferencesRepository.abilityListingContributionsEnabled

    val essenceConflictCount = essenceRepository.conflicts.map { it.size }
    val awakeningStoneConflictCount = awakeningStoneRepository.conflicts.map { it.size }
    val abilityListingConflictCount = abilityListingRepository.conflicts.map { it.size }

    fun setEssenceContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setEssenceContributionsEnabled(enabled)
    }

    fun setAwakeningStoneContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setAwakeningStoneContributionsEnabled(enabled)
    }

    fun setAbilityListingContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setAbilityListingContributionsEnabled(enabled)
    }
}
