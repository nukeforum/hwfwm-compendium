package wizardry.compendium.abilitylistinginfo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import wizardry.compendium.essences.model.Effect
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.ui.PreviewLightDark
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

/**
 * Horizontally-scrolling row of FilterChips, one per rank that the listing has at
 * least one effect for. Single-select with toggle-off — tapping the selected chip
 * clears the selection (returns to the full description).
 *
 * Renders nothing when fewer than 2 ranks are present.
 */
@Composable
fun RankFilterRow(
    effects: List<Effect.AbilityEffect>,
    selectedRank: Rank?,
    onSelect: (Rank?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranks = remember(effects) {
        effects.map { it.rank }.distinct().sortedBy { it.ordinal }
    }
    if (ranks.size < 2) return

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (rank in ranks) {
            val selected = rank == selectedRank
            FilterChip(
                selected = selected,
                onClick = { onSelect(if (selected) null else rank) },
                label = { Text(text = rank.name) },
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

private class RankFilterRowPreviewParams : PreviewParameterProvider<Rank?> {
    override val values = sequenceOf(null, Rank.Iron, Rank.Silver)
}

@PreviewLightDark
@Composable
private fun RankFilterRowPreview(
    @PreviewParameter(RankFilterRowPreviewParams::class) selected: Rank?,
) {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        val sampleEffects = listOf(Rank.Iron, Rank.Bronze, Rank.Silver, Rank.Gold)
            .map { rank ->
                Effect.AbilityEffect(
                    rank = rank,
                    type = wizardry.compendium.essences.model.AbilityType.Conjuration,
                    properties = emptyList(),
                    cost = emptyList(),
                    cooldown = kotlin.time.Duration.ZERO,
                    description = "sample",
                )
            }
        RankFilterRow(
            effects = sampleEffects,
            selectedRank = selected,
            onSelect = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}
