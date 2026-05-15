package wizardry.compendium.drive.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface TokenCacheApi {
    suspend fun read(): CachedToken?
    suspend fun write(token: String, expiresAt: Instant)
    suspend fun clear()
}

data class CachedToken(val accessToken: String, val expiresAt: Instant)

@Singleton
class TokenCache @Inject constructor(
    @DriveTokenDataStore private val dataStore: DataStore<Preferences>,
) : TokenCacheApi {
    private val tokenKey = stringPreferencesKey("access_token")
    private val expiryKey = longPreferencesKey("access_token_expires_epoch_seconds")

    override suspend fun read(): CachedToken? {
        val prefs = dataStore.data.first()
        val token = prefs[tokenKey] ?: return null
        val expiry = prefs[expiryKey] ?: return null
        return CachedToken(token, Instant.ofEpochSecond(expiry))
    }

    override suspend fun write(token: String, expiresAt: Instant) {
        dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[expiryKey] = expiresAt.epochSecond
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

