package wizardry.compendium.statuseffect.details

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.domain.model.Property
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.StatusType
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

@Composable
fun StatusEffectDetails(
    effectName: String,
    onEffectLoaded: (StatusEffect) -> Unit = {},
    onEditContribution: (StatusEffect) -> Unit = {},
    viewModel: StatusEffectDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(effectName) { viewModel.load(effectName) }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            when (event) {
                is StatusEffectDetailViewModel.ShareEvent.Encoded -> fireShareIntent(context, event.text)
            }
        }
    }

    val state by viewModel.state.collectAsState()

    when (val s = state) {
        StatusEffectDetailUiState.Loading -> Loading()
        is StatusEffectDetailUiState.Error -> ErrorMessage(s.exception.message ?: "Unable to load status effect")
        is StatusEffectDetailUiState.Success -> {
            LaunchedEffect(s.effect.name) { onEffectLoaded(s.effect) }
            Details(
                state = s,
                onShare = { viewModel.requestShare(s.effect) },
                onEdit = { onEditContribution(s.effect) },
            )
        }
    }
}

private fun fireShareIntent(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
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

@PreviewLightDark
@Composable
private fun DetailsAfflictionPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = StatusEffectDetailUiState.Success(
                effect = StatusEffect(
                    name = "Burning",
                    type = StatusType.Affliction.Elemental,
                    properties = listOf(Property.Fire, Property.DamageOverTime),
                    stackable = true,
                    description = "Inflicts fire damage every second while the affliction is active.",
                ),
                isContribution = false,
            ),
            onShare = {},
            onEdit = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DetailsBoonContributionPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = StatusEffectDetailUiState.Success(
                effect = StatusEffect(
                    name = "Inner Fire",
                    type = StatusType.Boon.Magic,
                    properties = emptyList(),
                    stackable = false,
                    description = "Restores mana over time.",
                ),
                isContribution = true,
            ),
            onShare = {},
            onEdit = {},
        )
    }
}
