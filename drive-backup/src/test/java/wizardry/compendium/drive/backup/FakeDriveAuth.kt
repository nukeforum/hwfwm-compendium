package wizardry.compendium.drive.backup

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDriveAuth(
    initialAccount: AuthAccount? = null,
) : DriveAuth {
    private val _current = MutableStateFlow(initialAccount)
    override val currentAccount: Flow<AuthAccount?> = _current

    var signInResult: DriveAuth.SignInResult =
        if (initialAccount != null) DriveAuth.SignInResult.Success(initialAccount)
        else DriveAuth.SignInResult.Success(AuthAccount("test@example.com"))
    var tokenResult: DriveAuth.TokenResult = DriveAuth.TokenResult.Success("fake-token")

    var signInCalls = 0
    var signOutCalls = 0
    var tokenCalls = 0

    override suspend fun signIn(activityContext: Context): DriveAuth.SignInResult {
        signInCalls++
        val r = signInResult
        if (r is DriveAuth.SignInResult.Success) _current.value = r.account
        return r
    }

    override suspend fun signOut() {
        signOutCalls++
        _current.value = null
    }

    override suspend fun getValidAccessToken(): DriveAuth.TokenResult {
        tokenCalls++
        return tokenResult
    }
}
