package wizardry.compendium.preferences

import kotlinx.coroutines.flow.Flow
import wizardry.compendium.preferences.ThemeMode

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
