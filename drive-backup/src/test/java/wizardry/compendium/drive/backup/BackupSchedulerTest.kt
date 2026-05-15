package wizardry.compendium.drive.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackupSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerBackupScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerBackupScheduler(workManager)
    }

    @Test
    fun `schedulePeriodic enqueues unique periodic work`() = runTest {
        scheduler.schedulePeriodic()
        val infos = workManager
            .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UniqueName)
            .get()
        assertEquals(1, infos.size)
        assertTrue(infos[0].state == WorkInfo.State.ENQUEUED ||
            infos[0].state == WorkInfo.State.RUNNING)
    }

    @Test
    fun `re-scheduling does not duplicate work`() = runTest {
        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()
        val infos = workManager
            .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UniqueName)
            .get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `cancelPeriodic cancels the work`() = runTest {
        scheduler.schedulePeriodic()
        scheduler.cancelPeriodic()
        val infos = workManager
            .getWorkInfosForUniqueWork(WorkManagerBackupScheduler.UniqueName)
            .get()
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
