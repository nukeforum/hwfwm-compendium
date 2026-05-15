package wizardry.compendium.drive.backup

import android.content.Context
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupCoordinatorTest {

    private lateinit var prefs: FakePreferencesRepository
    private lateinit var wire: FakeWirePayloadCodec
    private lateinit var snapshotCodec: PreferencesSnapshotCodec
    private lateinit var auth: FakeDriveAuth
    private lateinit var drive: FakeDriveAppFolderClient
    private lateinit var status: FakeBackupStatusStore
    private lateinit var scheduler: RecordingScheduler
    private lateinit var coordinator: BackupCoordinator
    private val fixedClock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)
    private val activityContext: Context = mock()

    @Before
    fun setUp() {
        prefs = FakePreferencesRepository()
        wire = FakeWirePayloadCodec()
        snapshotCodec = PreferencesSnapshotCodec(prefs)
        auth = FakeDriveAuth()
        drive = FakeDriveAppFolderClient()
        status = FakeBackupStatusStore()
        scheduler = RecordingScheduler()
        coordinator = newCoordinator()
    }

    @Test
    fun `enable when local empty and remote exists triggers restore`() = runTest {
        wire.hasLocal = false
        drive = FakeDriveAppFolderClient(initialRemote = sampleBundle())
        coordinator = newCoordinator()

        val result = coordinator.enable(activityContext)

        assertTrue(result is EnableResult.Success && result.restored)
        assertEquals(1, wire.importCalls)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `enable when local has data and remote exists does not restore`() = runTest {
        wire.hasLocal = true
        drive = FakeDriveAppFolderClient(initialRemote = sampleBundle())
        coordinator = newCoordinator()

        val result = coordinator.enable(activityContext)

        assertTrue(result is EnableResult.Success && !(result as EnableResult.Success).restored)
        assertEquals(0, wire.importCalls)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `enable when no remote schedules periodic and fires baseline backup`() = runTest {
        wire.hasLocal = true

        coordinator.enable(activityContext)

        assertTrue(scheduler.scheduled)
        // Baseline backup runs fire-and-forget in ioScope; UnconfinedTestDispatcher runs it inline.
        assertEquals(1, drive.uploadCalls)
    }

    @Test
    fun `enable canceled by user leaves flag false and does not schedule`() = runTest {
        auth.signInResult = DriveAuth.SignInResult.Canceled

        val result = coordinator.enable(activityContext)

        assertEquals(EnableResult.Canceled, result)
        assertEquals(false, prefs.isDriveBackupEnabled)
        assertEquals(false, scheduler.scheduled)
    }

    @Test
    fun `backupNow with 401 then refresh-success retries upload`() = runTest {
        // First upload returns AuthRequired; we then null nextUploadResult so the
        // retry path uses the default Success behavior.
        drive.nextUploadResult = DriveResult.AuthRequired("expired")
        auth.tokenResult = DriveAuth.TokenResult.Success("fresh-token")

        // The coordinator's retryAfterRefresh path calls uploadOrReplace again.
        // To make that retry succeed, we install a small hook: clear nextUploadResult
        // after the first upload by overriding the fake (or by relying on the fake's
        // semantics — nextUploadResult is one-shot in this test design).
        // The FakeDriveAppFolderClient as defined uses nextUploadResult for EVERY call
        // until null. To get one-shot behavior we extend the fake here.

        // Workaround: build a custom drive client inline for this test.
        val oneShotDrive = object : DriveAppFolderClient {
            var calls = 0
            override suspend fun getMetadata(accessToken: String) =
                DriveResult.Success<RemoteFile?>(null)
            override suspend fun uploadOrReplace(accessToken: String, bytes: ByteArray): DriveResult<RemoteFile> {
                calls++
                return if (calls == 1) DriveResult.AuthRequired("expired")
                else DriveResult.Success(
                    RemoteFile("fake-id", bytes.size.toLong(), Instant.ofEpochSecond(123))
                )
            }
            override suspend fun downloadLatest(accessToken: String) = DriveResult.Success<ByteArray?>(null)
        }
        drive = FakeDriveAppFolderClient()  // reset for other tests' isolation (unused below)
        coordinator = BackupCoordinator(
            auth = auth,
            drive = oneShotDrive,
            wireCodec = wire,
            snapshotCodec = snapshotCodec,
            statusStore = status,
            prefs = prefs,
            scheduler = scheduler,
            clock = fixedClock,
            ioScope = TestScope(UnconfinedTestDispatcher()),
        )

        val result = coordinator.backupNow()

        assertEquals(BackupNowResult.Success, result)
        assertEquals(2, oneShotDrive.calls)
    }

    @Test
    fun `backupNow with refresh failure records re-auth-required error`() = runTest {
        // First upload returns AuthRequired. Refresh (next getValidAccessToken call) returns ReAuthRequired.
        var firstTokenCall = true
        val authStub = object : DriveAuth {
            override val currentAccount = kotlinx.coroutines.flow.MutableStateFlow<AuthAccount?>(AuthAccount("e"))
            override suspend fun signIn(activityContext: Context) = DriveAuth.SignInResult.Success(AuthAccount("e"))
            override suspend fun signOut() {}
            override suspend fun getValidAccessToken(): DriveAuth.TokenResult {
                if (firstTokenCall) { firstTokenCall = false; return DriveAuth.TokenResult.Success("t") }
                return DriveAuth.TokenResult.ReAuthRequired("user revoked")
            }
        }
        drive.nextUploadResult = DriveResult.AuthRequired("expired")
        coordinator = BackupCoordinator(
            auth = authStub,
            drive = drive,
            wireCodec = wire,
            snapshotCodec = snapshotCodec,
            statusStore = status,
            prefs = prefs,
            scheduler = scheduler,
            clock = fixedClock,
            ioScope = TestScope(UnconfinedTestDispatcher()),
        )

        val result = coordinator.backupNow()

        assertTrue(result is BackupNowResult.FatalFailure)
        assertEquals(1, status.errorAt.size)
        assertTrue(status.errorAt[0].first.contains("Re-sign-in", ignoreCase = true))
    }

    @Test
    fun `restoreNow with unsupported version aborts without local mutation`() = runTest {
        val bundle = """{"schemaVersion":99,"contributions":"x","preferences":{"schemaVersion":1}}"""
            .toByteArray()
        drive = FakeDriveAppFolderClient(initialRemote = bundle)
        coordinator = newCoordinator()

        val result = coordinator.restoreNow()

        assertTrue(result is RestoreResult.UnsupportedVersion)
        assertEquals(0, wire.importCalls)
    }

    @Test
    fun `restoreNow with corrupt envelope does not re-upload`() = runTest {
        val bundle = "totally not json".toByteArray()
        drive = FakeDriveAppFolderClient(initialRemote = bundle)
        coordinator = newCoordinator()

        val result = coordinator.restoreNow()

        assertTrue(result is RestoreResult.FatalFailure)
        assertEquals(0, drive.uploadCalls)
    }

    private fun sampleBundle(): ByteArray = BackupBundle(
        schemaVersion = 1,
        contributions = "envelope-text",
        preferences = PreferencesSnapshot(),
    ).encode().toByteArray()

    private fun newCoordinator() = BackupCoordinator(
        auth = auth,
        drive = drive,
        wireCodec = wire,
        snapshotCodec = snapshotCodec,
        statusStore = status,
        prefs = prefs,
        scheduler = scheduler,
        clock = fixedClock,
        ioScope = TestScope(UnconfinedTestDispatcher()),
    )

    private class RecordingScheduler : BackupScheduler {
        var scheduled = false
        var canceled = false
        override fun schedulePeriodic() { scheduled = true }
        override fun cancelPeriodic() { canceled = true; scheduled = false }
    }
}
