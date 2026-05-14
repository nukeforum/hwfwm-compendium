package wizardry.compendium.ability.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import wizardry.compendium.domain.model.Cost
import wizardry.compendium.domain.model.DescriptionSegment
import wizardry.compendium.domain.model.StatusEffect
import wizardry.compendium.domain.model.parseDescription

/**
 * Renders a description template into a styled `AnnotatedString`:
 *  - Literal segments → no style
 *  - Resolved cost/cooldown → primary color
 *  - StatusLink → `[Name]` bold + primary color
 *  - Unresolved tokens → error color (raw token text)
 *
 * Reads the ambient `LocalStatusEffects` to resolve `{status:Name}` tokens.
 */
@Composable
@ReadOnlyComposable
fun annotatedDescription(
    template: String,
    costs: List<Cost>,
    cooldown: String,
    statusEffects: List<StatusEffect> = LocalStatusEffects.current,
    resolvedColor: Color = MaterialTheme.colorScheme.primary,
    errorColor: Color = MaterialTheme.colorScheme.error,
): AnnotatedString = buildAnnotatedString {
    for (segment in parseDescription(template, costs, cooldown, statusEffects)) {
        when (segment) {
            is DescriptionSegment.Literal -> append(segment.text)
            is DescriptionSegment.Resolved ->
                withStyle(SpanStyle(color = resolvedColor, fontWeight = FontWeight.Medium)) {
                    append(segment.value)
                }
            is DescriptionSegment.StatusLink ->
                withStyle(SpanStyle(color = resolvedColor, fontWeight = FontWeight.Bold)) {
                    append("[")
                    append(segment.name)
                    append("]")
                }
            is DescriptionSegment.Unresolved ->
                withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.Medium)) {
                    append(segment.token)
                }
        }
    }
}
