package com.mobile.wizardry.compendium.essences.dataloader

import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity

class EssenceCsvLoader(
    private val source: FileStreamSource,
) : EssenceDataLoader {
    override suspend fun loadEssenceData(): List<Essence> {
        val baseEssences = getBaseEssences()

        val confluenceEssences = getConfluenceEssences(baseEssences)

        return baseEssences.values + confluenceEssences.values
    }

    private fun getConfluenceEssences(baseEssences: Map<String, Essence>): Map<String, Essence> {
        return source.getInputStreamFor(CONFLUENCE_FILE_NAME).use {
            val confluences = mutableMapOf<String, Essence>()
            it.reader().readLines()
                .map { entry ->
                    entry.split(',')
                        .let { (essenceName1, essenceName2, essenceName3, confluenceName) ->
                            Essence.of(
                                confluenceName,
                                setOf(
                                    baseEssences[essenceName1]!!,
                                    baseEssences[essenceName2]!!,
                                    baseEssences[essenceName3]!!
                                )
                            )
                        }
                }
                .associateTo(confluences) { confluence ->
                    confluences[confluence.name]
                        ?.merge(confluence)
                        ?.let { resolvedEssence -> Pair(resolvedEssence.name, resolvedEssence) }
                        ?: Pair(confluence.name, confluence)
                }
        }
    }

    private fun getBaseEssences(): Map<String, Essence> {
        return source.getInputStreamFor(ESSENCE_FILE_NAME).use {
            it.reader().readLines()
                .map { entry ->
                    entry.split(',').let { (name, rarity) ->
                        Essence.of(
                            name,
                            "none",
                            Rarity.valueOf(rarity)
                        )
                    }
                }
                .associateBy { essence -> essence.name }
        }
    }

    companion object {
        private const val ESSENCE_FILE_NAME = "essences.csv"
        private const val CONFLUENCE_FILE_NAME = "combinations.csv"
    }
}

private fun Essence.merge(essence: Essence): Essence {
    if (name != essence.name)
        throw IllegalArgumentException("Cannot merge $name and ${essence.name}, they are not the same essence")

    if (confluences.isEmpty()) return this

    return essence.copy(confluences = essence.confluences + confluences)
}
