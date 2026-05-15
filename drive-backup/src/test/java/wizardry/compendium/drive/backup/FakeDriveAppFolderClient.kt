package wizardry.compendium.drive.backup

import java.time.Instant

class FakeDriveAppFolderClient(
    initialRemote: ByteArray? = null,
) : DriveAppFolderClient {

    private var remoteBytes: ByteArray? = initialRemote
    private var remoteMeta: RemoteFile? = initialRemote?.let {
        RemoteFile(id = "fake-id", size = it.size.toLong(), modifiedAt = Instant.EPOCH)
    }

    var nextMetadataResult: DriveResult<RemoteFile?>? = null
    var nextUploadResult: DriveResult<RemoteFile>? = null
    var nextDownloadResult: DriveResult<ByteArray?>? = null

    var uploadCalls = 0
    var downloadCalls = 0
    var metadataCalls = 0

    override suspend fun getMetadata(accessToken: String): DriveResult<RemoteFile?> {
        metadataCalls++
        return nextMetadataResult ?: DriveResult.Success(remoteMeta)
    }

    override suspend fun uploadOrReplace(
        accessToken: String,
        bytes: ByteArray,
    ): DriveResult<RemoteFile> {
        uploadCalls++
        nextUploadResult?.let { return it }
        remoteBytes = bytes
        val meta = RemoteFile(
            id = remoteMeta?.id ?: "fake-id",
            size = bytes.size.toLong(),
            modifiedAt = Instant.now(),
        )
        remoteMeta = meta
        return DriveResult.Success(meta)
    }

    override suspend fun downloadLatest(accessToken: String): DriveResult<ByteArray?> {
        downloadCalls++
        return nextDownloadResult ?: DriveResult.Success(remoteBytes)
    }
}
