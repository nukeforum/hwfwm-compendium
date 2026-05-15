package wizardry.compendium.drive.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant

class BackupStatusStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): BackupStatusStore {
        val file = tempFolder.newFile("backup-status.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { file })
        return BackupStatusStore(dataStore)
    }

    @Test
    fun `defaults are empty`() = runTest {
        val store = newStore()
        val status = store.statusFlow().first()
        assertNull(status.lastSuccessAt)
        assertNull(status.lastError)
        assertNull(status.lastErrorAt)
        assertNull(status.lastRestoreAt)
    }

    @Test
    fun `recordSuccess persists timestamp and clears error`() = runTest {
        val store = newStore()
        store.recordError("first failure", Instant.ofEpochSecond(10))
        store.recordSuccess(Instant.ofEpochSecond(100))
        val status = store.statusFlow().first()
        assertEquals(Instant.ofEpochSecond(100), status.lastSuccessAt)
        assertNull(status.lastError)
        assertNull(status.lastErrorAt)
    }

    @Test
    fun `recordError persists message and timestamp without clearing prior success`() = runTest {
        val store = newStore()
        store.recordSuccess(Instant.ofEpochSecond(100))
        store.recordError("network", Instant.ofEpochSecond(200))
        val status = store.statusFlow().first()
        assertEquals(Instant.ofEpochSecond(100), status.lastSuccessAt)
        assertEquals("network", status.lastError)
        assertEquals(Instant.ofEpochSecond(200), status.lastErrorAt)
    }

    @Test
    fun `recordRestore persists timestamp without clearing success`() = runTest {
        val store = newStore()
        store.recordSuccess(Instant.ofEpochSecond(100))
        store.recordRestore(Instant.ofEpochSecond(150))
        val status = store.statusFlow().first()
        assertEquals(Instant.ofEpochSecond(150), status.lastRestoreAt)
        assertEquals(Instant.ofEpochSecond(100), status.lastSuccessAt)
    }
}
