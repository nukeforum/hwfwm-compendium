package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.Rarity
import javax.inject.Inject

class EssenceDatabase @Inject constructor(driver: SqlDriver) : EssenceCache {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.essencesQueries

    override val identifiedManifestations: List<IdentifiedManifestation>
        get() = q.selectAllManifestations().executeAsList().map { row ->
            IdentifiedManifestation(
                id = row.id,
                manifestation = Essence.of(
                    name = row.name,
                    description = row.description,
                    rarity = Rarity.valueOf(row.rarity),
                    restricted = row.is_restricted == 1L,
                ),
            )
        }.sortedBy { it.manifestation.name }

    override val identifiedConfluences: List<IdentifiedConfluence>
        get() = db.transactionWithResult {
            val setsByConfluenceId = q.selectAllConfluenceSets().executeAsList()
                .groupBy { it.confluence_id }
            q.selectAllConfluences().executeAsList().map { row ->
                val sets = setsByConfluenceId[row.id].orEmpty().map { setRow ->
                    RawConfluenceSet(
                        essence1Ref = setRow.essence1_ref,
                        essence2Ref = setRow.essence2_ref,
                        essence3Ref = setRow.essence3_ref,
                        isRestricted = setRow.is_restricted == 1L,
                    )
                }
                IdentifiedConfluence(
                    id = row.id,
                    name = row.name,
                    isRestricted = row.is_restricted == 1L,
                    sets = sets,
                )
            }.sortedBy { it.name }
        }

    override fun insertManifestation(manifestation: Essence.Manifestation): Long = db.transactionWithResult {
        q.insertManifestation(
            name = manifestation.name,
            rarity = manifestation.rarity.name,
            description = manifestation.description,
            is_restricted = if (manifestation.isRestricted) 1L else 0L,
        )
        q.lastInsertRowId().executeAsOne()
    }

    override fun updateManifestation(id: Long, manifestation: Essence.Manifestation) {
        q.updateManifestationFully(
            name = manifestation.name,
            rarity = manifestation.rarity.name,
            description = manifestation.description,
            is_restricted = if (manifestation.isRestricted) 1L else 0L,
            id = id,
        )
    }

    override fun deleteManifestationById(id: Long) {
        q.deleteManifestationById(id = id)
    }

    override fun findManifestationIdByName(name: String): Long? =
        q.selectManifestationId(name = name).executeAsOneOrNull()

    override fun insertConfluence(name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>): Long = db.transactionWithResult {
        q.insertConfluence(name = name, is_restricted = if (isRestricted) 1L else 0L)
        val id = q.lastInsertRowId().executeAsOne()
        sets.forEach { set ->
            q.insertConfluenceSet(
                confluence_id = id,
                essence1_ref = set.essence1Ref,
                essence2_ref = set.essence2Ref,
                essence3_ref = set.essence3Ref,
                is_restricted = if (set.isRestricted) 1L else 0L,
            )
        }
        id
    }

    override fun updateConfluence(id: Long, name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>) = db.transaction {
        q.updateConfluenceName(name = name, is_restricted = if (isRestricted) 1L else 0L, id = id)
        q.deleteConfluenceSetsForConfluence(confluence_id = id)
        sets.forEach { set ->
            q.insertConfluenceSet(
                confluence_id = id,
                essence1_ref = set.essence1Ref,
                essence2_ref = set.essence2Ref,
                essence3_ref = set.essence3Ref,
                is_restricted = if (set.isRestricted) 1L else 0L,
            )
        }
    }

    override fun deleteConfluenceById(id: Long) = db.transaction {
        q.deleteConfluenceSetsForConfluence(confluence_id = id)
        q.deleteConfluenceById(id = id)
    }

    override fun findConfluenceIdByName(name: String): Long? =
        q.selectConfluenceId(name = name).executeAsOneOrNull()

    /**
     * Returns the names of confluences whose confluence_sets include the given
     * essence ref (encoded as `canon:<name>` or `contr:<id>` by the caller).
     * Used by checkDeleteImpact to find dependent confluence sets.
     */
    fun confluenceNamesReferencingEssenceRef(ref: String): List<String> =
        q.selectConfluenceSetsReferencingEssenceRef(
            essence1_ref = ref,
            essence2_ref = ref,
            essence3_ref = ref,
        ).executeAsList()

    override fun replaceAll(essences: List<Essence>) = db.transaction {
        q.deleteAllConfluenceSets()
        q.deleteAllConfluences()
        q.deleteAllManifestations()

        // Insert manifestations first; capture their assigned ids.
        val manifestationIdsByName = mutableMapOf<String, Long>()
        essences.filterIsInstance<Essence.Manifestation>().forEach { manifestation ->
            q.insertManifestation(
                name = manifestation.name,
                rarity = manifestation.rarity.name,
                description = manifestation.description,
                is_restricted = if (manifestation.isRestricted) 1L else 0L,
            )
            manifestationIdsByName[manifestation.name] = q.lastInsertRowId().executeAsOne()
        }

        // Insert confluences; for each confluence_set, encode the three essence members
        // as contr:<id> if the member's name is in the just-inserted manifestation table,
        // otherwise canon:<name>.
        essences.filterIsInstance<Essence.Confluence>().forEach { confluence ->
            q.insertConfluence(
                name = confluence.name,
                is_restricted = if (confluence.isRestricted) 1L else 0L,
            )
            val confluenceId = q.lastInsertRowId().executeAsOne()
            confluence.confluenceSets.forEach { set ->
                val (e1, e2, e3) = set.set.sortedBy { it.name }
                q.insertConfluenceSet(
                    confluence_id = confluenceId,
                    essence1_ref = encodeMemberRef(e1.name, manifestationIdsByName),
                    essence2_ref = encodeMemberRef(e2.name, manifestationIdsByName),
                    essence3_ref = encodeMemberRef(e3.name, manifestationIdsByName),
                    is_restricted = if (set.isRestricted) 1L else 0L,
                )
            }
        }
    }

    private fun encodeMemberRef(name: String, manifestationIds: Map<String, Long>): String =
        // Confluence_set members are structurally Manifestations -- never Confluences --
        // so the contributed variant is always mcontr:<id>. See domain/Refs.kt for the
        // discriminator rationale.
        manifestationIds[name]?.let { "mcontr:$it" } ?: "canon:$name"
}
