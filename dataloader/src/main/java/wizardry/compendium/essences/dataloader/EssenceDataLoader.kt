package wizardry.compendium.essences.dataloader

import wizardry.compendium.essences.model.Essence

/**
 * Defines the contract for loading [Essence] data.
 */
interface EssenceDataLoader {
    /**
     * Loads and parses all essence data.
     *
     * @return A list of all loaded [Essence]s.
     */
    suspend fun loadEssenceData(): List<Essence>
}
