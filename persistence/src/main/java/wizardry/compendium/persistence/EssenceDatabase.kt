package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import javax.inject.Inject

class EssenceDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)

    fun writeAll(essences: List<Essence>) {
        db.transaction {
            db.essencesQueries.deleteAllConfluenceSets()
            db.essencesQueries.deleteAllConfluences()
            db.essencesQueries.deleteAllManifestations()

            essences.filterIsInstance<Essence.Manifestation>().forEach { manifestation ->
                db.essencesQueries.insertManifestation(
                    name = manifestation.name,
                    rarity = manifestation.rarity.name,
                    description = manifestation.description,
                    is_restricted = if (manifestation.isRestricted) 1L else 0L,
                )
            }

            essences.filterIsInstance<Essence.Confluence>().forEach { confluence ->
                db.essencesQueries.insertConfluence(
                    name = confluence.name,
                    is_restricted = if (confluence.isRestricted) 1L else 0L,
                )
                confluence.confluenceSets.forEach { set ->
                    val (e1, e2, e3) = set.set.sortedBy { it.name }
                    db.essencesQueries.insertConfluenceSet(
                        confluence_name = confluence.name,
                        essence1 = e1.name,
                        essence2 = e2.name,
                        essence3 = e3.name,
                        is_restricted = if (set.isRestricted) 1L else 0L,
                    )
                }
            }
        }
    }

    fun readAll(): List<Essence> {
        val manifestationsByName = db.essencesQueries.selectAllManifestations().executeAsList()
            .associate { row ->
                row.name to Essence.of(
                    name = row.name,
                    description = row.description,
                    rarity = Rarity.valueOf(row.rarity),
                    restricted = row.is_restricted == 1L,
                )
            }

        val confluenceSets = db.essencesQueries.selectAllConfluenceSets().executeAsList()
            .groupBy { it.confluence_name }

        val confluences = db.essencesQueries.selectAllConfluences().executeAsList()
            .mapNotNull { row ->
                val sets = confluenceSets[row.name]
                    ?.mapNotNull { setRow ->
                        val e1 = manifestationsByName[setRow.essence1] ?: return@mapNotNull null
                        val e2 = manifestationsByName[setRow.essence2] ?: return@mapNotNull null
                        val e3 = manifestationsByName[setRow.essence3] ?: return@mapNotNull null
                        ConfluenceSet(
                            set = setOf(e1, e2, e3),
                            isRestricted = setRow.is_restricted == 1L,
                        )
                    }
                    ?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                Essence.of(name = row.name, restricted = row.is_restricted == 1L, confluences = sets)
            }

        return (manifestationsByName.values + confluences).sortedBy { it.name }
    }
}
