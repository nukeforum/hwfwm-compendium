package wizardry.compendium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LandingScreen(
    onEssenceClicked: () -> Unit,
    onAwakeningStoneClicked: () -> Unit,
    onAbilityListingClicked: () -> Unit,
    onStatusEffectClicked: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelButton(
                label = "Essences",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                onClick = onEssenceClicked,
            )
            PanelButton(
                label = "Awakening Stones",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                onClick = onAwakeningStoneClicked,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PanelButton(
                label = "Ability Listings",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                onClick = onAbilityListingClicked,
            )
            PanelButton(
                label = "Status Effects",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                onClick = onStatusEffectClicked,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PanelButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LandingScreenPreview() {
    LandingScreen(
        onEssenceClicked = {},
        onAwakeningStoneClicked = {},
        onAbilityListingClicked = {},
        onStatusEffectClicked = {},
    )
}
