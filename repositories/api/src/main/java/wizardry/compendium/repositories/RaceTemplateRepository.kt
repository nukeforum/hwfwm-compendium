package wizardry.compendium.repositories

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.domain.model.RaceTemplate

/**
 * Race templates come from two sources: the canonical seed (the book races —
 * Human, Elf, Celestine, … — loaded from `races.csv` like the other canonical
 * entities) and user contributions. Canonical templates are read-only; only
 * contributions can be saved or deleted, and a contribution may not reuse a
 * canonical race's name. The shape otherwise mirrors [CharacterBuildRepository]:
 * a live [raceTemplates] flow plus point reads and contribution writes.
 */
interface RaceTemplateRepository {
    val raceTemplates: Flow<List<RaceTemplate>>

    suspend fun getRaceTemplates(): List<RaceTemplate>

    suspend fun getRaceTemplate(name: String): RaceTemplate?

    /** True when [name] is a user contribution (and therefore editable). */
    suspend fun isContribution(name: String): Boolean

    suspend fun saveRaceTemplateContribution(template: RaceTemplate): ContributionResult

    suspend fun deleteContribution(name: String): ContributionResult
}
