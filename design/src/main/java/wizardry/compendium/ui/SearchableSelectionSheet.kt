package wizardry.compendium.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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

/**
 * Bottom-sheet picker reused across the build editor for essence, ability, and
 * racial-ability selection. Filters [options] by case-insensitive substring on
 * [labelOf].
 *
 * @param multiSelect when true, taps toggle membership in the selection (capped at
 *   [maxSelections]); the user confirms with the Done button. When false, tap
 *   immediately confirms a single selection and dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchableSelectionSheet(
    title: String,
    options: List<T>,
    initiallySelected: Set<T>,
    multiSelect: Boolean,
    maxSelections: Int,
    labelOf: (T) -> String,
    sublabelOf: (T) -> String? = { null },
    onDismiss: () -> Unit,
    onConfirm: (Set<T>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initiallySelected) }

    val filtered = remember(options, query) {
        if (query.isBlank()) options
        else options.filter { labelOf(it).contains(query, ignoreCase = true) }
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (options.isEmpty()) "Nothing to choose from" else "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filtered, key = { labelOf(it) }) { option ->
                        val isSelected = option in selected
                        val canSelectMore = selected.size < maxSelections
                        val rowEnabled = isSelected || canSelectMore || !multiSelect

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = rowEnabled) {
                                    if (multiSelect) {
                                        selected = if (isSelected) selected - option
                                        else selected + option
                                    } else {
                                        onConfirm(setOf(option))
                                    }
                                }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = labelOf(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (rowEnabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.outline,
                                )
                                sublabelOf(option)?.let {
                                    Text(
                                        text = it,
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

            if (multiSelect) {
                Button(
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done (${selected.size}${if (maxSelections < Int.MAX_VALUE) "/$maxSelections" else ""})")
                }
            }
        }
    }
}
