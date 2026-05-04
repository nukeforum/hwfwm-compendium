package wizardry.compendium.statuseffect.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType

@Composable
fun StatusEffectDetails(
    effectName: String,
    onEffectLoaded: (StatusEffect) -> Unit = {},
    onEditContribution: (StatusEffect) -> Unit = {},
    onShareContribution: (StatusEffect) -> Unit = {},
    viewModel: StatusEffectDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(effectName) { viewModel.load(effectName) }

    val state by viewModel.state.collectAsState()

    when (val s = state) {
        StatusEffectDetailUiState.Loading -> Loading()
        is StatusEffectDetailUiState.Error -> ErrorMessage(s.exception.message ?: "Unable to load status effect")
        is StatusEffectDetailUiState.Success -> {
            LaunchedEffect(s.effect.name) { onEffectLoaded(s.effect) }
            Details(
                state = s,
                onShare = { onShareContribution(s.effect) },
                onEdit = { onEditContribution(s.effect) },
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Loading")
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    state: StatusEffectDetailUiState.Success,
    onShare: () -> Unit,
    onEdit: () -> Unit,
) {
    val effect = state.effect
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = effect.name, style = MaterialTheme.typography.titleLarge)

        Text(
            text = typeText(effect.type),
            style = MaterialTheme.typography.labelMedium,
        )

        if (effect.stackable) {
            SuggestionChip(
                onClick = {},
                label = { Text("Stackable") },
            )
        }

        Text(
            text = effect.description,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (effect.properties.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                effect.properties.forEach { property ->
                    AssistChip(
                        onClick = {},
                        label = { Text(property.toString()) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
            }
            if (state.isContribution) {
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(text = " Edit", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private fun typeText(type: StatusType): String = when (type) {
    is StatusType.Affliction -> "Affliction · ${type::class.simpleName}"
    is StatusType.Boon -> "Boon · ${type::class.simpleName}"
}
