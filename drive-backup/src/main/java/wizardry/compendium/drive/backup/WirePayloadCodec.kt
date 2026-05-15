package wizardry.compendium.drive.backup

import wizardry.compendium.wire.ContributionDomain
import wizardry.compendium.wire.ImportSummary
import wizardry.compendium.wire.repo.WireIoRepository
import javax.inject.Inject

interface WirePayloadCodecApi {
    suspend fun encode(): String
    suspend fun hasLocalContributions(): Boolean
    suspend fun decodeAndImport(text: String): WireImportOutcome
}

@javax.inject.Singleton
class WirePayloadCodec @Inject constructor(
    private val wireIo: WireIoRepository,
) : WirePayloadCodecApi {
    private val allDomains = ContributionDomain.entries.toSet()

    override suspend fun encode(): String =
        wireIo.exportFiltered(allDomains).text

    override suspend fun hasLocalContributions(): Boolean {
        val counts = wireIo.exportCounts()
        return counts.manifestations + counts.confluences +
            counts.stones + counts.listings + counts.effects > 0
    }

    override suspend fun decodeAndImport(text: String): WireImportOutcome {
        return when (val result = wireIo.decodeEnvelopeOrFailed(text)) {
            is WireIoRepository.DecodeResult.Failed -> WireImportOutcome.Failed(result.reason)
            is WireIoRepository.DecodeResult.Decoded -> {
                val summary = wireIo.import(result.envelope, allDomains)
                WireImportOutcome.Imported(summary)
            }
        }
    }
}

sealed interface WireImportOutcome {
    data class Imported(val summary: ImportSummary) : WireImportOutcome
    data class Failed(val reason: String) : WireImportOutcome
}
