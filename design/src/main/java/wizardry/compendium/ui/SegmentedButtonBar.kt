package wizardry.compendium.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import wizardry.compendium.ui.theme.CompendiumTheme
import wizardry.compendium.preferences.ThemeMode

/**
 * One action segment within a [SegmentedButtonBar]. Behaviourally equivalent to
 * a plain button — [label] is the visible text and [onClick] fires when the
 * segment is tapped; a disabled segment dims and ignores taps.
 */
data class ButtonSegment(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/**
 * A horizontal group of action [segments] rendered as a single connected control
 * in the style of a classic Web 2.0 navigation bar: one rounded outer outline,
 * pill-rounded ends, and hairline dividers sharing the borders between segments.
 *
 * Purely presentational — each segment keeps its own [ButtonSegment.onClick] and
 * [ButtonSegment.enabled] behaviour, so this is a drop-in restyle for a row of
 * equally-weighted buttons.
 */
@Composable
fun SegmentedButtonBar(
    segments: List<ButtonSegment>,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val shape = RoundedCornerShape(percent = 50)
    val outline = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, outline),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.Start,
        ) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) {
                    VerticalDivider(color = outline)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (segment.enabled) {
                                Modifier.clickable(onClick = segment.onClick)
                            } else {
                                Modifier.alpha(0.38f)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = segment.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SegmentedButtonBarPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SegmentedButtonBar(
            segments = listOf(
                ButtonSegment(label = "Export…", onClick = {}),
                ButtonSegment(label = "Import…", onClick = {}),
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun SegmentedButtonBarDisabledPreview() {
    CompendiumTheme(themeMode = ThemeMode.System, dynamicColor = false) {
        SegmentedButtonBar(
            segments = listOf(
                ButtonSegment(label = "Back up now", onClick = {}),
                ButtonSegment(label = "Restore from Drive", onClick = {}, enabled = false),
            ),
        )
    }
}
