package wizardry.compendium.essence.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.share.ConfluenceImportPreview
import wizardry.compendium.share.PreviewCombination
import wizardry.compendium.share.PreviewEssence
import wizardry.compendium.wire.Envelope
import wizardry.compendium.wire.EnvelopeCodec

@Composable
fun ConfluenceReviewSheet(
    preview: ConfluenceImportPreview,
    saving: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val unresolvable = preview.unresolvableNames
    val canSave = !saving && unresolvable.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Import Confluence", style = MaterialTheme.typography.titleLarge)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(preview.confluenceName, style = MaterialTheme.typography.headlineSmall)
            if (preview.isRestricted) {
                Spacer(Modifier.width(8.dp))
                AssistChip(onClick = {}, enabled = false, label = { Text("Restricted") })
            }
        }

        Text("Combinations", style = MaterialTheme.typography.titleMedium)
        preview.combinations.forEach { combo ->
            CombinationRow(combo, unresolvable)
        }

        if (preview.essences.isNotEmpty()) {
            Text("Essences in this share", style = MaterialTheme.typography.titleMedium)
            preview.essences.forEach { essence ->
                EssenceRow(essence)
            }
        }

        if (unresolvable.isNotEmpty()) {
            Text(
                text = "This share references essences that aren't in your library. " +
                    "Cancel and import the essences first.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !saving,
                modifier = Modifier.weight(1f),
            ) { Text("Cancel") }

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.weight(1f),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun CombinationRow(combo: PreviewCombination, unresolvable: Set<String>) {
    val parts = listOf(combo.essence1, combo.essence2, combo.essence3)
    Row(verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { idx, name ->
            if (idx > 0) Text(" + ")
            val isUnresolvable = unresolvable.contains(name.lowercase())
            Text(
                text = if (isUnresolvable) "$name (unknown)" else name,
                color = if (isUnresolvable) MaterialTheme.colorScheme.error else Color.Unspecified,
                fontWeight = if (isUnresolvable) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (combo.isRestricted) {
            Text("  (restricted)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EssenceRow(essence: PreviewEssence) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${essence.name} (${essence.rarity.name.lowercase()})")
        if (essence.isNew) {
            AssistChip(onClick = {}, enabled = false, label = { Text("New") })
        } else {
            AssistChip(onClick = {}, enabled = false, label = { Text("Already in your library") })
        }
    }
}

// region Previews

private val previewTypical = ConfluenceImportPreview(
    envelope = Envelope(version = EnvelopeCodec.CurrentVersion),
    confluenceName = "Doom",
    isRestricted = false,
    combinations = listOf(PreviewCombination("Sin", "Blood", "Dark", false)),
    essences = listOf(
        PreviewEssence("Sin", Rarity.Legendary, isNew = true),
        PreviewEssence("Blood", Rarity.Uncommon, isNew = false),
        PreviewEssence("Dark", Rarity.Rare, isNew = true),
    ),
    unresolvableNames = emptySet(),
)

private val previewUnresolvable = previewTypical.copy(
    combinations = listOf(PreviewCombination("Sin", "Phantom", "Dark", false)),
    unresolvableNames = setOf("phantom"),
)

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetTypicalPreview() {
    ConfluenceReviewSheet(preview = previewTypical, saving = false, onSave = {}, onCancel = {})
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetUnresolvablePreview() {
    ConfluenceReviewSheet(preview = previewUnresolvable, saving = false, onSave = {}, onCancel = {})
}

@Preview(showBackground = true)
@Composable
private fun ConfluenceReviewSheetSavingPreview() {
    ConfluenceReviewSheet(preview = previewTypical, saving = true, onSave = {}, onCancel = {})
}

// endregion
