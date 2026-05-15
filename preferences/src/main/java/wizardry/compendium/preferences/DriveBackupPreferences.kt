package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow

interface DriveBackupPreferences {
    val driveBackupEnabled: Flow<Boolean>
    val isDriveBackupEnabled: Boolean
    fun setDriveBackupEnabled(enabled: Boolean)

    val driveBackupAccountEmail: Flow<String?>
    val currentDriveBackupAccountEmail: String?
    fun setDriveBackupAccountEmail(email: String?)
}
