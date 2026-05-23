package wizardry.compendium.repositories

interface IntegritySweep {
    suspend fun run(): List<IntegrityIssue>
}
