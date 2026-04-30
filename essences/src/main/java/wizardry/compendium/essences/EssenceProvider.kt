package wizardry.compendium.essences

import wizardry.compendium.essences.model.Essence

interface EssenceProvider {
    suspend fun getEssences(): List<Essence>
}
