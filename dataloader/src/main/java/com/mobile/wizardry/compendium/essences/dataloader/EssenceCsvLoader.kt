package com.mobile.wizardry.compendium.essences.dataloader

import com.mobile.wizardry.compendium.essences.model.ConfluenceSet
import com.mobile.wizardry.compendium.essences.model.Essence
import com.mobile.wizardry.compendium.essences.model.Rarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class EssenceCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : EssenceDataLoader {
    override suspend fun loadEssenceData(): List<Essence> = withContext(Dispatchers.IO) {
        val restrictedList = getRestrictedEssenceNames()
        val restrictedConfluenceSets = getRestrictedConfluenceSets()

        val baseEssences = getBaseEssences(getRestrictedEssenceNames())

        val confluenceEssences = getConfluenceEssences(baseEssences, restrictedConfluenceSets, restrictedList)

        return@withContext (baseEssences.values + confluenceEssences.values).sortedBy { it.name }
    }

    private fun getRestrictedEssenceNames(): Set<String> {
        return source.getInputStreamFor(RESTRICTED_LIST_FILE_NAME).use {
            it.reader().readLines()
                .takeLastWhile { entry -> entry.contains(RESTRICTED_LIST_DIVIDER_KEY).not() }
                .map { entry -> entry.split(',')[0] }
                .toSet()
        }
    }

    private fun getRestrictedConfluenceSets(): Set<Set<String>> {
        return source.getInputStreamFor(RESTRICTED_LIST_FILE_NAME).use {
            it.reader().readLines()
                .takeWhile { entry -> entry != RESTRICTED_LIST_DIVIDER_KEY }
                .map { entry ->
                    entry.split(',').let { (first, second, third) ->
                        setOf(first, second, third)
                    }
                }
                .toSet()
        }
    }

    private fun getBaseEssences(restrictedList: Set<String>): Map<String, Essence.Manifestation> {
        return source.getInputStreamFor(ESSENCE_FILE_NAME).use {
            it.reader().readLines()
                .map { entry ->
                    entry.split(',').let { (name, rarity) ->
                        Essence.of(
                            name = name,
                            description = "none",
                            rarity = Rarity.valueOf(rarity),
                            restricted = restrictedList.contains(name)
                        )
                    }
                }
                .associateBy { essence -> essence.name }
        }
    }

    private fun getConfluenceEssences(
        baseEssences: Map<String, Essence.Manifestation>,
        restrictedConfluenceSets: Set<Set<String>>,
        restrictedList: Set<String>,
    ): Map<String, Essence> {
        return source.getInputStreamFor(CONFLUENCE_FILE_NAME).use {
            val confluences = mutableMapOf<String, Essence.Confluence>()
            it.reader().readLines()
                .map { entry ->
                    entry.split(',')
                        .let { (essenceName1, essenceName2, essenceName3, confluenceName) ->
                            val restricted = restrictedConfluenceSets.contains(setOf(essenceName1, essenceName2, essenceName3))
                            val confluenceSet = ConfluenceSet(
                                first = baseEssences[essenceName1]!!,
                                second = baseEssences[essenceName2]!!,
                                third = baseEssences[essenceName3]!!,
                                restricted = restricted
                            )
                            Essence.of(
                                name = confluenceName,
                                restricted = restrictedList.contains(confluenceName),
                                confluenceSet
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

    companion object {
        private const val ESSENCE_FILE_NAME = "essences.csv"
        private const val CONFLUENCE_FILE_NAME = "combinations.csv"

        private const val RESTRICTED_LIST_FILE_NAME = "restricted.csv"
        private const val RESTRICTED_LIST_DIVIDER_KEY = "Always Restricted"
    }
}

private fun Essence.Confluence.merge(essence: Essence.Confluence): Essence.Confluence {
    if (name != essence.name)
        throw IllegalArgumentException("Cannot merge $name and ${essence.name}, they are not the same essence")

    if (confluenceSets.isEmpty()) return this

    return essence.copy(confluenceSets = essence.confluenceSets + confluenceSets)
}
