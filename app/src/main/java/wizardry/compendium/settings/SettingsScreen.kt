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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.wire.ImportSummary

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val essenceContributionsEnabled by viewModel.essenceContributionsEnabled.collectAsState(initial = false)
    val awakeningStoneContributionsEnabled by viewModel.awakeningStoneContributionsEnabled.collectAsState(initial = false)
    val abilityListingContributionsEnabled by viewModel.abilityListingContributionsEnabled.collectAsState(initial = false)
    val statusEffectContributionsEnabled by viewModel.statusEffectContributionsEnabled.collectAsState(initial = true)
    val essenceConflictCount by viewModel.essenceConflictCount.collectAsState(initial = 0)
    val awakeningStoneConflictCount by viewModel.awakeningStoneConflictCount.collectAsState(initial = 0)
    val abilityListingConflictCount by viewModel.abilityListingConflictCount.collectAsState(initial = 0)
    val statusEffectConflictCount by viewModel.statusEffectConflictCount.collectAsState(initial = 0)
    val ioState by viewModel.ioState.collectAsState()
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // Tracks whether the user requested file-export. When non-null, an
    // encoder result should be written to this URI rather than fired into
    // a share intent. Cleared once the write completes.
    var pendingExportUri by remember { mutableStateOf<Uri?>(null) }

    // SAF launcher: "Save to File". `text/plain` MIME so any text editor
    // can open the file. The default filename `contributions.compendium`
    // is suggested but the user can change it in the picker.
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingExportUri = uri
            viewModel.beginExport()
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
                viewModel.importFromText("")  // surface the empty-paste error path
                return@rememberLauncherForActivityResult
            }
            viewModel.importFromText(text)
        }
    }

    // When encoding finishes and the result fits in the share limit, route
    // it to either the share intent OR the pending file URI based on what
    // the user originally clicked. Doing the routing in a LaunchedEffect
    // keeps the ViewModel ignorant of which transport was chosen.
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

    SettingsContent(
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
        onShareClick = viewModel::beginExport,
        onSaveToFileClick = {
            // Suggest a default filename; the user can change it.
            createDocumentLauncher.launch("contributions.compendium")
        },
        onPasteClick = {
            pasteText = ""
            showImportDialog = true
        },
        onOpenFileClick = {
            // Don't constrain to a single MIME type — `*/*` would be too
            // permissive but `text/*` covers .txt, .compendium-as-text-plain,
            // etc. without dragging in binary picker entries.
            openDocumentLauncher.launch(arrayOf("text/*"))
        },
        ioState = ioState,
    )

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Contributions") },
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
                    showImportDialog = false
                    viewModel.importFromText(pasteText)
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
        )
    }

    when (val state = ioState) {
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
}

@Composable
fun SettingsContent(
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
    onShareClick: () -> Unit,
    onSaveToFileClick: () -> Unit,
    onPasteClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    ioState: SettingsViewModel.IoState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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

        Text("Backup & Share", style = MaterialTheme.typography.titleMedium)
        Text(
            "Share your contributions as a text blob (Discord, email, etc.) or " +
                "save them to a file for larger backups.",
            style = MaterialTheme.typography.bodySmall,
        )
        val encoding = ioState is SettingsViewModel.IoState.Encoding
        val importing = ioState is SettingsViewModel.IoState.Importing
        Text("Export", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onShareClick,
                enabled = !encoding,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (encoding) "Encoding…" else "Share")
            }
            OutlinedButton(
                onClick = onSaveToFileClick,
                enabled = !encoding,
                modifier = Modifier.weight(1f),
            ) {
                Text("Save to File")
            }
        }
        Text("Import", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPasteClick,
                enabled = !importing,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (importing) "Importing…" else "Paste")
            }
            OutlinedButton(
                onClick = onOpenFileClick,
                enabled = !importing,
                modifier = Modifier.weight(1f),
            ) {
                Text("Open File")
            }
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

@Preview(showBackground = true)
@Composable
private fun SettingsContentOffPreview() {
    SettingsContent(
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
        onShareClick = {},
        onSaveToFileClick = {},
        onPasteClick = {},
        onOpenFileClick = {},
        ioState = SettingsViewModel.IoState.Idle,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentEncodingPreview() {
    SettingsContent(
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
        onShareClick = {},
        onSaveToFileClick = {},
        onPasteClick = {},
        onOpenFileClick = {},
        ioState = SettingsViewModel.IoState.Encoding,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentConflictPreview() {
    SettingsContent(
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
        onShareClick = {},
        onSaveToFileClick = {},
        onPasteClick = {},
        onOpenFileClick = {},
        ioState = SettingsViewModel.IoState.Idle,
    )
}
