package wizardry.compendium.characterbuild.contributions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wizardry.compendium.essences.model.ConfluenceSet
import wizardry.compendium.essences.model.Essence
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.ThemeMode

/**
 * Essence picker. Shows Manifestations always; switches to Confluence list when the
 * user is picking their FINAL essence (the other three slots are filled with
 * non-Confluence essences). The segmented control only appears in that final-pick
 * state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssencePickerSheet(
    title: String,
    manifestations: List<Essence.Manifestation>,
    confluences: List<Essence.Confluence>,
    initiallySelected: Essence?,
    showSegmentedControl: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Essence) -> Unit,
    subtitleOf: (Essence) -> String? = { null },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var segmentIndex by remember { mutableStateOf(0) } // 0 = Essence, 1 = Confluence

    val showingConfluences = showSegmentedControl && segmentIndex == 1
    val source: List<Essence> = if (showingConfluences) confluences else manifestations
    val filtered = remember(source, query) {
        if (query.isBlank()) source
        else source.filter { it.name.contains(query, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)

            if (showSegmentedControl) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = segmentIndex == 0,
                        onClick = { segmentIndex = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Essence") }
                    SegmentedButton(
                        selected = segmentIndex == 1,
                        onClick = { segmentIndex = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Confluence") }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 400.dp, max = 600.dp),
            ) {
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (source.isEmpty()) "Nothing to choose from" else "No matches",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filtered, key = { it.name }) { option ->
                            val isSelected = option.name == initiallySelected?.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onConfirm(option) }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    subtitleOf(option)?.let { sub ->
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sampleManifestation(name: String, properties: List<Property> = listOf(Property.Magic)): Essence.Manifestation =
    Essence.Manifestation(
        name = name,
        rank = Rank.Iron,
        rarity = Rarity.Common,
        properties = properties,
        description = "Manifested essence of $name",
        isRestricted = false,
    )

private fun sampleManifestations(): List<Essence.Manifestation> = listOf(
    sampleManifestation("Fire", listOf(Property.Fire)),
    sampleManifestation("Water"),
    sampleManifestation("Wind"),
    sampleManifestation("Earth"),
    sampleManifestation("Light", listOf(Property.Light)),
)

private fun sampleConfluences(): List<Essence.Confluence> = listOf(
    Essence.of(
        "Storm",
        restricted = false,
        ConfluenceSet(
            sampleManifestation("Water"),
            sampleManifestation("Wind"),
            sampleManifestation("Lightning", listOf(Property.Lightning)),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun EssencePickerSheetManifestationsPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        EssencePickerSheet(
            title = "Power essence",
            manifestations = sampleManifestations(),
            confluences = sampleConfluences(),
            initiallySelected = sampleManifestations().first(),
            showSegmentedControl = false,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun EssencePickerSheetWithConfluencePreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        EssencePickerSheet(
            title = "Recovery essence",
            manifestations = sampleManifestations(),
            confluences = sampleConfluences(),
            initiallySelected = null,
            showSegmentedControl = true,
            onDismiss = {},
            onConfirm = {},
            subtitleOf = { if (it is Essence.Confluence) "Includes Water, Wind, Lightning" else null },
        )
    }
}
