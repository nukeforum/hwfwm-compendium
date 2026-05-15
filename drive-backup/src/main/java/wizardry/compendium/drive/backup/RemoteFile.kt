package wizardry.compendium.drive.backup

import java.time.Instant

data class RemoteFile(
    val id: String,
    val size: Long,
    val modifiedAt: Instant,
)
