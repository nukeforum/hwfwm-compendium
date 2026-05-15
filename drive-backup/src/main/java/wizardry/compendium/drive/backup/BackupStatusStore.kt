package wizardry.compendium.drive.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface BackupStatusStoreApi {
    fun statusFlow(): Flow<BackupStatus>
    suspend fun recordSuccess(at: Instant)
    suspend fun recordError(message: String, at: Instant)
    suspend fun recordRestore(at: Instant)
    suspend fun clear()
}

@Singleton
class BackupStatusStore @Inject constructor(
    @BackupStatusDataStore private val dataStore: DataStore<Preferences>,
) : BackupStatusStoreApi {
    private val lastSuccessKey = longPreferencesKey("last_success_epoch_seconds")
    private val lastErrorKey = stringPreferencesKey("last_error")
    private val lastErrorAtKey = longPreferencesKey("last_error_at_epoch_seconds")
    private val lastRestoreKey = longPreferencesKey("last_restore_epoch_seconds")

    override fun statusFlow(): Flow<BackupStatus> = dataStore.data.map { prefs ->
        BackupStatus(
            lastSuccessAt = prefs[lastSuccessKey]?.let(Instant::ofEpochSecond),
            lastError = prefs[lastErrorKey],
            lastErrorAt = prefs[lastErrorAtKey]?.let(Instant::ofEpochSecond),
            lastRestoreAt = prefs[lastRestoreKey]?.let(Instant::ofEpochSecond),
        )
    }

    override suspend fun recordSuccess(at: Instant) {
        dataStore.edit { prefs ->
            prefs[lastSuccessKey] = at.epochSecond
            prefs.remove(lastErrorKey)
            prefs.remove(lastErrorAtKey)
        }
    }

    override suspend fun recordError(message: String, at: Instant) {
        dataStore.edit { prefs ->
            prefs[lastErrorKey] = message
            prefs[lastErrorAtKey] = at.epochSecond
        }
    }

    override suspend fun recordRestore(at: Instant) {
        dataStore.edit { prefs ->
            prefs[lastRestoreKey] = at.epochSecond
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
