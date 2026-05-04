package wizardry.compendium.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.wire.EnvelopeCodec
import wizardry.compendium.wire.ImportSummary
import wizardry.compendium.wire.WireDecodeException
import wizardry.compendium.wire.WireExporter
import wizardry.compendium.wire.WireImporter
import wizardry.compendium.wire.WireVersionUnsupported
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    essenceRepository: EssenceRepository,
    awakeningStoneRepository: AwakeningStoneRepository,
    abilityListingRepository: AbilityListingRepository,
    statusEffectRepository: StatusEffectRepository,
) : ViewModel() {
    val essenceContributionsEnabled = preferencesRepository.essenceContributionsEnabled
    val awakeningStoneContributionsEnabled = preferencesRepository.awakeningStoneContributionsEnabled
    val abilityListingContributionsEnabled = preferencesRepository.abilityListingContributionsEnabled

    val essenceConflictCount = essenceRepository.conflicts.map { it.size }
    val awakeningStoneConflictCount = awakeningStoneRepository.conflicts.map { it.size }
    val abilityListingConflictCount = abilityListingRepository.conflicts.map { it.size }

    private val exporter = WireExporter(essenceRepository, awakeningStoneRepository, abilityListingRepository, statusEffectRepository)
    private val importer = WireImporter(essenceRepository, awakeningStoneRepository, abilityListingRepository, statusEffectRepository)

    /**
     * Modal states for the share/import flows.
     *
     * The ViewModel owns these because both export and import are
     * multi-step: export goes through encode → check size → emit share
     * intent; import goes through decode → run importer → show summary.
     * Each step emits a new state, the screen reacts.
     */
    sealed interface IoState {
        data object Idle : IoState
        data object Encoding : IoState
        data class ReadyToShare(val text: String, val byteSize: Int) : IoState
        data class ShareTooLarge(val byteSize: Int, val limit: Int) : IoState
        data object Importing : IoState
        data class ImportComplete(val summary: ImportSummary) : IoState
        data class ImportFailed(val message: String) : IoState
    }

    private val _ioState = MutableStateFlow<IoState>(IoState.Idle)
    val ioState: StateFlow<IoState> = _ioState.asStateFlow()

    fun setEssenceContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setEssenceContributionsEnabled(enabled)
    }

    fun setAwakeningStoneContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setAwakeningStoneContributionsEnabled(enabled)
    }

    fun setAbilityListingContributionsEnabled(enabled: Boolean) {
        preferencesRepository.setAbilityListingContributionsEnabled(enabled)
    }

    /**
     * Build an envelope of every user contribution, encode it, and surface
     * the result for the screen to launch a share intent.
     *
     * If the encoded payload exceeds the share-size limit, the state moves
     * to `ShareTooLarge` instead — the screen prompts the user to use
     * file export (tier 4c).
     */
    fun beginExport() {
        _ioState.value = IoState.Encoding
        viewModelScope.launch(Dispatchers.IO) {
            val envelope = exporter.exportAll()
            val encoded = EnvelopeCodec.encode(envelope)
            _ioState.value = if (encoded.fitsInShareLimit) {
                IoState.ReadyToShare(text = encoded.text, byteSize = encoded.byteSize)
            } else {
                IoState.ShareTooLarge(byteSize = encoded.byteSize, limit = EnvelopeCodec.ShareSizeLimitBytes)
            }
        }
    }

    /**
     * Decode a pasted share string, apply it via the importer, and surface
     * the per-entry summary.
     *
     * Decode-time errors (bad base64, corrupt gzip, future version) become
     * `ImportFailed` with a user-friendly message. Per-entry import results
     * are surfaced via `ImportComplete`.
     */
    fun importFromText(text: String) {
        if (text.isBlank()) {
            _ioState.value = IoState.ImportFailed("Paste is empty.")
            return
        }
        _ioState.value = IoState.Importing
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val envelope = EnvelopeCodec.decode(text)
                val summary = importer.import(envelope)
                _ioState.value = IoState.ImportComplete(summary)
            } catch (e: WireVersionUnsupported) {
                _ioState.value = IoState.ImportFailed(
                    "This share was created with a newer version of the app. Update the app to import it.",
                )
            } catch (e: WireDecodeException) {
                _ioState.value = IoState.ImportFailed(
                    e.message ?: "Pasted data is not a valid contributions share.",
                )
            } catch (e: Exception) {
                _ioState.value = IoState.ImportFailed("Import failed: ${e.message}")
            }
        }
    }

    fun resetIoState() {
        _ioState.value = IoState.Idle
    }
}
