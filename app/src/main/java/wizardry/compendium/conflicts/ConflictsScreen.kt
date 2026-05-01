package wizardry.compendium.conflicts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.essences.AbilityListingConflict
import wizardry.compendium.essences.AwakeningStoneConflict
import wizardry.compendium.essences.Conflict
import wizardry.compendium.essences.EssenceConflict

@Composable
fun ConflictsScreen(
    onEditEssenceContribution: (name: String) -> Unit,
    onEditAwakeningStoneContribution: (name: String) -> Unit,
    onEditAbilityListingContribution: (name: String) -> Unit,
    viewModel: ConflictsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var selected by remember { mutableStateOf<Conflict?>(null) }

    if (state.total == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No conflicts to resolve.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.essence.isNotEmpty()) {
            item { GroupHeader("Essences") }
            items(state.essence) { ConflictRow(it) { selected = it } }
        }
        if (state.awakeningStone.isNotEmpty()) {
            item { GroupHeader("Awakening Stones") }
            items(state.awakeningStone) { ConflictRow(it) { selected = it } }
        }
        if (state.abilityListing.isNotEmpty()) {
            item { GroupHeader("Ability Listings") }
            items(state.abilityListing) { ConflictRow(it) { selected = it } }
        }
    }

    val current = selected
    if (current != null) {
        ResolutionDialog(
            conflict = current,
            onDismiss = { selected = null },
            onEdit = {
                val name = current.editTargetName()
                selected = null
                when (current) {
                    is EssenceConflict -> onEditEssenceContribution(name)
                    is AwakeningStoneConflict -> onEditAwakeningStoneContribution(name)
                    is AbilityListingConflict -> onEditAbilityListingContribution(name)
                }
            },
            onDeleteContribution = {
                selected = null
                when (current) {
                    is EssenceConflict.NameCollision -> viewModel.deleteEssenceContribution(current.contribution.name)
                    is EssenceConflict.CombinationCollision -> viewModel.deleteEssenceContribution(current.contribution.name)
                    is AwakeningStoneConflict.NameCollision -> viewModel.deleteAwakeningStoneContribution(current.contribution.name)
                    is AbilityListingConflict.NameCollision -> viewModel.deleteAbilityListingContribution(current.contribution.name)
                }
            },
            onRemoveCombination = if (current is EssenceConflict.CombinationCollision) {
                {
                    selected = null
                    viewModel.removeCombinationFromContribution(current.contribution, current.combination)
                }
            } else null,
        )
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ConflictRow(conflict: Conflict, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = conflict.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = conflict.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResolutionDialog(
    conflict: Conflict,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDeleteContribution: () -> Unit,
    onRemoveCombination: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conflict.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(conflict.summary)
                Text(
                    text = "Choose how to resolve this conflict.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("Edit Contribution")
                }
                if (onRemoveCombination != null) {
                    OutlinedButton(onClick = onRemoveCombination, modifier = Modifier.fillMaxWidth()) {
                        Text("Remove This Combination")
                    }
                }
                OutlinedButton(
                    onClick = onDeleteContribution,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete Contribution")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun Conflict.editTargetName(): String = when (this) {
    is EssenceConflict.NameCollision -> contribution.name
    is EssenceConflict.CombinationCollision -> contribution.name
    is AwakeningStoneConflict.NameCollision -> contribution.name
    is AbilityListingConflict.NameCollision -> contribution.name
    else -> error("Unknown conflict type: $this")
}
