package wizardry.compendium.repositories

sealed interface ContributionResult {
    data object Success : ContributionResult
    data class Failure(val message: String) : ContributionResult
}
