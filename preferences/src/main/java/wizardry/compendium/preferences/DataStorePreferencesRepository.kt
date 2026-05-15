package wizardry.compendium.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
import wizardry.compendium.preferences.ThemeMode
import javax.inject.Inject
import javax.inject.Singleton

private val LEGACY_ESSENCE_CONTRIBUTIONS_KEY = booleanPreferencesKey("contributions_enabled")
private val ESSENCE_CONTRIBUTIONS_KEY = booleanPreferencesKey("essence_contributions_enabled")

private val RenameEssenceContributionsKey = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        LEGACY_ESSENCE_CONTRIBUTIONS_KEY in currentData

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = currentData[LEGACY_ESSENCE_CONTRIBUTIONS_KEY] ?: return currentData
        return currentData.toMutablePreferences().apply {
            if (ESSENCE_CONTRIBUTIONS_KEY !in this) set(ESSENCE_CONTRIBUTIONS_KEY, legacy)
            remove(LEGACY_ESSENCE_CONTRIBUTIONS_KEY)
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

private val Context.dataStore by preferencesDataStore(
    name = "compendium_prefs",
    produceMigrations = { listOf(RenameEssenceContributionsKey) },
)

@Singleton
class DataStorePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PreferencesRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val essenceContributionsKey = ESSENCE_CONTRIBUTIONS_KEY
    private val awakeningStoneContributionsKey = booleanPreferencesKey("awakening_stone_contributions_enabled")
    private val abilityListingContributionsKey = booleanPreferencesKey("ability_listing_contributions_enabled")
    private val statusEffectContributionsKey = booleanPreferencesKey("status_effect_contributions_enabled")
    private val essencesAsAwakeningStonesKey = booleanPreferencesKey("essences_as_awakening_stones_enabled")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorEnabledKey = booleanPreferencesKey("dynamic_color_enabled")
    private val driveBackupEnabledKey = booleanPreferencesKey("drive_backup_enabled")
    private val driveBackupAccountEmailKey = stringPreferencesKey("drive_backup_account_email")

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

    private val driveBackupEnabledState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[driveBackupEnabledKey] ?: false }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = false)

    private val driveBackupAccountEmailState: StateFlow<String?> = context.dataStore.data
        .map { prefs -> prefs[driveBackupAccountEmailKey] }
        .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = null)

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

    override val themeMode: Flow<ThemeMode>
        get() = themeModeState

    override val isCurrentThemeMode: ThemeMode
        get() = themeModeState.value

    override val dynamicColorEnabled: Flow<Boolean>
        get() = dynamicColorEnabledState

    override val isDynamicColorEnabled: Boolean
        get() = dynamicColorEnabledState.value

    override fun setEssenceContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[essenceContributionsKey] = enabled
            }
        }
    }

    override fun setAwakeningStoneContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[awakeningStoneContributionsKey] = enabled
            }
        }
    }

    override fun setAbilityListingContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[abilityListingContributionsKey] = enabled
            }
        }
    }

    override fun setStatusEffectContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[statusEffectContributionsKey] = enabled
            }
        }
    }

    override fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[essencesAsAwakeningStonesKey] = enabled
            }
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[themeModeKey] = mode.storedValue
            }
        }
    }

    override fun setDynamicColorEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[dynamicColorEnabledKey] = enabled
            }
        }
    }

    override val driveBackupEnabled: Flow<Boolean> get() = driveBackupEnabledState
    override val isDriveBackupEnabled: Boolean get() = driveBackupEnabledState.value
    override fun setDriveBackupEnabled(enabled: Boolean) {
        scope.launch { context.dataStore.edit { prefs -> prefs[driveBackupEnabledKey] = enabled } }
    }

    override val driveBackupAccountEmail: Flow<String?> get() = driveBackupAccountEmailState
    override val currentDriveBackupAccountEmail: String? get() = driveBackupAccountEmailState.value
    override fun setDriveBackupAccountEmail(email: String?) {
        scope.launch {
            context.dataStore.edit { prefs ->
                if (email == null) prefs.remove(driveBackupAccountEmailKey)
                else prefs[driveBackupAccountEmailKey] = email
            }
        }
    }
}
