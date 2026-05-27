package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.domain.model.Ability
import wizardry.compendium.domain.model.CharacterBuild
import wizardry.compendium.domain.model.Essence
import javax.inject.Inject

/**
 * Raw row tuples returned from CharacterBuildDatabase. The persistence
 * layer cannot resolve tagged-string refs (canonical lives outside the DB),
 * so the caller (repository) decodes [listingRef] / [essenceRef] via
 * RefCodec and resolves against the canonical and contributions caches.
 */
data class RawBuildRow(val name: String, val race: String)

data class RawRacialAbilityRow(val buildName: String, val listingRef: String, val ordinal: Long)

data class RawAttributeRow(val buildName: String, val kind: String, val essenceRef: String)

data class RawAcquiredAbilityRow(
    val buildName: String,
    val attributeKind: String,
    val listingRef: String,
    val rank: String,
    val tier: Long,
    val progress: Double,
    val ordinal: Long,
)

/**
 * Snapshot of all build-related rows read inside a single SQLite transaction.
 * Use this when the caller needs to assemble whole builds across the four
 * tables — repository hydration would otherwise see a torn view if a writer
 * commits between the four separate reads.
 */
data class RawBuildSnapshot(
    val builds: List<RawBuildRow>,
    val racialAbilities: List<RawRacialAbilityRow>,
    val attributes: List<RawAttributeRow>,
    val acquiredAbilities: List<RawAcquiredAbilityRow>,
)

/**
 * Strategy provided by the repository layer to encode `Ability.Listing` /
 * `Essence` references into tagged-string form at write time.
 *
 * - For a listing or essence that exists in the contributions DB → `contr:<id>`.
 * - Otherwise → `canon:<name>`.
 */
interface BuildRefResolver {
    fun encodeListing(listing: Ability.Listing): String
    fun encodeEssence(essence: Essence): String
}

class CharacterBuildDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)
    private val q get() = db.characterBuildsQueries

    // --- Read path: raw rows only ---------------------------------------

    fun readAllBuilds(): List<RawBuildRow> =
        q.selectAllCharacterBuilds().executeAsList()
            .map { RawBuildRow(name = it.name, race = it.race) }

    fun readAllRacialAbilities(): List<RawRacialAbilityRow> =
        q.selectAllRacialAbilities().executeAsList()
            .map { RawRacialAbilityRow(buildName = it.build_name, listingRef = it.listing_ref, ordinal = it.ordinal) }

    fun readAllAttributes(): List<RawAttributeRow> =
        q.selectAllAttributes().executeAsList()
            .map { RawAttributeRow(buildName = it.build_name, kind = it.kind, essenceRef = it.essence_ref) }

    fun readAllAcquiredAbilities(): List<RawAcquiredAbilityRow> =
        q.selectAllAcquiredAbilities().executeAsList()
            .map {
                RawAcquiredAbilityRow(
                    buildName = it.build_name,
                    attributeKind = it.attribute_kind,
                    listingRef = it.listing_ref,
                    rank = it.rank,
                    tier = it.tier,
                    progress = it.progress,
                    ordinal = it.ordinal,
                )
            }

    /**
     * Read all four build-related tables inside a single SQLite transaction so
     * the assembled snapshot is internally consistent. Prefer this over
     * chaining the four `readAll*` accessors at a call site that builds whole
     * domain `CharacterBuild` values.
     */
    fun readSnapshot(): RawBuildSnapshot = db.transactionWithResult {
        RawBuildSnapshot(
            builds = readAllBuilds(),
            racialAbilities = readAllRacialAbilities(),
            attributes = readAllAttributes(),
            acquiredAbilities = readAllAcquiredAbilities(),
        )
    }

    /**
     * Returns the names of builds that reference the given listing ref string
     * (the caller passes already-encoded form, e.g. "contr:42" or "canon:Flame Bolt").
     * Searches both racial-ability and acquired-ability tables. Sorted, distinct.
     */
    fun buildsReferencingListingRef(ref: String): List<String> {
        val a = q.selectRacialAbilitiesReferencingListing(listing_ref = ref).executeAsList()
        val b = q.selectAcquiredAbilitiesReferencingListing(listing_ref = ref).executeAsList()
        return (a + b).distinct().sorted()
    }

    /** Same shape for essence_ref. Searches the attribute table. */
    fun buildsReferencingEssenceRef(ref: String): List<String> =
        q.selectAttributesReferencingEssence(essence_ref = ref).executeAsList()
            .distinct().sorted()

    // --- Write path: takes a BuildRefResolver ---------------------------

    fun writeAll(builds: List<CharacterBuild>, resolver: BuildRefResolver) {
        db.transaction {
            q.deleteAllAcquiredAbilities()
            q.deleteAllAttributes()
            q.deleteAllRacialAbilities()
            q.deleteAllCharacterBuilds()
            builds.forEach { writeBuildInternal(it, resolver) }
        }
    }

    fun upsert(build: CharacterBuild, resolver: BuildRefResolver) {
        db.transaction {
            q.deleteAcquiredAbilitiesForBuild(build_name = build.name)
            q.deleteAttributesForBuild(build_name = build.name)
            q.deleteRacialAbilitiesForBuild(build_name = build.name)
            q.deleteCharacterBuildByName(name = build.name)
            writeBuildInternal(build, resolver)
        }
    }

    fun deleteByName(name: String) {
        db.transaction {
            q.deleteAcquiredAbilitiesForBuild(build_name = name)
            q.deleteAttributesForBuild(build_name = name)
            q.deleteRacialAbilitiesForBuild(build_name = name)
            q.deleteCharacterBuildByName(name = name)
        }
    }

    private fun writeBuildInternal(build: CharacterBuild, resolver: BuildRefResolver) {
        q.insertCharacterBuild(name = build.name, race = build.race)

        build.racialAbilities.forEachIndexed { ordinal, listing ->
            q.insertRacialAbility(
                build_name = build.name,
                listing_ref = resolver.encodeListing(listing),
                ordinal = ordinal.toLong(),
            )
        }

        AttributeKind.entries.forEach { kind ->
            val attribute = build.attributeFor(kind)
            val absorbed = attribute.essence ?: return@forEach
            q.insertAttribute(
                build_name = build.name,
                kind = kind.name,
                essence_ref = resolver.encodeEssence(absorbed.essence),
            )
            absorbed.abilities.forEachIndexed { ordinal, acquired ->
                q.insertAcquiredAbility(
                    build_name = build.name,
                    attribute_kind = kind.name,
                    listing_ref = resolver.encodeListing(Ability.Listing.of(acquired.name)),
                    rank = acquired.rank.name,
                    tier = acquired.tier.toLong(),
                    progress = acquired.progress.toDouble(),
                    ordinal = ordinal.toLong(),
                )
            }
        }
    }

    private enum class AttributeKind {
        Power, Speed, Spirit, Recovery;
    }

    private fun CharacterBuild.attributeFor(kind: AttributeKind): wizardry.compendium.domain.model.Attribute = when (kind) {
        AttributeKind.Power -> Power
        AttributeKind.Speed -> Speed
        AttributeKind.Spirit -> Spirit
        AttributeKind.Recovery -> Recovery
    }
}
