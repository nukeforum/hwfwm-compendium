package wizardry.compendium.drive.backup

import wizardry.compendium.wire.ImportSummary

class FakeWirePayloadCodec : WirePayloadCodecApi {
    var hasLocal: Boolean = false
    var encodedText: String = "envelope-text"
    var nextImportOutcome: WireImportOutcome = WireImportOutcome.Imported(
        ImportSummary(results = emptyList())
    )
    var importCalls = 0
    var lastImportText: String? = null

    override suspend fun encode(): String = encodedText
    override suspend fun hasLocalContributions(): Boolean = hasLocal
    override suspend fun decodeAndImport(text: String): WireImportOutcome {
        importCalls++
        lastImportText = text
        return nextImportOutcome
    }
}
