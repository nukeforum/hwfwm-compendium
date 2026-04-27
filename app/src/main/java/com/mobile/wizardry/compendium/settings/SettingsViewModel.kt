package com.mobile.wizardry.compendium.settings

import androidx.lifecycle.ViewModel
import com.mobile.wizardry.compendium.preferences.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val contributionsEnabled = preferencesRepository.contributionsEnabled

    fun setContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setContributionsEnabled(enabled)
    }
}
