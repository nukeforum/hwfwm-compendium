package wizardry.compendium.drive.backup

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerBackupScheduler @Inject constructor(
    private val workManager: WorkManager,
) : BackupScheduler {

    override fun schedulePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UniqueName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancelPeriodic() {
        workManager.cancelUniqueWork(UniqueName)
    }

    companion object {
        const val UniqueName = "drive-backup-periodic"
    }
}
