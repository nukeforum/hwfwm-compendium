package wizardry.compendium.drive.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import wizardry.compendium.preferences.PreferencesRepository

@HiltWorker
class DriveBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: BackupCoordinatorApi,
    private val prefs: PreferencesRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!prefs.isDriveBackupEnabled) return Result.success()
        return when (coordinator.backupNow()) {
            BackupNowResult.Success -> Result.success()
            is BackupNowResult.TransientFailure -> Result.retry()
            is BackupNowResult.FatalFailure -> Result.failure()
        }
    }
}
