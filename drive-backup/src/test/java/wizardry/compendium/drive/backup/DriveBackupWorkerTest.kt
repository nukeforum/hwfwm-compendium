package wizardry.compendium.drive.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DriveBackupWorkerTest {

    private lateinit var context: Context
    private lateinit var coordinator: FakeBackupCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = FakeBackupCoordinator()
    }

    @Test
    fun `doWork returns Success when coordinator backs up`() = runTest {
        coordinator.nextBackupResult = BackupNowResult.Success
        val worker = buildWorker()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `doWork returns Retry on transient failure`() = runTest {
        coordinator.nextBackupResult = BackupNowResult.TransientFailure("network")
        val worker = buildWorker()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `doWork returns Failure on fatal failure`() = runTest {
        coordinator.nextBackupResult = BackupNowResult.FatalFailure("revoked")
        val worker = buildWorker()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `doWork returns Success without invoking coordinator when backup disabled`() = runTest {
        val worker = buildWorkerWithDisabledPrefs()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, coordinator.backupCalls)
    }

    private fun buildWorker(): DriveBackupWorker =
        TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ) = DriveBackupWorker(
                    appContext, workerParameters, coordinator,
                    FakePreferencesRepository().apply { setDriveBackupEnabled(true) },
                )
            })
            .build()

    private fun buildWorkerWithDisabledPrefs(): DriveBackupWorker =
        TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ) = DriveBackupWorker(
                    appContext, workerParameters, coordinator,
                    FakePreferencesRepository(),  // setDriveBackupEnabled defaults to false
                )
            })
            .build()
}

class FakeBackupCoordinator : BackupCoordinatorApi {
    var nextBackupResult: BackupNowResult = BackupNowResult.Success
    var backupCalls = 0
    var enableCalls = 0
    var disableCalls = 0
    var restoreCalls = 0

    override suspend fun enable(activityContext: Context, resolver: ResolutionResolver) =
        EnableResult.Success(restored = false).also { enableCalls++ }
    override suspend fun disable() { disableCalls++ }
    override suspend fun backupNow(): BackupNowResult {
        backupCalls++
        return nextBackupResult
    }
    override suspend fun restoreNow(): RestoreResult {
        restoreCalls++
        return RestoreResult.Success
    }
}
