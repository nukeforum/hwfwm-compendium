package wizardry.compendium.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.ui.theme.ThemeMode

@Composable
fun AboutScreen(versionName: String) {
    AboutContent(versionName = versionName)
}

@Composable
fun AboutContent(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("HWFWM Compendium", style = MaterialTheme.typography.titleLarge)
            Text("Version $versionName", style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "An Android reference app for the magical system from the " +
                "He Who Fights With Monsters book series. Browse essences " +
                "and their abilities, look up awakening stones, roll " +
                "randomized essence sets, and contribute corrections or " +
                "additions when the canonical data falls short.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            "This is an unofficial fan project. It is not affiliated with, " +
                "endorsed by, or licensed by the author or publisher.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text("Credits", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Shirtaloon (Travis Deverell) — author of He Who Fights With " +
                    "Monsters. The magical system, essences, abilities, " +
                    "awakening stones, and all related lore are his creation. " +
                    "Go read the books.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "llamawaffles555 (Discord) — aggregated and maintained the " +
                    "spreadsheet of essences, abilities, and awakening stones " +
                    "that seeds this app's canonical data.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        HorizontalDivider()

        Text("License", style = MaterialTheme.typography.titleMedium)
        Text(
            "Source code released under the MIT License. The underlying " +
                "setting, characters, and magical system belong to Shirtaloon.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@PreviewLightDark
@Composable
private fun AboutContentPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        AboutContent(versionName = "1.0")
    }
}
