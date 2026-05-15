package wizardry.compendium.drive.backup

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import wizardry.compendium.preferences.PreferencesRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface BackupCoordinatorApi {
    suspend fun enable(activityContext: Context): EnableResult
    suspend fun disable()
    suspend fun backupNow(): BackupNowResult
    suspend fun restoreNow(): RestoreResult
}

@Singleton
class BackupCoordinator @Inject constructor(
    private val auth: DriveAuth,
    private val drive: DriveAppFolderClient,
    private val wireCodec: WirePayloadCodecApi,
    private val snapshotCodec: PreferencesSnapshotCodec,
    private val statusStore: BackupStatusStoreApi,
    private val prefs: PreferencesRepository,
    private val scheduler: BackupScheduler,
    private val clock: Clock,
    @DriveBackupIoScope private val ioScope: CoroutineScope,
) : BackupCoordinatorApi {

    override suspend fun enable(activityContext: Context): EnableResult {
        val signIn = auth.signIn(activityContext)
        return when (signIn) {
            is DriveAuth.SignInResult.Canceled -> EnableResult.Canceled
            is DriveAuth.SignInResult.Failed -> EnableResult.Failed(signIn.message)
            is DriveAuth.SignInResult.Success -> {
                prefs.setDriveBackupEnabled(true)
                prefs.setDriveBackupAccountEmail(signIn.account.email)

                val restored = maybeAutoRestore()
                scheduler.schedulePeriodic()
                ioScope.launch { backupNow() }
                EnableResult.Success(restored = restored)
            }
        }
    }

    override suspend fun disable() {
        prefs.setDriveBackupEnabled(false)
        prefs.setDriveBackupAccountEmail(null)
        auth.signOut()
        scheduler.cancelPeriodic()
    }

    override suspend fun backupNow(): BackupNowResult {
        val token = when (val t = auth.getValidAccessToken()) {
            is DriveAuth.TokenResult.Success -> t.accessToken
            is DriveAuth.TokenResult.ReAuthRequired -> {
                statusStore.recordError("Re-sign-in required: ${t.message}", Instant.now(clock))
                return BackupNowResult.FatalFailure(t.message)
            }
            is DriveAuth.TokenResult.NotSignedIn -> {
                statusStore.recordError("Not signed in", Instant.now(clock))
                return BackupNowResult.FatalFailure("Not signed in")
            }
            is DriveAuth.TokenResult.TransientFailure -> {
                statusStore.recordError(t.message, Instant.now(clock))
                return BackupNowResult.TransientFailure(t.message)
            }
        }

        val bundle = BackupBundle(
            schemaVersion = BackupBundle.CURRENT_SCHEMA_VERSION,
            contributions = wireCodec.encode(),
            preferences = snapshotCodec.snapshot(),
        )

        return when (val result = drive.uploadOrReplace(token, bundle.encode().toByteArray())) {
            is DriveResult.Success -> {
                statusStore.recordSuccess(Instant.now(clock))
                BackupNowResult.Success
            }
            is DriveResult.AuthRequired -> retryAfterRefresh(bundle)
            is DriveResult.QuotaExceeded -> {
                statusStore.recordError("Drive storage full", Instant.now(clock))
                BackupNowResult.FatalFailure(result.message)
            }
            is DriveResult.Transient -> {
                statusStore.recordError(result.message, Instant.now(clock))
                BackupNowResult.TransientFailure(result.message)
            }
            is DriveResult.Fatal -> {
                statusStore.recordError(result.message, Instant.now(clock))
                BackupNowResult.FatalFailure(result.message)
            }
        }
    }

    override suspend fun restoreNow(): RestoreResult {
        val token = when (val t = auth.getValidAccessToken()) {
            is DriveAuth.TokenResult.Success -> t.accessToken
            is DriveAuth.TokenResult.ReAuthRequired -> {
                statusStore.recordError("Re-sign-in required: ${t.message}", Instant.now(clock))
                return RestoreResult.FatalFailure(t.message)
            }
            is DriveAuth.TokenResult.NotSignedIn ->
                return RestoreResult.FatalFailure("Not signed in")
            is DriveAuth.TokenResult.TransientFailure ->
                return RestoreResult.TransientFailure(t.message)
        }

        val bytes = when (val result = drive.downloadLatest(token)) {
            is DriveResult.Success -> result.value ?: return RestoreResult.Nothing
            is DriveResult.AuthRequired -> return RestoreResult.FatalFailure(result.message)
            is DriveResult.QuotaExceeded -> return RestoreResult.FatalFailure(result.message)
            is DriveResult.Transient -> return RestoreResult.TransientFailure(result.message)
            is DriveResult.Fatal -> return RestoreResult.FatalFailure(result.message)
        }

        val bundle = try {
            BackupBundle.decode(String(bytes, Charsets.UTF_8))
        } catch (e: BackupBundleUnsupportedVersionException) {
            statusStore.recordError(
                "Backup made by a newer app version — update to restore.",
                Instant.now(clock),
            )
            return RestoreResult.UnsupportedVersion(e.version)
        } catch (e: BackupBundleCorruptException) {
            statusStore.recordError("Backup file is unreadable.", Instant.now(clock))
            return RestoreResult.FatalFailure("Backup file is unreadable")
        }

        val outcome = wireCodec.decodeAndImport(bundle.contributions)
        if (outcome is WireImportOutcome.Failed) {
            statusStore.recordError(outcome.reason, Instant.now(clock))
            return RestoreResult.FatalFailure(outcome.reason)
        }

        try {
            snapshotCodec.apply(bundle.preferences)
        } catch (_: Exception) {
            // Best-effort. Contributions matter most.
        }

        statusStore.recordRestore(Instant.now(clock))
        return RestoreResult.Success
    }

    private suspend fun maybeAutoRestore(): Boolean {
        if (wireCodec.hasLocalContributions()) return false
        val token = when (val t = auth.getValidAccessToken()) {
            is DriveAuth.TokenResult.Success -> t.accessToken
            else -> return false
        }
        @Suppress("UNUSED_VARIABLE")
        val meta = when (val m = drive.getMetadata(token)) {
            is DriveResult.Success -> m.value ?: return false
            else -> return false
        }
        return restoreNow() is RestoreResult.Success
    }

    private suspend fun retryAfterRefresh(bundle: BackupBundle): BackupNowResult {
        return when (val t = auth.getValidAccessToken()) {
            is DriveAuth.TokenResult.Success -> {
                when (val r = drive.uploadOrReplace(t.accessToken, bundle.encode().toByteArray())) {
                    is DriveResult.Success -> {
                        statusStore.recordSuccess(Instant.now(clock))
                        BackupNowResult.Success
                    }
                    is DriveResult.AuthRequired -> {
                        statusStore.recordError(r.message, Instant.now(clock))
                        BackupNowResult.FatalFailure(r.message)
                    }
                    is DriveResult.Fatal -> {
                        statusStore.recordError(r.message, Instant.now(clock))
                        BackupNowResult.FatalFailure(r.message)
                    }
                    is DriveResult.QuotaExceeded -> {
                        statusStore.recordError(r.message, Instant.now(clock))
                        BackupNowResult.FatalFailure(r.message)
                    }
                    is DriveResult.Transient -> {
                        statusStore.recordError(r.message, Instant.now(clock))
                        BackupNowResult.TransientFailure(r.message)
                    }
                }
            }
            is DriveAuth.TokenResult.ReAuthRequired -> {
                statusStore.recordError("Re-sign-in required: ${t.message}", Instant.now(clock))
                BackupNowResult.FatalFailure(t.message)
            }
            else -> {
                statusStore.recordError("Backup failed: auth refresh failed", Instant.now(clock))
                BackupNowResult.FatalFailure("Auth refresh failed")
            }
        }
    }
}
