package wizardry.compendium.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import wizardry.compendium.ui.theme.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.ui.ContributionDomainPicker
import wizardry.compendium.ui.DomainPickerRow
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.wire.ContributionDomain
import wizardry.compendium.wire.ImportSummary

@Composable
fun SettingsScreen(
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val essenceContributionsEnabled by viewModel.essenceContributionsEnabled.collectAsState(initial = false)
    val awakeningStoneContributionsEnabled by viewModel.awakeningStoneContributionsEnabled.collectAsState(initial = false)
    val abilityListingContributionsEnabled by viewModel.abilityListingContributionsEnabled.collectAsState(initial = false)
    val statusEffectContributionsEnabled by viewModel.statusEffectContributionsEnabled.collectAsState(initial = true)
    val essencesAsAwakeningStonesEnabled by viewModel.essencesAsAwakeningStonesEnabled.collectAsState(initial = false)
    val essenceConflictCount by viewModel.essenceConflictCount.collectAsState(initial = 0)
    val awakeningStoneConflictCount by viewModel.awakeningStoneConflictCount.collectAsState(initial = 0)
    val abilityListingConflictCount by viewModel.abilityListingConflictCount.collectAsState(initial = 0)
    val statusEffectConflictCount by viewModel.statusEffectConflictCount.collectAsState(initial = 0)
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val ioState by viewModel.ioState.collectAsState()
    val context = LocalContext.current
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // When non-null, an encoder result should be written to this URI rather
    // than fired into a share intent. Set by the SAF launcher callback;
    // cleared after the file write completes.
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }
    // Capture the selection at the moment the user taps "Save to File" so
    // the SAF launcher's callback can re-invoke confirmExport with the same
    // set after the picker sheet has dismissed.
    var pendingExportSelection by remember {
        mutableStateOf<Set<ContributionDomain>>(emptySet())
    }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingExportUri = uri
            // Re-enter encoding for the captured selection. The picker
            // sheet has already dismissed; we transition straight to
            // Encoding via a tiny shim.
            viewModel.encodeForFile(pendingExportSelection)
        } else {
            pendingExportSelection = emptySet()
        }
    }

    // SAF launcher: "Open File". `text/*` lets the picker accept .compendium
    // files (registered as text) plus any other text the user has on disk.
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            val text = try {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            } catch (e: Exception) {
                viewModel.resetIoState()
                viewModel.pasteImport("")  // surface the empty-paste error path
                return@rememberLauncherForActivityResult
            }
            viewModel.pasteImport(text)
        }
    }

    SettingsContent(
        themeMode = themeMode,
        onThemeModeSelected = viewModel::setThemeMode,
        dynamicColorAvailable = viewModel.dynamicColorAvailable,
        dynamicColorEnabled = dynamicColorEnabled,
        onDynamicColorToggled = viewModel::setDynamicColorEnabled,
        essenceContributionsEnabled = essenceContributionsEnabled,
        essenceConflictCount = essenceConflictCount,
        onEssenceContributionsToggled = viewModel::setEssenceContributionsEnabled,
        awakeningStoneContributionsEnabled = awakeningStoneContributionsEnabled,
        awakeningStoneConflictCount = awakeningStoneConflictCount,
        onAwakeningStoneContributionsToggled = viewModel::setAwakeningStoneContributionsEnabled,
        abilityListingContributionsEnabled = abilityListingContributionsEnabled,
        abilityListingConflictCount = abilityListingConflictCount,
        onAbilityListingContributionsToggled = viewModel::setAbilityListingContributionsEnabled,
        statusEffectContributionsEnabled = statusEffectContributionsEnabled,
        statusEffectConflictCount = statusEffectConflictCount,
        onStatusEffectContributionsToggled = viewModel::setStatusEffectContributionsEnabled,
        essencesAsAwakeningStonesEnabled = essencesAsAwakeningStonesEnabled,
        onEssencesAsAwakeningStonesToggled = viewModel::setEssencesAsAwakeningStonesEnabled,
        onExportClick = viewModel::openExportPicker,
        onImportClick = viewModel::openImportSource,
        onAboutClick = onAboutClick,
        ioState = ioState,
    )

    // Routing effect: when encoding finishes with a fittable payload, route
    // to either the share intent OR the pending file URI. The VM stays
    // ignorant of which transport was chosen.
    LaunchedEffect(ioState) {
        val state = ioState
        if (state is SettingsViewModel.IoState.ReadyToShare) {
            val targetUri = pendingExportUri
            if (targetUri != null) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        out.write(state.text.toByteArray(Charsets.UTF_8))
                    }
                } catch (_: Exception) {
                    // File-write failures are rare but possible (revoked
                    // permission, disk full). Silent for now; future tier
                    // could surface a toast or dialog.
                }
                pendingExportUri = null
            } else {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, state.text)
                }
                context.startActivity(Intent.createChooser(intent, "Share contributions"))
            }
            viewModel.resetIoState()
        }
    }

    when (val state = ioState) {
        is SettingsViewModel.IoState.ExportPickerOpen -> {
            ExportPickerSheet(
                rows = state.rows,
                onToggle = viewModel::toggleExportDomain,
                onShare = {
                    pendingExportUri = null
                    viewModel.confirmExport()
                },
                onSaveToFile = {
                    val selection = state.rows.filter { it.selected }.map { it.key }.toSet()
                    if (selection.isNotEmpty()) {
                        pendingExportSelection = selection
                        viewModel.dismissPicker()
                        createDocumentLauncher.launch("contributions.compendium")
                    }
                },
                onDismiss = viewModel::dismissPicker,
            )
        }
        is SettingsViewModel.IoState.ImportSourceOpen -> {
            ImportSourceSheet(
                onPaste = {
                    pasteText = ""
                    showPasteDialog = true
                },
                onOpenFile = { openDocumentLauncher.launch(arrayOf("text/*")) },
                onDismiss = viewModel::dismissPicker,
            )
        }
        is SettingsViewModel.IoState.ImportPreviewOpen -> {
            ImportPreviewSheet(
                rows = state.rows,
                onToggle = viewModel::toggleImportDomain,
                onConfirm = viewModel::confirmImport,
                onDismiss = viewModel::dismissPicker,
            )
        }
        is SettingsViewModel.IoState.ShareTooLarge -> {
            AlertDialog(
                onDismissRequest = viewModel::resetIoState,
                title = { Text("Share too large") },
                text = {
                    Text(
                        "Your contributions encoded to ${state.byteSize / 1024} KB, " +
                            "above the ${state.limit / 1024} KB limit for plain-text shares. " +
                            "Use \"Save to File\" instead.",
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.resetIoState()
                        createDocumentLauncher.launch("contributions.compendium")
                    }) { Text("Save to File") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::resetIoState) { Text("Cancel") }
                },
            )
        }
        is SettingsViewModel.IoState.ImportFailed -> {
            AlertDialog(
                onDismissRequest = viewModel::resetIoState,
                title = { Text("Import failed") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = viewModel::resetIoState) { Text("OK") }
                },
            )
        }
        is SettingsViewModel.IoState.ImportComplete -> {
            ImportSummaryDialog(state.summary, onDismiss = viewModel::resetIoState)
        }
        else -> {}
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text("Paste import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a contribution share you received.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        label = { Text("Paste here") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        minLines = 6,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPasteDialog = false
                    viewModel.pasteImport(pasteText)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    dynamicColorAvailable: Boolean,
    dynamicColorEnabled: Boolean,
    onDynamicColorToggled: (Boolean) -> Unit,
    essenceContributionsEnabled: Boolean,
    essenceConflictCount: Int,
    onEssenceContributionsToggled: (Boolean) -> Unit,
    awakeningStoneContributionsEnabled: Boolean,
    awakeningStoneConflictCount: Int,
    onAwakeningStoneContributionsToggled: (Boolean) -> Unit,
    abilityListingContributionsEnabled: Boolean,
    abilityListingConflictCount: Int,
    onAbilityListingContributionsToggled: (Boolean) -> Unit,
    statusEffectContributionsEnabled: Boolean,
    statusEffectConflictCount: Int,
    onStatusEffectContributionsToggled: (Boolean) -> Unit,
    essencesAsAwakeningStonesEnabled: Boolean,
    onEssencesAsAwakeningStonesToggled: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onAboutClick: () -> Unit,
    ioState: SettingsViewModel.IoState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        ThemeModeSelector(
            selected = themeMode,
            onSelected = onThemeModeSelected,
        )
        if (dynamicColorAvailable) {
            ToggleRow(
                title = "Use dynamic colors",
                subtitle = "Match your wallpaper",
                checked = dynamicColorEnabled,
                conflictCount = 0,
                onCheckedChange = onDynamicColorToggled,
            )
        }
        HorizontalDivider()
        Text("Contributions", style = MaterialTheme.typography.titleMedium)
        ToggleRow(
            title = "My Essences",
            subtitle = "Include your submitted essences",
            checked = essenceContributionsEnabled,
            conflictCount = essenceConflictCount,
            onCheckedChange = onEssenceContributionsToggled,
        )
        ToggleRow(
            title = "My Awakening Stones",
            subtitle = "Include your submitted awakening stones",
            checked = awakeningStoneContributionsEnabled,
            conflictCount = awakeningStoneConflictCount,
            onCheckedChange = onAwakeningStoneContributionsToggled,
        )
        ToggleRow(
            title = "My Ability Listings",
            subtitle = "Include your submitted ability listings",
            checked = abilityListingContributionsEnabled,
            conflictCount = abilityListingConflictCount,
            onCheckedChange = onAbilityListingContributionsToggled,
        )
        ToggleRow(
            title = "My Status Effects",
            subtitle = "Include your submitted status effects",
            checked = statusEffectContributionsEnabled,
            conflictCount = statusEffectConflictCount,
            onCheckedChange = onStatusEffectContributionsToggled,
        )

        HorizontalDivider()

        Text("Awakening Stones", style = MaterialTheme.typography.titleMedium)
        ToggleRow(
            title = "Essences as Awakening Stones",
            subtitle = "Show essences in awakening stone listings",
            checked = essencesAsAwakeningStonesEnabled,
            conflictCount = 0,
            onCheckedChange = onEssencesAsAwakeningStonesToggled,
        )

        HorizontalDivider()

        Text("Backup & Share", style = MaterialTheme.typography.titleMedium)
        Text(
            "Export any subset of your contributions as a text blob (Discord, " +
                "email, etc.) or to a file. Import bundles you've received.",
            style = MaterialTheme.typography.bodySmall,
        )
        val encoding = ioState is SettingsViewModel.IoState.Encoding ||
            ioState is SettingsViewModel.IoState.ExportPickerOpen
        val importing = ioState is SettingsViewModel.IoState.Importing ||
            ioState is SettingsViewModel.IoState.Decoding ||
            ioState is SettingsViewModel.IoState.ImportPreviewOpen ||
            ioState is SettingsViewModel.IoState.ImportSourceOpen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onExportClick,
                enabled = !encoding,
                modifier = Modifier.weight(1f),
            ) {
                Text("Export…")
            }
            Button(
                onClick = onImportClick,
                enabled = !importing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Import…")
            }
        }

        HorizontalDivider()

        Text("About", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = onAboutClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("About this app")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    conflictCount: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val locked = conflictCount > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            if (locked) {
                Text(
                    text = "Resolve $conflictCount conflict${if (conflictCount == 1) "" else "s"} to enable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Switch(
            checked = checked && !locked,
            onCheckedChange = onCheckedChange,
            enabled = !locked,
        )
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(mode.name)
            }
        }
    }
}

