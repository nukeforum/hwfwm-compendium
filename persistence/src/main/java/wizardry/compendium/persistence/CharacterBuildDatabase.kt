package wizardry.compendium.persistence

import app.cash.sqldelight.db.SqlDriver
import wizardry.compendium.essences.model.AbsorbedEssence
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Attribute
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import javax.inject.Inject

/**
 * Persists CharacterBuild rows in contributions.db. Stores ESSENCE/LISTING NAMES,
 * not embedded entities — cross-DB FKs aren't supported in SQLite. Hydration is the
 * repository's job; this layer just round-trips bytes.
 *
 * Read-time hydration uses placeholder Essence.Manifestation / Ability.Listing values.
 * The repository (DefaultCharacterBuildRepository) replaces placeholders with real
 * lookups against EssenceRepository / AbilityListingRepository.
 */
class CharacterBuildDatabase @Inject constructor(driver: SqlDriver) {
    private val db = CompendiumDatabase(driver)

    fun writeAll(builds: List<CharacterBuild>) {
        db.transaction {
            db.characterBuildsQueries.deleteAllAcquiredAbilities()
            db.characterBuildsQueries.deleteAllAttributes()
            db.characterBuildsQueries.deleteAllRacialAbilities()
            db.characterBuildsQueries.deleteAllCharacterBuilds()
            builds.forEach { build -> writeBuild(build) }
        }
    }

    fun deleteByName(name: String) {
        db.transaction {
            db.characterBuildsQueries.deleteAcquiredAbilitiesForBuild(name)
            db.characterBuildsQueries.deleteAttributesForBuild(name)
            db.characterBuildsQueries.deleteRacialAbilitiesForBuild(name)
            db.characterBuildsQueries.deleteCharacterBuildByName(name)
        }
    }

    fun upsert(build: CharacterBuild) {
        db.transaction {
            db.characterBuildsQueries.deleteAcquiredAbilitiesForBuild(build.name)
            db.characterBuildsQueries.deleteAttributesForBuild(build.name)
            db.characterBuildsQueries.deleteRacialAbilitiesForBuild(build.name)
            db.characterBuildsQueries.deleteCharacterBuildByName(build.name)
            writeBuild(build)
        }
    }

    fun readAll(): List<CharacterBuild> {
        val buildRows = db.characterBuildsQueries.selectAllCharacterBuilds().executeAsList()
        if (buildRows.isEmpty()) return emptyList()
        val racials = db.characterBuildsQueries.selectAllRacialAbilities().executeAsList()
            .groupBy { it.build_name }
        val attrs = db.characterBuildsQueries.selectAllAttributes().executeAsList()
            .groupBy { it.build_name }
        val acquired = db.characterBuildsQueries.selectAllAcquiredAbilities().executeAsList()
            .groupBy { it.build_name to it.attribute_kind }

        return buildRows.map { row ->
            val racialAbilities = (racials[row.name].orEmpty())
                .sortedBy { it.ordinal }
                .map { Ability.Listing.of(it.listing_name) }

            val attributes = AttributeKind.entries.map { kind ->
                val attrRow = attrs[row.name].orEmpty().firstOrNull { it.kind == kind.name }
                if (attrRow == null) {
                    kind.empty()
                } else {
                    val placeholderEssence = placeholderEssence(attrRow.essence_name)
                    val absorbed = AbsorbedEssence(
                        essence = placeholderEssence,
                        abilities = (acquired[row.name to kind.name].orEmpty())
                            .sortedBy { it.ordinal }
                            .map { ability ->
                                Ability.Acquired(
                                    name = ability.listing_name,
                                    effects = emptyList(),
                                    rank = Rank.valueOf(ability.rank),
                                    tier = ability.tier.toInt(),
                                    progress = ability.progress.toFloat(),
                                    boundEssence = placeholderEssence,
                                    listing = Ability.Listing.of(ability.listing_name),
                                )
                            },
                    )
                    kind.withEssence(absorbed)
                }
            }.toSet()

            CharacterBuild(
                name = row.name,
                race = row.race,
                racialAbilities = racialAbilities,
                attributes = attributes,
            )
        }.sortedBy { it.name }
    }

    private fun writeBuild(build: CharacterBuild) {
        db.characterBuildsQueries.insertCharacterBuild(name = build.name, race = build.race)

        build.racialAbilities.forEachIndexed { ordinal, listing ->
            db.characterBuildsQueries.insertRacialAbility(
                build_name = build.name,
                listing_name = listing.name,
                ordinal = ordinal.toLong(),
            )
        }

        AttributeKind.entries.forEach { kind ->
            val attribute = build.attributeFor(kind)
            val absorbed = attribute.essence ?: return@forEach
            db.characterBuildsQueries.insertAttribute(
                build_name = build.name,
                kind = kind.name,
                essence_name = absorbed.essence.name,
            )
            absorbed.abilities.forEachIndexed { ordinal, acquired ->
                db.characterBuildsQueries.insertAcquiredAbility(
                    build_name = build.name,
                    attribute_kind = kind.name,
                    listing_name = acquired.name,
                    rank = acquired.rank.name,
                    tier = acquired.tier.toLong(),
                    progress = acquired.progress.toDouble(),
                    ordinal = ordinal.toLong(),
                )
            }
        }
    }

    private fun placeholderEssence(name: String): Essence.Manifestation =
        Essence.Manifestation(
            name = name,
            rank = Rank.Unranked,
            rarity = Rarity.Unknown,
            properties = emptyList(),
            description = "",
            isRestricted = false,
        )

    private enum class AttributeKind {
        Power, Speed, Spirit, Recovery;

        fun empty(): Attribute = when (this) {
            Power -> Attribute.Power()
            Speed -> Attribute.Speed()
            Spirit -> Attribute.Spirit()
            Recovery -> Attribute.Recovery()
        }

        fun withEssence(absorbed: AbsorbedEssence): Attribute = when (this) {
            Power -> Attribute.Power(essence = absorbed)
            Speed -> Attribute.Speed(essence = absorbed)
            Spirit -> Attribute.Spirit(essence = absorbed)
            Recovery -> Attribute.Recovery(essence = absorbed)
        }
    }

    private fun CharacterBuild.attributeFor(kind: AttributeKind): Attribute = when (kind) {
        AttributeKind.Power -> Power
        AttributeKind.Speed -> Speed
        AttributeKind.Spirit -> Spirit
        AttributeKind.Recovery -> Recovery
    }
}
