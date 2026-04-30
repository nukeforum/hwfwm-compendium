package wizardry.compendium.essences

sealed interface ContributionResult {
    data object Success : ContributionResult
    data class Failure(val message: String) : ContributionResult
}
