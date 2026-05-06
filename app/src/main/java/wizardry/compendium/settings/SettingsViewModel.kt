package wizardry.compendium.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import wizardry.compendium.essences.AbilityListingRepository
import wizardry.compendium.essences.AwakeningStoneRepository
import wizardry.compendium.essences.EssenceRepository
import wizardry.compendium.essences.StatusEffectRepository
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.ui.DomainPickerRow
import wizardry.compendium.ui.theme.ThemeMode
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val essenceRepository: EssenceRepository,
    private val awakeningStoneRepository: AwakeningStoneRepository,
    private val abilityListingRepository: AbilityListingRepository,
    private val statusEffectRepository: StatusEffectRepository,
) : ViewModel() {
    /**
     * Background dispatcher for encode/decode/import work. Visible for
     * testing so unit tests can substitute the runTest dispatcher; defaults
     * to [Dispatchers.IO] in production. Constructor stays Hilt-friendly by
     * exposing this only as a settable property.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    val essenceContributionsEnabled = preferencesRepository.essenceContributionsEnabled
    val awakeningStoneContributionsEnabled = preferencesRepository.awakeningStoneContributionsEnabled
    val abilityListingContributionsEnabled = preferencesRepository.abilityListingContributionsEnabled
    val statusEffectContributionsEnabled = preferencesRepository.statusEffectContributionsEnabled
    val essencesAsAwakeningStonesEnabled = preferencesRepository.essencesAsAwakeningStonesEnabled

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesRepository.isCurrentThemeMode,
    )

    val dynamicColorEnabled: StateFlow<Boolean> = preferencesRepository.dynamicColorEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = preferencesRepository.isDynamicColorEnabled,
    )

    val dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val essenceConflictCount = essenceRepository.conflicts.map { it.size }
    val awakeningStoneConflictCount = awakeningStoneRepository.conflicts.map { it.size }
    val abilityListingConflictCount = abilityListingRepository.conflicts.map { it.size }
    val statusEffectConflictCount = statusEffectRepository.conflicts.map { it.size }

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

    /**
     * Modal state machine for the multi-step export and import flows.
     *
     * Export: `Idle` → `ExportPickerOpen` → `Encoding` → `ReadyToShare` /
     * `ShareTooLarge`. The screen consumes `ReadyToShare` and routes the
     * payload to a share intent or a SAF file write, then calls
     * `dismissPicker()` (or `resetIoState()`).
     *
     * Import: `Idle` → `ImportSourceOpen` → `Decoding` →
     * `ImportPreviewOpen` → `Importing` → `ImportComplete` /
     * `ImportFailed`.
     */
    sealed interface IoState {
        data object Idle : IoState
        data class ExportPickerOpen(val rows: List<DomainPickerRow<ContributionDomain>>) : IoState
        data object Encoding : IoState
        data class ReadyToShare(
            val text: String,
            val byteSize: Int,
            val selection: Set<ContributionDomain>,
        ) : IoState
        data class ShareTooLarge(
            val byteSize: Int,
            val limit: Int,
            val selection: Set<ContributionDomain>,
        ) : IoState
        data object ImportSourceOpen : IoState
        data object Decoding : IoState
        data class ImportPreviewOpen(
            val envelope: Envelope,
            val rows: List<DomainPickerRow<ContributionDomain>>,
        ) : IoState
        data object Importing : IoState
        data class ImportComplete(val summary: ImportSummary) : IoState
        data class ImportFailed(val message: String) : IoState
    }

    private val _ioState = MutableStateFlow<IoState>(IoState.Idle)
    val ioState: StateFlow<IoState> = _ioState.asStateFlow()

    fun setEssenceContributionsEnabled(enabled: Boolean) =
        preferencesRepository.setEssenceContributionsEnabled(enabled)
    fun setAwakeningStoneContributionsEnabled(enabled: Boolean) =
        preferencesRepository.setAwakeningStoneContributionsEnabled(enabled)
    fun setAbilityListingContributionsEnabled(enabled: Boolean) =
        preferencesRepository.setAbilityListingContributionsEnabled(enabled)
    fun setStatusEffectContributionsEnabled(enabled: Boolean) =
        preferencesRepository.setStatusEffectContributionsEnabled(enabled)
    fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean) =
        preferencesRepository.setEssencesAsAwakeningStonesEnabled(enabled)
    fun setThemeMode(mode: ThemeMode) = preferencesRepository.setThemeMode(mode)
    fun setDynamicColorEnabled(enabled: Boolean) =
        preferencesRepository.setDynamicColorEnabled(enabled)

    fun openExportPicker() {
        viewModelScope.launch(ioDispatcher) {
            val rows = buildExportRows()
            _ioState.value = IoState.ExportPickerOpen(rows)
        }
    }

    fun toggleExportDomain(domain: ContributionDomain) {
        val current = _ioState.value as? IoState.ExportPickerOpen ?: return
        val flipped = current.rows.map { row ->
            if (row.key == domain && row.enabled) row.copy(selected = !row.selected) else row
        }
        _ioState.value = IoState.ExportPickerOpen(flipped)
    }

    fun confirmExport() {
        val current = _ioState.value as? IoState.ExportPickerOpen ?: return
        val selection = current.rows.filter { it.selected }.map { it.key }.toSet()
        if (selection.isEmpty()) return  // button should already be disabled
        _ioState.value = IoState.Encoding
        viewModelScope.launch(ioDispatcher) {
            val envelope = exporter.exportFiltered(selection)
            val encoded = EnvelopeCodec.encode(envelope)
            _ioState.value = if (encoded.fitsInShareLimit) {
                IoState.ReadyToShare(text = encoded.text, byteSize = encoded.byteSize, selection = selection)
            } else {
                IoState.ShareTooLarge(
                    byteSize = encoded.byteSize,
                    limit = EnvelopeCodec.ShareSizeLimitBytes,
                    selection = selection,
                )
            }
        }
    }

    /**
     * Encode the given domain selection without going through the picker
     * state. Used by the "Save to File" path where the SAF launcher fires
     * after the picker sheet has dismissed.
     */
    fun encodeForFile(selection: Set<ContributionDomain>) {
        if (selection.isEmpty()) return
        _ioState.value = IoState.Encoding
        viewModelScope.launch(ioDispatcher) {
            val envelope = exporter.exportFiltered(selection)
            val encoded = EnvelopeCodec.encode(envelope)
            _ioState.value = if (encoded.fitsInShareLimit) {
                IoState.ReadyToShare(text = encoded.text, byteSize = encoded.byteSize, selection = selection)
            } else {
                IoState.ShareTooLarge(
                    byteSize = encoded.byteSize,
                    limit = EnvelopeCodec.ShareSizeLimitBytes,
                    selection = selection,
                )
            }
        }
    }

    fun openImportSource() {
        _ioState.value = IoState.ImportSourceOpen
    }

    fun pasteImport(text: String) {
        if (text.isBlank()) {
            _ioState.value = IoState.ImportFailed("Paste is empty.")
            return
        }
        _ioState.value = IoState.Decoding
        viewModelScope.launch(ioDispatcher) {
            try {
                val envelope = EnvelopeCodec.decode(text)
                val rows = buildImportRows(envelope)
                _ioState.value = IoState.ImportPreviewOpen(envelope = envelope, rows = rows)
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

    fun toggleImportDomain(domain: ContributionDomain) {
        val current = _ioState.value as? IoState.ImportPreviewOpen ?: return
        val flipped = current.rows.map { row ->
            if (row.key == domain && row.enabled) row.copy(selected = !row.selected) else row
        }
        _ioState.value = current.copy(rows = flipped)
    }

    fun confirmImport() {
        val current = _ioState.value as? IoState.ImportPreviewOpen ?: return
        val selection = current.rows.filter { it.selected }.map { it.key }.toSet()
        if (selection.isEmpty()) return
        val filtered = current.envelope.filteredTo(selection)
        _ioState.value = IoState.Importing
        viewModelScope.launch(ioDispatcher) {
            try {
                val summary = importer.import(filtered)
                _ioState.value = IoState.ImportComplete(summary)
            } catch (e: Exception) {
                _ioState.value = IoState.ImportFailed("Import failed: ${e.message}")
            }
        }
    }

    fun dismissPicker() {
        _ioState.value = IoState.Idle
    }

    fun resetIoState() {
        _ioState.value = IoState.Idle
    }

    private suspend fun buildExportRows(): List<DomainPickerRow<ContributionDomain>> {
        val essences = essenceRepository.getContributions()
        val mfns = essences.count { it is Essence.Manifestation }
        val confs = essences.count { it is Essence.Confluence }
        val stones = awakeningStoneRepository.getContributions().size
        val listings = abilityListingRepository.getContributions().size
        val effects = statusEffectRepository.getContributions().size
        return listOf(
            row(ContributionDomain.Essences, "Essences", mfns),
            row(ContributionDomain.Confluences, "Confluences", confs),
            row(ContributionDomain.AwakeningStones, "Awakening Stones", stones),
            row(ContributionDomain.AbilityListings, "Ability Listings", listings),
            row(ContributionDomain.StatusEffects, "Status Effects", effects),
        )
    }

    private fun row(
        key: ContributionDomain,
        label: String,
        count: Int,
    ): DomainPickerRow<ContributionDomain> = DomainPickerRow(
        key = key,
        label = label,
        count = count,
        countSuffix = "",
        selected = count > 0,
        enabled = count > 0,
    )

    private fun buildImportRows(envelope: Envelope): List<DomainPickerRow<ContributionDomain>> {
        return listOf(
            importRow(ContributionDomain.Essences, "Essences", envelope.manifestations.size),
            importRow(ContributionDomain.Confluences, "Confluences", envelope.confluences.size),
            importRow(ContributionDomain.AwakeningStones, "Awakening Stones", envelope.stones.size),
            importRow(ContributionDomain.AbilityListings, "Ability Listings", envelope.listings.size),
            importRow(ContributionDomain.StatusEffects, "Status Effects", envelope.statusEffects.size),
        )
    }

    private fun importRow(
        key: ContributionDomain,
        label: String,
        count: Int,
    ): DomainPickerRow<ContributionDomain> = DomainPickerRow(
        key = key,
        label = label,
        count = count,
        countSuffix = " in bundle",
        selected = count > 0,
        enabled = count > 0,
    )
}