@Composable
private fun ImportSummaryDialog(summary: ImportSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Import complete")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${summary.addedCount} added · ${summary.skippedCount} skipped · " +
                        "${summary.failedCount} failed",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (summary.skipped.isNotEmpty()) {
                    Text("Skipped (already existed):", style = MaterialTheme.typography.labelMedium)
                    summary.skipped.forEach { entry ->
                        Text("• ${entry.name} — ${entry.reason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (summary.failed.isNotEmpty()) {
                    Text("Failed:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    summary.failed.forEach { entry ->
                        Text(
                            "• ${entry.name} — ${entry.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("OK") }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExportPickerSheet(
    rows: List<DomainPickerRow<ContributionDomain>>,
    onToggle: (ContributionDomain) -> Unit,
    onShare: () -> Unit,
    onSaveToFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { /* drag-dismiss is blocked by confirmValueChange */ },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Export contributions", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick which contributions to include.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ContributionDomainPicker(
                rows = rows,
                onToggle = onToggle,
            )
            val anySelected = rows.any { it.selected }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onShare,
                    enabled = anySelected,
                    modifier = Modifier.weight(1f),
                ) { Text("Share") }
                OutlinedButton(
                    onClick = onSaveToFile,
                    enabled = anySelected,
                    modifier = Modifier.weight(1f),
                ) { Text("Save to File") }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Cancel") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ImportSourceSheet(
    onPaste: () -> Unit,
    onOpenFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Import contributions", style = MaterialTheme.typography.titleLarge)
            Text(
                "Paste a share you received, or open a saved .compendium file.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                ) { Text("Paste text") }
                OutlinedButton(
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f),
                ) { Text("Open file") }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Cancel") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ImportPreviewSheet(
    rows: List<DomainPickerRow<ContributionDomain>>,
    onToggle: (ContributionDomain) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { false },
    )
    val totalCount = rows.sumOf { it.count }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Review import", style = MaterialTheme.typography.titleLarge)
            Text(
                "$totalCount entries in this bundle. Pick which to keep.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ContributionDomainPicker(
                rows = rows,
                onToggle = onToggle,
            )
            val anySelected = rows.any { it.selected && it.enabled }
            Button(
                onClick = onConfirm,
                enabled = anySelected,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import") }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Cancel") }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsContentOffPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SettingsContent(
            themeMode = ThemeMode.System,
            onThemeModeSelected = {},
            dynamicColorAvailable = true,
            dynamicColorEnabled = true,
            onDynamicColorToggled = {},
            essenceContributionsEnabled = false,
            essenceConflictCount = 0,
            onEssenceContributionsToggled = {},
            awakeningStoneContributionsEnabled = false,
            awakeningStoneConflictCount = 0,
            onAwakeningStoneContributionsToggled = {},
            abilityListingContributionsEnabled = false,
            abilityListingConflictCount = 0,
            onAbilityListingContributionsToggled = {},
            statusEffectContributionsEnabled = true,
            statusEffectConflictCount = 0,
            onStatusEffectContributionsToggled = {},
            essencesAsAwakeningStonesEnabled = false,
            onEssencesAsAwakeningStonesToggled = {},
            onExportClick = {},
            onImportClick = {},
            onAboutClick = {},
            ioState = SettingsViewModel.IoState.Idle,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsContentEncodingPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SettingsContent(
            themeMode = ThemeMode.System,
            onThemeModeSelected = {},
            dynamicColorAvailable = true,
            dynamicColorEnabled = true,
            onDynamicColorToggled = {},
            essenceContributionsEnabled = true,
            essenceConflictCount = 0,
            onEssenceContributionsToggled = {},
            awakeningStoneContributionsEnabled = true,
            awakeningStoneConflictCount = 0,
            onAwakeningStoneContributionsToggled = {},
            abilityListingContributionsEnabled = true,
            abilityListingConflictCount = 0,
            onAbilityListingContributionsToggled = {},
            statusEffectContributionsEnabled = true,
            statusEffectConflictCount = 0,
            onStatusEffectContributionsToggled = {},
            essencesAsAwakeningStonesEnabled = false,
            onEssencesAsAwakeningStonesToggled = {},
            onExportClick = {},
            onImportClick = {},
            onAboutClick = {},
            ioState = SettingsViewModel.IoState.Encoding,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsContentConflictPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SettingsContent(
            themeMode = ThemeMode.System,
            onThemeModeSelected = {},
            dynamicColorAvailable = true,
            dynamicColorEnabled = true,
            onDynamicColorToggled = {},
            essenceContributionsEnabled = true,
            essenceConflictCount = 2,
            onEssenceContributionsToggled = {},
            awakeningStoneContributionsEnabled = false,
            awakeningStoneConflictCount = 0,
            onAwakeningStoneContributionsToggled = {},
            abilityListingContributionsEnabled = true,
            abilityListingConflictCount = 1,
            onAbilityListingContributionsToggled = {},
            statusEffectContributionsEnabled = true,
            statusEffectConflictCount = 0,
            onStatusEffectContributionsToggled = {},
            essencesAsAwakeningStonesEnabled = false,
            onEssencesAsAwakeningStonesToggled = {},
            onExportClick = {},
            onImportClick = {},
            onAboutClick = {},
            ioState = SettingsViewModel.IoState.Idle,
        )
    }
}
