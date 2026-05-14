package wizardry.compendium.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

/**
 * One row in a [ContributionDomainPicker].
 *
 * @param key Caller-defined identifier for this row. Echoed back via
 *   `onToggle`. Settings uses `ContributionDomain` here; tests pass an enum
 *   or any other comparable key.
 * @param label Display name (e.g. "Essences").
 * @param count The numeric count to show in the label.
 * @param countSuffix Suffix text after the count, e.g. "" for export
 *   ("Essences (12)") or " in bundle" for import ("Essences (4 in bundle)").
 * @param selected Whether the checkbox is checked.
 * @param enabled When false, the row is greyed out and taps are ignored.
 *   Used for domains with zero contributions on export, or domains absent
 *   from the bundle on import.
 */
data class DomainPickerRow<K : Any>(
    val key: K,
    val label: String,
    val count: Int,
    val countSuffix: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/**
 * Stateless multi-select list of contribution domains with counts.
 *
 * Visually a vertical column of checkbox rows, each showing
 * `"<label> (<count><suffix>)"`. Disabled rows render greyed and ignore
 * taps. The component is purely presentational — caller owns the row state
 * and reacts to `onToggle` to flip selection.
 */
@Composable
fun <K : Any> ContributionDomainPicker(
    rows: List<DomainPickerRow<K>>,
    onToggle: (K) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            DomainPickerRowItem(row = row, onToggle = onToggle)
        }
    }
}

@Composable
private fun <K : Any> DomainPickerRowItem(
    row: DomainPickerRow<K>,
    onToggle: (K) -> Unit,
) {
    val rowModifier = if (row.enabled) {
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(row.key) }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = row.selected,
            onCheckedChange = if (row.enabled) ({ onToggle(row.key) }) else null,
            enabled = row.enabled,
        )
        Text(
            text = "${row.label} (${row.count}${row.countSuffix})",
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@PreviewLightDark
@Composable
private fun ContributionDomainPickerPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        ContributionDomainPicker(
            rows = listOf(
                DomainPickerRow(key = "essences", label = "Essences", count = 12, countSuffix = "", selected = true, enabled = true),
                DomainPickerRow(key = "confluences", label = "Confluences", count = 3, countSuffix = "", selected = true, enabled = true),
                DomainPickerRow(key = "stones", label = "Awakening Stones", count = 5, countSuffix = "", selected = false, enabled = true),
                DomainPickerRow(key = "listings", label = "Abilities", count = 8, countSuffix = "", selected = true, enabled = true),
                DomainPickerRow(key = "effects", label = "Status Effects", count = 0, countSuffix = "", selected = false, enabled = false),
            ),
            onToggle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
