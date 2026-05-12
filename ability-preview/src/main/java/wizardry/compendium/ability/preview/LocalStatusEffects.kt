package wizardry.compendium.ability.preview

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import wizardry.compendium.essences.model.StatusEffect

/**
 * Status effects available to ability description rendering. Hosts that have
 * access to a `StatusEffectRepository` (e.g., `AbilityDetails`,
 * `AbilityContributionsScreen`) provide this around `AbilityPreview` so
 * that `{status:Name}` tokens can resolve to inline `[Name]` styling and the
 * appended Linked Effects section.
 *
 * Defaults to an empty list — unresolved references will render in error color.
 */
val LocalStatusEffects: ProvidableCompositionLocal<List<StatusEffect>> =
    staticCompositionLocalOf { emptyList() }
