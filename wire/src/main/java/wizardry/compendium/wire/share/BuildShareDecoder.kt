package wizardry.compendium.wire.share

import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.CharacterBuildRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.wire.BuildAbility
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.SlotIndex
import wizardry.compendium.wire.WireDecodeException
import wizardry.compendium.wire.WireVersionUnsupported

/**
 * Decodes a build share-text into a `BuildImportPreview`, resolving every
 * referenced name against the receiver's repos and surfacing both missing
 * references and build-name collisions for the preview sheet.
 *
 * Lives in `:wire` (not `:app`) so both `ShareViewModel` (in `:app`) and
 * `CharacterBuildContributionsViewModel` (in `:character-build-contributions`)
 * can depend on it without a VM-to-VM dependency or cyclic module graph.
 *
 * `open` for test substitution: VM tests provide a fake subclass that
 * returns a canned `Result` without going through the full wire decode.
 *
 * No `@Inject` because `:wire` is a pure JVM module that doesn't pull in
 * Hilt/javax.inject. Construction is wired via Hilt `@Provides` in
 * `:app/di/WireModule.kt`.
 */
open class BuildShareDecoder(
    private val essenceRepository: EssenceRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val buildRepository: CharacterBuildRepository,
) {

    sealed interface Result {
        data class Loaded(val preview: BuildImportPreview) : Result
        data class Failed(val reason: String) : Result
    }

    open suspend fun decode(text: String): Result {
        if (text.isBlank()) return Result.Failed("Paste is empty.")

        val envelope: Envelope = try {
            EnvelopeCodec.decode(text)
        } catch (e: WireVersionUnsupported) {
            return Result.Failed("This share was made with a newer app version. Update to import.")
        } catch (e: WireDecodeException) {
            return Result.Failed(e.message ?: "Pasted data is not a valid build share.")
        } catch (e: Exception) {
            return Result.Failed("Import failed: ${e.message}")
        }

        val others = envelope.manifestations.size + envelope.confluences.size +
            envelope.stones.size + envelope.listings.size + envelope.statusEffects.size
        if (envelope.builds.size != 1 || others > 0) {
            return Result.Failed(
                "This share doesn't contain exactly one character build. Use Settings → Import for multi-entry shares.",
            )
        }

        val wireBuild = envelope.builds.single()

        val essences = essenceRepository.getEssences()
        val manifestationByLower = essences.filterIsInstance<Essence.Manifestation>().associateBy { it.name.lowercase() }
        val confluenceByLower = essences.filterIsInstance<Essence.Confluence>().associateBy { it.name.lowercase() }
        val listingByLower = abilityListingRepository.getAbilityListings().associateBy { it.name.lowercase() }
        val nameCollides = buildRepository.getBuild(wireBuild.name) != null

        val racials = wireBuild.racials.map { name ->
            val match = listingByLower[name.lowercase()]
            if (match != null) RefResolution.Resolved(name, match) else RefResolution.Missing(name)
        }

        require(wireBuild.attributes.size == SlotIndex.SLOT_COUNT) {
            "Build '${wireBuild.name}' has ${wireBuild.attributes.size} slots, expected ${SlotIndex.SLOT_COUNT}"
        }

        val attributes = wireBuild.attributes.mapIndexed { i, wireAttr ->
            val slotName = SlotIndex.orderedNames[i]
            val resolution = when {
                wireAttr.essenceName.isEmpty() -> SlotResolution.Empty
                else -> {
                    val essence: Essence? = if (wireAttr.confluence) confluenceByLower[wireAttr.essenceName.lowercase()]
                    else manifestationByLower[wireAttr.essenceName.lowercase()]
                    if (essence == null) {
                        SlotResolution.Missing(wireAttr.essenceName)
                    } else {
                        val abilities = wireAttr.abilities.map { wireAbility ->
                            val match = listingByLower[wireAbility.name.lowercase()]
                            if (match != null) RefResolution.Resolved(wireAbility.name, match)
                            else RefResolution.Missing(wireAbility.name)
                        }
                        SlotResolution.Resolved(essence, abilities, wireAttr.abilities)
                    }
                }
            }
            AttributePreview(slot = slotName, resolution = resolution)
        }

        return Result.Loaded(
            BuildImportPreview(
                envelope = envelope,
                originalName = wireBuild.name,
                race = wireBuild.race,
                nameCollides = nameCollides,
                racials = racials,
                attributes = attributes,
            ),
        )
    }
}

data class BuildImportPreview(
    val envelope: Envelope,
    val originalName: String,
    val race: String,
    val nameCollides: Boolean,
    val racials: List<RefResolution<Ability.Listing>>,
    val attributes: List<AttributePreview>,
) {
    val missingCount: Int =
        racials.count { it is RefResolution.Missing } +
            attributes.sumOf { attr ->
                when (val r = attr.resolution) {
                    is SlotResolution.Missing -> 1
                    is SlotResolution.Resolved -> r.abilities.count { it is RefResolution.Missing }
                    SlotResolution.Empty -> 0
                }
            }
}

data class AttributePreview(val slot: String, val resolution: SlotResolution)

sealed interface SlotResolution {
    data object Empty : SlotResolution
    data class Resolved(
        val essence: Essence,
        val abilities: List<RefResolution<Ability.Listing>>,
        val wireAbilities: List<BuildAbility>,
    ) : SlotResolution
    data class Missing(val essenceName: String) : SlotResolution
}

sealed interface RefResolution<out T> {
    data class Resolved<T>(val name: String, val value: T) : RefResolution<T>
    data class Missing(val name: String) : RefResolution<Nothing>
}
