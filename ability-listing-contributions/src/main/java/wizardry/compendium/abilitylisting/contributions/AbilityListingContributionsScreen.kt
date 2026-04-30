package wizardry.compendium.abilitylisting.contributions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun AbilityListingContributionsScreen(
    viewModel: AbilityListingContributionsViewModel = hiltViewModel(),
) {
    val saveState by viewModel.saveState.collectAsState()
    AbilityListingForm(
        saveState = saveState,
        onSave = { name -> viewModel.saveAbilityListing(name) },
        onClearState = viewModel::clearSaveState,
    )
}

@Composable
private fun AbilityListingForm(
    saveState: AbilityListingContributionsViewModel.SaveState,
    onSave: (name: String) -> Unit,
    onClearState: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        SaveFeedback(saveState = saveState, onClearState = onClearState)

        Button(
            onClick = { onSave(name) },
            modifier = Modifier.fillMaxWidth(),
            enabled = saveState !is AbilityListingContributionsViewModel.SaveState.Saving,
        ) {
            Text("Save Ability Listing")
        }
    }
}

@Composable
private fun SaveFeedback(
    saveState: AbilityListingContributionsViewModel.SaveState,
    onClearState: () -> Unit,
) {
    when (saveState) {
        is AbilityListingContributionsViewModel.SaveState.Success -> {
            LaunchedEffect(saveState) {
                kotlinx.coroutines.delay(2000)
                onClearState()
            }
            Text(
                text = "Saved successfully",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is AbilityListingContributionsViewModel.SaveState.Error -> {
            Text(
                text = saveState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AbilityListingFormIdlePreview() {
    AbilityListingForm(
        saveState = AbilityListingContributionsViewModel.SaveState.Idle,
        onSave = {},
        onClearState = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AbilityListingFormErrorPreview() {
    AbilityListingForm(
        saveState = AbilityListingContributionsViewModel.SaveState.Error("Name cannot be empty"),
        onSave = {},
        onClearState = {},
    )
}
