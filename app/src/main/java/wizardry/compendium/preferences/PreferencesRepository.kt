package wizardry.compendium.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import wizardry.compendium.essences.AbilityListingContributionsToggleFlow
import wizardry.compendium.ui.theme.ThemeMode
import wizardry.compendium.essences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.essences.EssenceContributionsToggleFlow
import wizardry.compendium.essences.EssencesAsAwakeningStonesToggleFlow
import wizardry.compendium.essences.StatusEffectContributionsToggleFlow
import wizardry.compendium.persistence.AbilityListingContributionsToggle
import wizardry.compendium.persistence.AwakeningStoneContributionsToggle
import wizardry.compendium.persistence.EssenceContributionsToggle
import wizardry.compendium.persistence.EssencesAsAwakeningStonesToggle
import wizardry.compendium.persistence.StatusEffectContributionsToggle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "compendium_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EssenceContributionsToggle,
    EssenceContributionsToggleFlow,
    AwakeningStoneContributionsToggle,
    AwakeningStoneContributionsToggleFlow,
    AbilityListingContributionsToggle,
    AbilityListingContributionsToggleFlow,
    StatusEffectContributionsToggle,
    StatusEffectContributionsToggleFlow,
    EssencesAsAwakeningStonesToggle,
    EssencesAsAwakeningStonesToggleFlow {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val essenceContributionsKey = booleanPreferencesKey("contributions_enabled")
    private val awakeningStoneContributionsKey = booleanPreferencesKey("awakening_stone_contributions_enabled")
    private val abilityListingContributionsKey = booleanPreferencesKey("ability_listing_contributions_enabled")
    private val statusEffectContributionsKey = booleanPreferencesKey("status_effect_contributions_enabled")
    private val essencesAsAwakeningStonesKey = booleanPreferencesKey("essences_as_awakening_stones_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorEnabledKey = booleanPreferencesKey("dynamic_color_enabled")

    private val essenceContributionsState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[essenceContributionsKey] ?: false }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val awakeningStoneContributionsState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[awakeningStoneContributionsKey] ?: false }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val abilityListingContributionsState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[abilityListingContributionsKey] ?: false }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val statusEffectContributionsState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[statusEffectContributionsKey] ?: true }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    private val essencesAsAwakeningStonesState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[essencesAsAwakeningStonesKey] ?: false }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    private val themeModeState: StateFlow<ThemeMode> = context.dataStore.data
        .map { prefs -> ThemeMode.fromStoredValue(prefs[themeModeKey]) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = ThemeMode.System,
        )

    private val dynamicColorEnabledState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[dynamicColorEnabledKey] ?: true }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = true,
        )

    override val isEssenceContributionsEnabled: Boolean
        get() = essenceContributionsState.value

    override val essenceContributionsEnabled: Flow<Boolean>
        get() = essenceContributionsState

    override val isAwakeningStoneContributionsEnabled: Boolean
        get() = awakeningStoneContributionsState.value

    override val awakeningStoneContributionsEnabled: Flow<Boolean>
        get() = awakeningStoneContributionsState

    override val isAbilityListingContributionsEnabled: Boolean
        get() = abilityListingContributionsState.value

    override val abilityListingContributionsEnabled: Flow<Boolean>
        get() = abilityListingContributionsState

    override val isStatusEffectContributionsEnabled: Boolean
        get() = statusEffectContributionsState.value

    override val statusEffectContributionsEnabled: Flow<Boolean>
        get() = statusEffectContributionsState

    override val isEssencesAsAwakeningStonesEnabled: Boolean
        get() = essencesAsAwakeningStonesState.value

    override val essencesAsAwakeningStonesEnabled: Flow<Boolean>
        get() = essencesAsAwakeningStonesState

    val themeMode: Flow<ThemeMode>
        get() = themeModeState

    val isCurrentThemeMode: ThemeMode
        get() = themeModeState.value

    val dynamicColorEnabled: Flow<Boolean>
        get() = dynamicColorEnabledState

    val isDynamicColorEnabled: Boolean
        get() = dynamicColorEnabledState.value

    fun setEssenceContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[essenceContributionsKey] = enabled
            }
        }
    }

    fun setAwakeningStoneContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[awakeningStoneContributionsKey] = enabled
            }
        }
    }

    fun setAbilityListingContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[abilityListingContributionsKey] = enabled
            }
        }
    }

    fun setStatusEffectContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[statusEffectContributionsKey] = enabled
            }
        }
    }

    fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[essencesAsAwakeningStonesKey] = enabled
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[themeModeKey] = mode.storedValue
            }
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[dynamicColorEnabledKey] = enabled
            }
        }
    }
}
