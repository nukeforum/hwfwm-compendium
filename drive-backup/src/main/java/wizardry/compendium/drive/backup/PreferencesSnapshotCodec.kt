package wizardry.compendium.drive.backup

import kotlinx.coroutines.flow.first
import wizardry.compendium.preferences.PreferencesRepository
import wizardry.compendium.preferences.ThemeMode
import javax.inject.Inject

class PreferencesSnapshotCodec @Inject constructor(
    private val prefs: PreferencesRepository,
) {
    suspend fun snapshot(): PreferencesSnapshot = PreferencesSnapshot(
        schemaVersion = PreferencesSnapshot.CURRENT_SCHEMA_VERSION,
        essenceContributionsEnabled = prefs.essenceContributionsEnabled.first(),
        awakeningStoneContributionsEnabled = prefs.awakeningStoneContributionsEnabled.first(),
        abilityListingContributionsEnabled = prefs.abilityListingContributionsEnabled.first(),
        statusEffectContributionsEnabled = prefs.statusEffectContributionsEnabled.first(),
        essencesAsAwakeningStonesEnabled = prefs.essencesAsAwakeningStonesEnabled.first(),
        themeMode = prefs.themeMode.first().name,
        dynamicColorEnabled = prefs.dynamicColorEnabled.first(),
    )

    fun apply(snapshot: PreferencesSnapshot) {
        snapshot.essenceContributionsEnabled?.let(prefs::setEssenceContributionsEnabled)
        snapshot.awakeningStoneContributionsEnabled?.let(prefs::setAwakeningStoneContributionsEnabled)
        snapshot.abilityListingContributionsEnabled?.let(prefs::setAbilityListingContributionsEnabled)
        snapshot.statusEffectContributionsEnabled?.let(prefs::setStatusEffectContributionsEnabled)
        snapshot.essencesAsAwakeningStonesEnabled?.let(prefs::setEssencesAsAwakeningStonesEnabled)
        snapshot.themeMode?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }?.let(prefs::setThemeMode)
        }
        snapshot.dynamicColorEnabled?.let(prefs::setDynamicColorEnabled)
    }
}
