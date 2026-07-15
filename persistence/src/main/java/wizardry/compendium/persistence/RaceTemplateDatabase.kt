package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.RaceTemplate
import javax.inject.Inject

/**
 * Raw row tuples returned from RaceTemplateDatabase. The persistence layer
 * cannot resolve tagged-string refs (canonical lives outside the DB), so the
 * caller (repository) decodes [listingRef] via RefCodec and resolves against
 * the canonical and contributions caches.
 */
data class RawRaceTemplateRow(val name: String)

data class RawRaceTemplateAbilityRow(val templateName: String, val listingRef: String, val ordinal: Long)

/**
 * Snapshot of all race-template rows read inside a single SQLite transaction,
 * so assembling whole templates across the two tables can't see a torn view if
 * a writer commits between the two separate reads.
 */
data class RawRaceTemplateSnapshot(
    val templates: List<RawRaceTemplateRow>,
    val racialAbilities: List<RawRaceTemplateAbilityRow>,
)

/**
 * Strategy provided by the repository layer to encode an `Ability.Listing`
 * reference into tagged-string form at write time: `contr:<id>` for a listing
 * that exists in the contributions DB, otherwise `canon:<name>`. Mirrors
 * [BuildRefResolver] but race templates only reference ability listings.
 */
interface RaceTemplateRefResolver {
    fun encodeListing(listing: Ability.Listing): String
}

class RaceTemplateDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.raceTemplatesQueries

    // --- Read path: raw rows only ---------------------------------------

    // `race_template` has a single column, so SQLDelight's `SELECT *` maps each
    // row to the scalar `name` string rather than a generated row type.
    fun readAllRaceTemplates(): List<RawRaceTemplateRow> =
        q.selectAllRaceTemplates().executeAsList()
            .map { RawRaceTemplateRow(name = it) }

    fun readAllRacialAbilities(): List<RawRaceTemplateAbilityRow> =
        q.selectAllRacialAbilities().executeAsList()
            .map { RawRaceTemplateAbilityRow(templateName = it.template_name, listingRef = it.listing_ref, ordinal = it.ordinal) }

    /**
     * Read both race-template tables inside a single SQLite transaction so the
     * assembled snapshot is internally consistent. Prefer this over chaining
     * the two `readAll*` accessors at a call site that builds whole domain
     * `RaceTemplate` values.
     */
    fun readSnapshot(): RawRaceTemplateSnapshot = db.transactionWithResult {
        RawRaceTemplateSnapshot(
            templates = readAllRaceTemplates(),
            racialAbilities = readAllRacialAbilities(),
        )
    }

    /**
     * Returns the names of templates that reference the given listing ref
     * string (already-encoded form, e.g. "contr:42" or "canon:Tough"). Sorted,
     * distinct.
     */
    fun templatesReferencingListingRef(ref: String): List<String> =
        q.selectRacialAbilitiesReferencingListing(listing_ref = ref).executeAsList()
            .distinct().sorted()

    // --- Write path: takes a RaceTemplateRefResolver --------------------

    fun writeAll(templates: List<RaceTemplate>, resolver: RaceTemplateRefResolver) {
        db.transaction {
            q.deleteAllRacialAbilities()
            q.deleteAllRaceTemplates()
            templates.forEach { writeTemplateInternal(it, resolver) }
        }
    }

    fun upsert(template: RaceTemplate, resolver: RaceTemplateRefResolver) {
        db.transaction {
            q.deleteRacialAbilitiesForTemplate(template_name = template.name)
            q.deleteRaceTemplateByName(name = template.name)
            writeTemplateInternal(template, resolver)
        }
    }

    fun deleteByName(name: String) {
        db.transaction {
            q.deleteRacialAbilitiesForTemplate(template_name = name)
            q.deleteRaceTemplateByName(name = name)
        }
    }

    private fun writeTemplateInternal(template: RaceTemplate, resolver: RaceTemplateRefResolver) {
        q.insertRaceTemplate(name = template.name)
        template.racialAbilities.forEachIndexed { ordinal, listing ->
            q.insertRacialAbility(
                template_name = template.name,
                listing_ref = resolver.encodeListing(listing),
                ordinal = ordinal.toLong(),
            )
        }
    }
}
