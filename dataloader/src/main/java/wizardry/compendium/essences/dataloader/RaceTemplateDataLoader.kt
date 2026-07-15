package wizardry.compendium.essences.dataloader

import wizardry.compendium.domain.model.RaceTemplate

/**
 * Defines the contract for loading canonical [RaceTemplate] data.
 */
interface RaceTemplateDataLoader {
    /**
     * Loads and parses all canonical race-template data.
     *
     * @return A list of all loaded [RaceTemplate]s.
     */
    suspend fun loadRaceTemplateData(): List<RaceTemplate>
}
