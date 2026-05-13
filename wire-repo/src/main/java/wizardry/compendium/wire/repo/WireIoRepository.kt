package wizardry.compendium.wire.repo

import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Ability
import wizardry.compendium.essences.model.AwakeningStone
import wizardry.compendium.essences.model.CharacterBuild
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.wire.ContributionDomain
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.ImportSummary
import wizardry.compendium.wire.WireDecodeException
import wizardry.compendium.wire.WireExporter
import wizardry.compendium.wire.WireImporter
import wizardry.compendium.wire.WireVersionUnsupported
import wizardry.compendium.wire.filteredTo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WireIoRepository @Inject constructor(
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
) {
    private val exporter = WireExporter(
        essenceRepository,
        awakeningStoneRepository,
        abilityListingRepository,
        statusEffectRepository,
    )

    private val importer = WireImporter(
        essenceRepository,
        awakeningStoneRepository,
        abilityListingRepository,
        statusEffectRepository,
    )

    suspend fun exportFiltered(selection: Set<ContributionDomain>): EncodedShare {
        val envelope = exporter.exportFiltered(selection)
        val encoded = EnvelopeCodec.encode(envelope)
        return EncodedShare(
            text = encoded.text,
            byteSize = encoded.byteSize,
            fitsInShareLimit = encoded.fitsInShareLimit,
            shareSizeLimitBytes = EnvelopeCodec.ShareSizeLimitBytes,
        )
    }

    suspend fun import(envelope: Envelope, selection: Set<ContributionDomain>): ImportSummary {
        val filtered = envelope.filteredTo(selection)
        return importer.import(filtered)
    }

    suspend fun exportCounts(): ExportCounts {
        val essences = essenceRepository.getContributions()
        return ExportCounts(
            manifestations = essences.count { it is Essence.Manifestation },
            confluences = essences.count { it is Essence.Confluence },
            stones = awakeningStoneRepository.getContributions().size,
            listings = abilityListingRepository.getContributions().size,
            effects = statusEffectRepository.getContributions().size,
        )
    }

    fun encodeSingle(essence: Essence): String = when (essence) {
        is Essence.Manifestation -> EnvelopeCodec.encode(exporter.exportSingle(essence)).text
        is Essence.Confluence -> EnvelopeCodec.encode(exporter.exportSingle(essence)).text
        else -> error("Unsupported Essence subtype: ${essence::class}")
    }

    fun encodeSingle(stone: AwakeningStone): String =
        EnvelopeCodec.encode(exporter.exportSingle(stone)).text

    fun encodeSingle(listing: Ability.Listing): String =
        EnvelopeCodec.encode(exporter.exportSingle(listing)).text

    fun encodeSingle(effect: StatusEffect): String =
        EnvelopeCodec.encode(exporter.exportSingle(effect)).text

    fun encodeSingle(build: CharacterBuild): String =
        EnvelopeCodec.encode(exporter.exportSingle(build)).text

    fun decodeEnvelopeOrFailed(text: String): DecodeResult {
        if (text.isBlank()) return DecodeResult.Failed("Paste is empty.")
        return try {
            DecodeResult.Decoded(EnvelopeCodec.decode(text))
        } catch (e: WireVersionUnsupported) {
            DecodeResult.Failed(
                "This share was made with a newer app version. Update to import.",
            )
        } catch (e: WireDecodeException) {
            DecodeResult.Failed(
                e.message ?: "Pasted data is not a valid contribution share.",
            )
        } catch (e: Exception) {
            DecodeResult.Failed("Import failed: ${e.message}")
        }
    }

    sealed interface DecodeResult {
        data class Decoded(val envelope: Envelope) : DecodeResult
        data class Failed(val reason: String) : DecodeResult
    }

    data class EncodedShare(
        val text: String,
        val byteSize: Int,
        val fitsInShareLimit: Boolean,
        val shareSizeLimitBytes: Int,
    )

    data class ExportCounts(
        val manifestations: Int,
        val confluences: Int,
        val stones: Int,
        val listings: Int,
        val effects: Int,
    )
}
