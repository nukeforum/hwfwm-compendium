package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.domain.model.RaceTemplate

/**
 * Race templates are user contributions only (there is no canonical race-template
 * content), so every template exposed here is editable. The shape mirrors
 * [CharacterBuildRepository]: a live [raceTemplates] flow plus point reads and
 * contribution writes.
 */
interface RaceTemplateRepository {
    val raceTemplates: Flow<List<RaceTemplate>>

    suspend fun getRaceTemplates(): List<RaceTemplate>

    suspend fun getRaceTemplate(name: String): RaceTemplate?

    suspend fun saveRaceTemplateContribution(template: RaceTemplate): ContributionResult

    suspend fun deleteContribution(name: String): ContributionResult
}
