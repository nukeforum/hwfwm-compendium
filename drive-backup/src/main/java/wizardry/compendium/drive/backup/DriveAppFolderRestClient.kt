package wizardry.compendium.drive.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveAppFolderRestClient @Inject constructor(
    private val httpClient: OkHttpClient,
) : DriveAppFolderClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getMetadata(accessToken: String): DriveResult<RemoteFile?> {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder" +
            "&q=name%3D%27$FILE_NAME%27" +
            "&fields=files(id,size,modifiedTime)"
        val request = authed(accessToken, Request.Builder().url(url).get()).build()
        return execute(request) { body ->
            val list = json.decodeFromString(FileListResponse.serializer(), body)
            list.files.firstOrNull()?.let {
                RemoteFile(
                    id = it.id,
                    size = it.size?.toLongOrNull() ?: 0L,
                    modifiedAt = it.modifiedTime?.let(Instant::parse) ?: Instant.EPOCH,
                )
            }
        }
    }

    override suspend fun uploadOrReplace(
        accessToken: String,
        bytes: ByteArray,
    ): DriveResult<RemoteFile> {
        val existing = when (val r = getMetadata(accessToken)) {
            is DriveResult.Success -> r.value
            is DriveResult.AuthRequired -> return r
            is DriveResult.QuotaExceeded -> return r
            is DriveResult.Transient -> return r
            is DriveResult.Fatal -> return r
        }
        val request = if (existing == null) {
            createMultipartRequest(accessToken, bytes)
        } else {
            updateMediaRequest(accessToken, existing.id, bytes)
        }
        return execute(request) { body ->
            val parsed = json.decodeFromString(FileResource.serializer(), body)
            RemoteFile(
                id = parsed.id,
                size = bytes.size.toLong(),
                modifiedAt = parsed.modifiedTime?.let(Instant::parse) ?: Instant.now(),
            )
        }
    }

    override suspend fun downloadLatest(accessToken: String): DriveResult<ByteArray?> {
        val meta = when (val r = getMetadata(accessToken)) {
            is DriveResult.Success -> r.value ?: return DriveResult.Success(null)
            is DriveResult.AuthRequired -> return r
            is DriveResult.QuotaExceeded -> return r
            is DriveResult.Transient -> return r
            is DriveResult.Fatal -> return r
        }
        val url = "https://www.googleapis.com/drive/v3/files/${meta.id}?alt=media"
        val request = authed(accessToken, Request.Builder().url(url).get()).build()
        return executeBinary(request)
    }

    private fun createMultipartRequest(accessToken: String, bytes: ByteArray): Request {
        val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"]}"""
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.MIXED)
            .addPart(metadata.toRequestBody(JSON_TYPE))
            .addPart(bytes.toRequestBody(OCTET_TYPE))
            .build()
        return authed(
            accessToken,
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .post(multipart),
        ).build()
    }

    private fun updateMediaRequest(accessToken: String, fileId: String, bytes: ByteArray): Request {
        val body = bytes.toRequestBody(OCTET_TYPE)
        return authed(
            accessToken,
            Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .patch(body),
        ).build()
    }

    private fun authed(token: String, builder: Request.Builder): Request.Builder =
        builder.header("Authorization", "Bearer $token")

    private suspend inline fun <T> execute(
        request: Request,
        crossinline parse: (String) -> T,
    ): DriveResult<T> = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                when {
                    response.isSuccessful -> DriveResult.Success(parse(body))
                    response.code == 401 -> DriveResult.AuthRequired("Token expired (401)")
                    response.code == 403 && body.contains("storageQuotaExceeded") ->
                        DriveResult.QuotaExceeded("Drive storage full")
                    response.code == 403 -> DriveResult.Fatal("Forbidden (403). Scope may have been revoked.")
                    response.code in 500..599 -> DriveResult.Transient("Drive ${response.code}")
                    else -> DriveResult.Fatal("Drive ${response.code}: ${body.take(200)}")
                }
            }
        } catch (e: IOException) {
            DriveResult.Transient(e.message ?: "Network error")
        }
    }

    private suspend fun executeBinary(request: Request): DriveResult<ByteArray?> =
        withContext(Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> DriveResult.Success(response.body?.bytes())
                        response.code == 401 -> DriveResult.AuthRequired("Token expired (401)")
                        response.code in 500..599 -> DriveResult.Transient("Drive ${response.code}")
                        else -> DriveResult.Fatal("Drive ${response.code}")
                    }
                }
            } catch (e: IOException) {
                DriveResult.Transient(e.message ?: "Network error")
            }
        }

    @Serializable
    private data class FileListResponse(val files: List<FileResource> = emptyList())

    @Serializable
    private data class FileResource(
        val id: String,
        val size: String? = null,
        val modifiedTime: String? = null,
    )

    companion object {
        private const val FILE_NAME = "compendium-backup.v1.json"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private val OCTET_TYPE = "application/octet-stream".toMediaType()
    }
}
