package wizardry.compendium.drive.backup

interface DriveAppFolderClient {
    /** Returns the metadata of the existing backup file, or `null` if none exists. */
    suspend fun getMetadata(accessToken: String): DriveResult<RemoteFile?>

    /** Creates or updates the single backup file. Returns the resulting metadata. */
    suspend fun uploadOrReplace(accessToken: String, bytes: ByteArray): DriveResult<RemoteFile>

    /** Downloads the latest backup payload, or returns `null` if no file exists. */
    suspend fun downloadLatest(accessToken: String): DriveResult<ByteArray?>
}

sealed interface DriveResult<out T> {
    data class Success<T>(val value: T) : DriveResult<T>
    data class AuthRequired(val message: String) : DriveResult<Nothing>
    data class QuotaExceeded(val message: String) : DriveResult<Nothing>
    data class Transient(val message: String) : DriveResult<Nothing>
    data class Fatal(val message: String) : DriveResult<Nothing>
}
