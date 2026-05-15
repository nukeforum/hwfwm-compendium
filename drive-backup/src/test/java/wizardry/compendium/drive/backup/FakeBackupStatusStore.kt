package wizardry.compendium.drive.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class FakeBackupStatusStore : BackupStatusStoreApi {
    val state = MutableStateFlow(BackupStatus())
    val successAt = mutableListOf<Instant>()
    val errorAt = mutableListOf<Pair<String, Instant>>()
    val restoreAt = mutableListOf<Instant>()
    val cleared = mutableListOf<Unit>()

    override fun statusFlow(): Flow<BackupStatus> = state
    override suspend fun recordSuccess(at: Instant) {
        successAt += at
        state.value = state.value.copy(lastSuccessAt = at, lastError = null, lastErrorAt = null)
    }
    override suspend fun recordError(message: String, at: Instant) {
        errorAt += message to at
        state.value = state.value.copy(lastError = message, lastErrorAt = at)
    }
    override suspend fun recordRestore(at: Instant) {
        restoreAt += at
        state.value = state.value.copy(lastRestoreAt = at)
    }
    override suspend fun clear() {
        cleared += Unit
        state.value = BackupStatus()
    }
}
