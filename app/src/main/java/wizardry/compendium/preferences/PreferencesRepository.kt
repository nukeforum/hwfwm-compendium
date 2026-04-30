package wizardry.compendium.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import wizardry.compendium.essences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.essences.EssenceContributionsToggleFlow
import wizardry.compendium.persistence.AwakeningStoneContributionsToggle
import wizardry.compendium.persistence.EssenceContributionsToggle
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
    AwakeningStoneContributionsToggleFlow {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val essenceContributionsKey = booleanPreferencesKey("contributions_enabled")
    private val awakeningStoneContributionsKey = booleanPreferencesKey("awakening_stone_contributions_enabled")

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

    override val isEssenceContributionsEnabled: Boolean
        get() = essenceContributionsState.value

    override val essenceContributionsEnabled: Flow<Boolean>
        get() = essenceContributionsState

    override val isAwakeningStoneContributionsEnabled: Boolean
        get() = awakeningStoneContributionsState.value

    override val awakeningStoneContributionsEnabled: Flow<Boolean>
        get() = awakeningStoneContributionsState

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
}
