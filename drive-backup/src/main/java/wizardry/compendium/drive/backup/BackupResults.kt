package wizardry.compendium.drive.backup

sealed interface EnableResult {
    data class Success(val restored: Boolean) : EnableResult
    data object Canceled : EnableResult
    data class Failed(val message: String) : EnableResult
}

sealed interface BackupNowResult {
    data object Success : BackupNowResult
    data class TransientFailure(val message: String) : BackupNowResult
    data class FatalFailure(val message: String) : BackupNowResult
}

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object Nothing : RestoreResult
    data class UnsupportedVersion(val version: Int) : RestoreResult
    data class TransientFailure(val message: String) : RestoreResult
    data class FatalFailure(val message: String) : RestoreResult
}
