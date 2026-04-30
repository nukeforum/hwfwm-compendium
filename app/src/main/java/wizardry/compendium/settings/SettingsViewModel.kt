package wizardry.compendium.settings

import androidx.lifecycle.ViewModel
import wizardry.compendium.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val essenceContributionsEnabled = preferencesRepository.essenceContributionsEnabled
    val awakeningStoneContributionsEnabled = preferencesRepository.awakeningStoneContributionsEnabled
    val abilityListingContributionsEnabled = preferencesRepository.abilityListingContributionsEnabled

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
