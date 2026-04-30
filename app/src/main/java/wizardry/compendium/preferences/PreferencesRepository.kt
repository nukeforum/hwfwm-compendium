package wizardry.compendium.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import wizardry.compendium.essences.ContributionsToggleFlow
import wizardry.compendium.persistence.ContributionsToggle
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
) : ContributionsToggle, ContributionsToggleFlow {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contributionsKey = booleanPreferencesKey("contributions_enabled")

    private val contributionsEnabledState: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[contributionsKey] ?: false }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    override val isContributionsEnabled: Boolean
        get() = contributionsEnabledState.value

    override val contributionsEnabled: Flow<Boolean>
        get() = contributionsEnabledState

    fun setContributionsEnabled(enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[contributionsKey] = enabled
            }
        }
    }
}
