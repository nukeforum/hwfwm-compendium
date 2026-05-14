package wizardry.compendium.awakeningstoneinfo

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
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
    LaunchedEffect(viewModel) {
        viewModel.shareEvents.collect { event ->
            when (event) {
                is AwakeningStoneDetailViewModel.ShareEvent.Encoded -> fireShareIntent(context, event.text)
            }
        }
    }

    val state by viewModel.state.collectAsState()

    when (val details = state) {
        is AwakeningStoneDetailUiState.Error -> ErrorMessage(details.exception.message ?: "Unable to load awakening stone")
        AwakeningStoneDetailUiState.Loading -> Loading()
        is AwakeningStoneDetailUiState.Success -> {
            onStoneLoaded(details.stone)
            Details(
                state = details,
                onEdit = { onEditContribution(details.stone) },
                onShare = { viewModel.requestShare(details.stone) },
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
) {
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
                OutlinedButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(text = " Share", modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(text = " Edit", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = Dp.Infinity, minHeight = 80.dp)
                .border(1.dp, Color.DarkGray)
                .padding(8.dp)
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center),
                text = state.stone.report()
            )
        }
    }
}

private fun AwakeningStone.report(): String {
    return """
        Item: [$name Awakening Stone]
        (${rank.toString().lowercase()}, ${rarity.toString().lowercase()})

        $description (${properties.joinToString(", ")}).

        ${effects.joinToString { "Effect: ${it.description}" }}
    """.trimIndent()
}

private fun fireShareIntent(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
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
        )
    }
}
