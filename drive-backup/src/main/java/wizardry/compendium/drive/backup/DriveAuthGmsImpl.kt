package wizardry.compendium.drive.backup

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import wizardry.compendium.preferences.PreferencesRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveAuthGmsImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: PreferencesRepository,
    private val tokenCache: TokenCacheApi,
) : DriveAuth {

    private val _accountState = MutableStateFlow(
        prefs.currentDriveBackupAccountEmail?.let(::AuthAccount),
    )
    override val currentAccount: Flow<AuthAccount?> = _accountState

    override suspend fun signIn(activityContext: Context): DriveAuth.SignInResult {
        val activity = activityContext.findActivity()
            ?: return DriveAuth.SignInResult.Failed("Activity context required")

        // Step 1: account picker via Credential Manager.
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(SERVER_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val email = try {
            val response = credentialManager.getCredential(activityContext, request)
            val cred = response.credential
            val google = if (cred is GoogleIdTokenCredential) cred
            else GoogleIdTokenCredential.createFrom(cred.data)
            google.id
        } catch (e: GetCredentialCancellationException) {
            return DriveAuth.SignInResult.Canceled
        } catch (e: GetCredentialException) {
            return DriveAuth.SignInResult.Failed(e.message ?: "Sign-in failed")
        }

        // Step 2: scope grant via Authorization API.
        val authorizationRequest = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val authClient = Identity.getAuthorizationClient(activity)
        val authResult = try {
            authClient.authorize(authorizationRequest).await()
        } catch (e: Exception) {
            return DriveAuth.SignInResult.Failed(e.message ?: "Authorization failed")
        }
        if (authResult.hasResolution()) {
            // TODO: Plumb an ActivityResultLauncher<IntentSenderRequest> through
            //  DriveAuth.signIn so the UI can launch this PendingIntent and
            //  await the result. Until then, first-time sign-in cannot
            //  complete the scope grant. See:
            //  https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult#getPendingIntent()
            return DriveAuth.SignInResult.Failed(
                "Google needs your permission to access app data. " +
                    "Open Settings on your device and grant the permission, then try again."
            )
        }

        val accessToken = authResult.accessToken
            ?: return DriveAuth.SignInResult.Failed("Authorization succeeded but no access token returned")
        // Drive access tokens live ~1h. Cache with a small safety margin.
        tokenCache.write(accessToken, Instant.now().plusSeconds(TOKEN_TTL_SECONDS - 60))

        val newAccount = AuthAccount(email)
        _accountState.value = newAccount
        return DriveAuth.SignInResult.Success(newAccount)
    }

    override suspend fun signOut() {
        tokenCache.clear()
        _accountState.value = null
    }

    override suspend fun getValidAccessToken(): DriveAuth.TokenResult {
        if (_accountState.value == null) return DriveAuth.TokenResult.NotSignedIn

        val cached = tokenCache.read()
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return DriveAuth.TokenResult.Success(cached.accessToken)
        }

        // Try a silent re-authorize. AuthorizationClient.authorize() with an
        // application context returns a result without UI when the scope is
        // already granted; if it requires a resolution we surface ReAuthRequired.
        return try {
            val authClient = Identity.getAuthorizationClient(appContext)
            val authorizationRequest = AuthorizationRequest.Builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val result = authClient.authorize(authorizationRequest).await()
            if (result.hasResolution()) {
                DriveAuth.TokenResult.ReAuthRequired("Drive scope no longer granted. Re-enable in Settings.")
            } else {
                val token = result.accessToken
                    ?: return DriveAuth.TokenResult.ReAuthRequired("No access token returned")
                tokenCache.write(token, Instant.now().plusSeconds(TOKEN_TTL_SECONDS - 60))
                DriveAuth.TokenResult.Success(token)
            }
        } catch (e: Exception) {
            DriveAuth.TokenResult.TransientFailure(e.message ?: "Authorization refresh failed")
        }
    }

    companion object {
        private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val TOKEN_TTL_SECONDS = 3600L
        // Replace with the OAuth web client ID created in Google Cloud Console.
        // See RELEASE.md.
        const val SERVER_CLIENT_ID = "REPLACE_WITH_OAUTH_CLIENT_ID"
    }
}
