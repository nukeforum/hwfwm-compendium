package wizardry.compendium.drive.backup

import java.time.Instant

data class BackupStatus(
    val lastSuccessAt: Instant? = null,
    val lastError: String? = null,
    val lastErrorAt: Instant? = null,
    val lastRestoreAt: Instant? = null,
)
