package wizardry.compendium.drive.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.preferences.ThemeMode

/**
 * Minimal in-memory implementation used by codec + coordinator tests. Mirrors
 * the production interface exactly; values are exposed as MutableStateFlow so
 * tests can assert on observed state.
 *
 * NOTE: After Task 5 lands the DriveBackup keys on PreferencesRepository, this
 * fake will be extended with driveBackupEnabled / driveBackupAccountEmail.
 */
class FakePreferencesRepository : PreferencesRepository {
    private val _essence = MutableStateFlow(false)
    private val _stone = MutableStateFlow(false)
    private val _listing = MutableStateFlow(false)
    private val _effect = MutableStateFlow(true)
    private val _crossover = MutableStateFlow(false)
    private val _theme = MutableStateFlow(ThemeMode.System)
    private val _dynamic = MutableStateFlow(true)

    override val essenceContributionsEnabled: Flow<Boolean> = _essence
    override val isEssenceContributionsEnabled get() = _essence.value
    override fun setEssenceContributionsEnabled(enabled: Boolean) { _essence.value = enabled }

    override val awakeningStoneContributionsEnabled: Flow<Boolean> = _stone
    override val isAwakeningStoneContributionsEnabled get() = _stone.value
    override fun setAwakeningStoneContributionsEnabled(enabled: Boolean) { _stone.value = enabled }

    override val abilityListingContributionsEnabled: Flow<Boolean> = _listing
    override val isAbilityListingContributionsEnabled get() = _listing.value
    override fun setAbilityListingContributionsEnabled(enabled: Boolean) { _listing.value = enabled }

    override val statusEffectContributionsEnabled: Flow<Boolean> = _effect
    override val isStatusEffectContributionsEnabled get() = _effect.value
    override fun setStatusEffectContributionsEnabled(enabled: Boolean) { _effect.value = enabled }

    override val essencesAsAwakeningStonesEnabled: Flow<Boolean> = _crossover
    override val isEssencesAsAwakeningStonesEnabled get() = _crossover.value
    override fun setEssencesAsAwakeningStonesEnabled(enabled: Boolean) { _crossover.value = enabled }

    override val themeMode: Flow<ThemeMode> = _theme
    override val isCurrentThemeMode get() = _theme.value
    override fun setThemeMode(mode: ThemeMode) { _theme.value = mode }

    override val dynamicColorEnabled: Flow<Boolean> = _dynamic
    override val isDynamicColorEnabled get() = _dynamic.value
    override fun setDynamicColorEnabled(enabled: Boolean) { _dynamic.value = enabled }
}
