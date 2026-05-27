package wizardry.compendium.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import wizardry.compendium.preferences.ThemeMode
import wizardry.compendium.repositories.DeleteImpact
import wizardry.compendium.ui.theme.CompendiumTheme

/**
 * Confirmation dialog shown by contribute screens before deleting a contribution
 * that other entities reference. Each section of [DeleteImpact] renders as a
 * header + bullet list of names. The optional [explanatoryNote] is rendered at
 * the bottom in small text — used by status-effect deletes to spell out
 * irreversibility ({status:NAME} tokens can't be auto-restored).
 *
 * Callers SHOULD short-circuit when [DeleteImpact.isEmpty] is true and call
 * the delete action directly without showing this dialog.
 */
@Composable
fun DeleteWithReferencesDialog(
    contributionName: String,
    impact: DeleteImpact,
    explanatoryNote: String? = null,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete anyway") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        title = {
            Text(
                text = "Delete \"$contributionName\"?",
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (impact.referencingBuilds.isNotEmpty()) {
                    Text(
                        text = "Builds that reference this:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    impact.referencingBuilds.forEach { Text(text = it) }
                }
                if (impact.referencingConfluenceSets.isNotEmpty()) {
                    Text(
                        text = "Confluence sets that include this:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    impact.referencingConfluenceSets.forEach { Text(text = it) }
                }
                if (impact.referencingAbilityListings.isNotEmpty()) {
                    Text(
                        text = "Abilities whose descriptions reference this:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    impact.referencingAbilityListings.forEach { Text(text = it) }
                }
                if (explanatoryNote != null) {
                    Text(
                        text = explanatoryNote,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        modifier = Modifier,
    )
}

@PreviewLightDark
@Composable
private fun DeleteWithReferencesDialogPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        DeleteWithReferencesDialog(
            contributionName = "Flame Shroud",
            impact = DeleteImpact(
                referencingBuilds = listOf("Fire Mage Build", "Pyromancer Starter"),
                referencingConfluenceSets = listOf("Inferno Confluence"),
                referencingAbilityListings = listOf("Burning Aura"),
            ),
            explanatoryNote = "Deleting this status effect will break {status:Flame Shroud} tokens in ability descriptions. These tokens cannot be automatically restored.",
            onCancel = {},
            onConfirm = {},
        )
    }
}
