package wizardry.compendium.awakeningstoneinfo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import wizardry.compendium.domain.model.AwakeningStone
import wizardry.compendium.domain.model.Rarity
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

@Composable
fun AwakeningStoneDetails(
    stoneName: String,
    onStoneLoaded: (AwakeningStone) -> Unit,
    onEditContribution: (AwakeningStone) -> Unit = {},
    viewModel: AwakeningStoneDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(stoneName) {
        viewModel.load(stoneName)
    }

    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            fireShareIntent(context, event.text, event.title)
        }
    }

    when (val details = state) {
        is AwakeningStoneDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load awakening stone")
        AwakeningStoneDetailUiState.Loading -> Loading()
        is AwakeningStoneDetailUiState.Success -> {
            onStoneLoaded(details.stone)
            Details(
                state = details,
                onEdit = { onEditContribution(details.stone) },
                onShare = { viewModel.requestShareAsText(details.stone) },
                onExport = { viewModel.requestExport(details.stone) },
            )
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }
}

@Composable
private fun Loading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Loading")
    }
}

@Composable
private fun Details(
    state: AwakeningStoneDetailUiState.Success,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            if (state.isContribution) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onExport) {
                        Icon(Icons.Filled.IosShare, contentDescription = null)
                        Text(text = " Export", modifier = Modifier.padding(start = 4.dp))
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedButton(onClick = onShare) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                    .border(1.dp, Color.DarkGray)
                    .padding(8.dp)
            ) {
                Column {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(text = "Item: [${state.stone.name} Awakening Stone]")
                        Text(text = "(${state.stone.rank.toString().lowercase()}, ${state.stone.rarity.toString().lowercase()})")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                        Text(text = "${state.stone.description} (${state.stone.properties.joinToString(", ")}).")
                    }
                    if (state.stone.effects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        state.stone.effects.forEach { effect ->
                            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                Text(text = "Effect: ${effect.description}")
                            }
                        }
                    }
                }
            }
        }

        if (state.isContribution) {
            FloatingActionButton(
                onClick = onEdit,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit awakening stone")
            }
        }
    }
}

private fun fireShareIntent(context: Context, text: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_SUBJECT, title)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

@PreviewLightDark
@Composable
private fun DetailsCanonicalPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = AwakeningStoneDetailUiState.Success(
                stone = AwakeningStone.of(name = "Wind", rarity = Rarity.Common),
                isContribution = false,
            ),
            onEdit = {},
            onShare = {},
            onExport = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun DetailsContributionPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        Details(
            state = AwakeningStoneDetailUiState.Success(
                stone = AwakeningStone.of(name = "Flame", rarity = Rarity.Uncommon),
                isContribution = true,
            ),
            onEdit = {},
            onShare = {},
            onExport = {},
        )
    }
}
