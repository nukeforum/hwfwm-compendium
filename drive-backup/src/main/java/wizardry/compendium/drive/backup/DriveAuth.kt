package wizardry.compendium.drive.backup

import android.content.Context
import kotlinx.coroutines.flow.Flow

interface DriveAuth {
    val currentAccount: Flow<AuthAccount?>

    suspend fun signIn(
        activityContext: Context,
        resolver: ResolutionResolver,
    ): SignInResult

    suspend fun signOut()

    /** Returns a valid OAuth access token, refreshing transparently if needed. */
    suspend fun getValidAccessToken(): TokenResult

    sealed interface SignInResult {
        data class Success(val account: AuthAccount) : SignInResult
        data object Canceled : SignInResult
        data class Failed(val message: String) : SignInResult
    }

    sealed interface TokenResult {
        data class Success(val accessToken: String) : TokenResult
        data object NotSignedIn : TokenResult
        data class ReAuthRequired(val message: String) : TokenResult
        data class TransientFailure(val message: String) : TokenResult
    }
}
