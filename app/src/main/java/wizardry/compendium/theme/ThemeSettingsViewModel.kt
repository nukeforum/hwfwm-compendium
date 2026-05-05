package wizardry.compendium.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.ui.theme.ThemeMode
import javax.inject.Inject

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesRepository.isCurrentThemeMode,
    )

    val dynamicColorEnabled: StateFlow<Boolean> = preferencesRepository.dynamicColorEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesRepository.isDynamicColorEnabled,
    )
}
