package wizardry.compendium.persistence

import wizardry.compendium.domain.model.Essence

data class IdentifiedManifestation(val id: Long, val manifestation: Essence.Manifestation)

data class RawConfluenceSet(
    val essence1Ref: String,
    val essence2Ref: String,
    val essence3Ref: String,
    val isRestricted: Boolean,
)

data class IdentifiedConfluence(
    val id: Long,
    val name: String,
    val isRestricted: Boolean,
    val sets: List<RawConfluenceSet>,
)

interface EssenceCache {
    val identifiedManifestations: List<IdentifiedManifestation>
    val identifiedConfluences: List<IdentifiedConfluence>

    fun insertManifestation(manifestation: Essence.Manifestation): Long
    fun updateManifestation(id: Long, manifestation: Essence.Manifestation)
    fun deleteManifestationById(id: Long)
    fun findManifestationIdByName(name: String): Long?

    /**
     * Insert a confluence row and its confluence_set rows. The caller is
     * responsible for encoding each set's three essence references as
     * tagged-string refs ('canon:<name>' or 'contr:<id>') — the cache
     * cannot resolve canonical refs because canonical lives outside the DB.
     */
    fun insertConfluence(name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>): Long
    fun updateConfluence(id: Long, name: String, isRestricted: Boolean, sets: List<RawConfluenceSet>)
    fun deleteConfluenceById(id: Long)
    fun findConfluenceIdByName(name: String): Long?

    /**
     * Replace ALL rows (manifestations + confluences + confluence_sets) in one
     * transaction. Used by the canonical data loader, which loads all members
     * of a confluence_set from the same source — refs are encoded as
     * `contr:<id>` against the just-inserted manifestation rows.
     */
    fun replaceAll(essences: List<Essence>)
}
