package wizardry.compendium.drive.backup

import android.content.IntentSender
import androidx.activity.result.ActivityResult

/**
 * Bridges a foreground PendingIntent resolution (e.g., GMS Authorization API's
 * consent UI) into a suspending boundary. The implementation in the UI layer
 * wraps an ActivityResultLauncher<IntentSenderRequest> and a CompletableDeferred.
 */
fun interface ResolutionResolver {
    suspend fun resolve(intentSender: IntentSender): ActivityResult
}

/**
 * Resolver used when no UI is available (e.g., background WorkManager paths).
 * Throws if invoked — callers should never trigger a resolution from a
 * non-UI context. The token-cache + silent re-authorize path in
 * DriveAuthGmsImpl.getValidAccessToken handles background refreshes without
 * needing a resolver.
 */
val NoUiResolutionResolver: ResolutionResolver = ResolutionResolver {
    error("Resolution intent cannot be handled without a UI context.")
}
