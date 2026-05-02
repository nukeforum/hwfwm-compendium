package wizardry.compendium.settings

import android.content.Intent
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
import wizardry.compendium.wire.ImportResult
import wizardry.compendium.wire.ImportSummary

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val essenceContributionsEnabled by viewModel.essenceContributionsEnabled.collectAsState(initial = false)
    val awakeningStoneContributionsEnabled by viewModel.awakeningStoneContributionsEnabled.collectAsState(initial = false)
    val abilityListingContributionsEnabled by viewModel.abilityListingContributionsEnabled.collectAsState(initial = false)
    val essenceConflictCount by viewModel.essenceConflictCount.collectAsState(initial = 0)
    val awakeningStoneConflictCount by viewModel.awakeningStoneConflictCount.collectAsState(initial = 0)
    val abilityListingConflictCount by viewModel.abilityListingConflictCount.collectAsState(initial = 0)
    val ioState by viewModel.ioState.collectAsState()
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }

    // When encoding finishes and the result fits in the share limit, fire
    // the share intent immediately and reset state. Doing this in a
    // LaunchedEffect rather than synchronously in the click handler keeps
    // the ViewModel's Encoding state visible to the UI even for fast
    // encodes.
    LaunchedEffect(ioState) {
        val state = ioState
        if (state is SettingsViewModel.IoState.ReadyToShare) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, state.text)
            }
            context.startActivity(Intent.createChooser(intent, "Share contributions"))
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
        onExportClick = viewModel::beginExport,
        onImportClick = {
            pasteText = ""
            showImportDialog = true
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
                            "Use export-to-file instead (coming soon).",
                    )
                },
                confirmButton = {
                    Button(onClick = viewModel::resetIoState) { Text("OK") }
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
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
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

        HorizontalDivider()

        Text("Backup & Share", style = MaterialTheme.typography.titleMedium)
        Text(
            "Export your contributions as a shareable text blob, or import a share " +
                "from someone else.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onExportClick,
                enabled = ioState !is SettingsViewModel.IoState.Encoding,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (ioState is SettingsViewModel.IoState.Encoding) "Encoding…" else "Share",
                )
            }
            OutlinedButton(
                onClick = onImportClick,
                enabled = ioState !is SettingsViewModel.IoState.Importing,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (ioState is SettingsViewModel.IoState.Importing) "Importing…" else "Import",
                )
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
        onExportClick = {},
        onImportClick = {},
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
        onExportClick = {},
        onImportClick = {},
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
        onExportClick = {},
        onImportClick = {},
        ioState = SettingsViewModel.IoState.Idle,
    )
}
