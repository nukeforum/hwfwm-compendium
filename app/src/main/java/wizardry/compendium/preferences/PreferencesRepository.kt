package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.preferences.AbilityListingContributionsToggleFlow
import wizardry.compendium.preferences.AwakeningStoneContributionsToggleFlow
import wizardry.compendium.preferences.EssenceContributionsToggleFlow
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggleFlow
import wizardry.compendium.preferences.StatusEffectContributionsToggleFlow
import wizardry.compendium.preferences.AbilityListingContributionsToggle
import wizardry.compendium.preferences.AwakeningStoneContributionsToggle
import wizardry.compendium.preferences.EssenceContributionsToggle
import wizardry.compendium.preferences.EssencesAsAwakeningStonesToggle
import wizardry.compendium.preferences.StatusEffectContributionsToggle
import wizardry.compendium.ui.theme.ThemeMode

/**
 * Aggregate-style settings facade. Exposes every toggle the app surfaces in
 * the Settings screen: per-domain contribution toggles (also implemented via
 * the smaller per-domain interfaces), the "essences as awakening stones"
 * crossover, theme mode, and dynamic color.
 *
 * Made an interface so unit tests can supply a no-op fake without standing
 * up Robolectric / DataStore. The default DataStore-backed implementation is
 * [DataStorePreferencesRepository].
 */
interface PreferencesRepository :
    EssenceContributionsToggle,
    EssenceContributionsToggleFlow,
    AwakeningStoneContributionsToggle,
    AwakeningStoneContributionsToggleFlow,
    AbilityListingContributionsToggle,
    AbilityListingContributionsToggleFlow,
    StatusEffectContributionsToggle,
    StatusEffectContributionsToggleFlow,
    EssencesAsAwakeningStonesToggle,
    EssencesAsAwakeningStonesToggleFlow {

    val themeMode: Flow<ThemeMode>
    val isCurrentThemeMode: ThemeMode
    val dynamicColorEnabled: Flow<Boolean>
    val isDynamicColorEnabled: Boolean

    fun setEssenceContributionsEnabled(enabled: Boolean)
    fun setAwakeningStoneContributionsEnabled(enabled: Boolean)
    fun setAbilityListingContributionsEnabled(enabled: Boolean)
    fun setStatusEffectContributionsEnabled(enabled: Boolean)
    fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean)
    fun setThemeMode(mode: ThemeMode)
    fun setDynamicColorEnabled(enabled: Boolean)
}
