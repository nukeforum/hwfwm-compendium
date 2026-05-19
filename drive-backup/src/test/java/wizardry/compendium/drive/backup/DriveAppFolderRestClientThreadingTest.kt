package wizardry.compendium.drive.backup

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Regression test for the NetworkOnMainThreadException that crashed the
 * "enable Drive backup" toggle: BackupCoordinator.enable() → maybeAutoRestore()
 * → drive.downloadLatest() ran the blocking OkHttp .execute() on whatever
 * dispatcher the caller (viewModelScope.launch, i.e. Main) provided. Suspend
 * functions that perform blocking I/O must switch off Main themselves.
 */
class DriveAppFolderRestClientThreadingTest {

    @Test
    fun `execute does not run blocking call on caller thread`() = runTest {
        val observedThread = AtomicReference<Thread>()
        val client = OkHttpClient.Builder()
            .addInterceptor(captureThread(observedThread, """{"files":[]}"""))
            .build()
        val rest = DriveAppFolderRestClient(client)

        val callerThread = Thread.currentThread()
        rest.getMetadata("token")

        assertNotNull("Interceptor should have run", observedThread.get())
        assertNotEquals(
            "OkHttp execute must run off the caller's dispatcher",
            callerThread,
            observedThread.get(),
        )
    }

    @Test
    fun `executeBinary does not run blocking call on caller thread`() = runTest {
        val metadataThread = AtomicReference<Thread>()
        val downloadThread = AtomicReference<Thread>()
        var first = true
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                if (first) {
                    first = false
                    metadataThread.set(Thread.currentThread())
                    jsonResponse(chain, """{"files":[{"id":"abc","size":"3","modifiedTime":"2026-01-01T00:00:00Z"}]}""")
                } else {
                    downloadThread.set(Thread.currentThread())
                    binaryResponse(chain, byteArrayOf(1, 2, 3))
                }
            })
            .build()
        val rest = DriveAppFolderRestClient(client)

        val callerThread = Thread.currentThread()
        rest.downloadLatest("token")

        assertNotNull("Metadata interceptor should have run", metadataThread.get())
        assertNotNull("Download interceptor should have run", downloadThread.get())
        assertNotEquals(callerThread, downloadThread.get())
    }

    private fun captureThread(sink: AtomicReference<Thread>, body: String): Interceptor =
        Interceptor { chain ->
            sink.set(Thread.currentThread())
            jsonResponse(chain, body)
        }

    private fun jsonResponse(chain: Interceptor.Chain, body: String): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private fun binaryResponse(chain: Interceptor.Chain, bytes: ByteArray): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
            .build()
}
