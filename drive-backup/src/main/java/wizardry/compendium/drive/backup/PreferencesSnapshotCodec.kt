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

    suspend fun apply(snapshot: PreferencesSnapshot) {
        snapshot.essenceContributionsEnabled?.let { value ->
            prefs.setEssenceContributionsEnabled(value)
            prefs.essenceContributionsEnabled.first { it == value }
        }
        snapshot.awakeningStoneContributionsEnabled?.let { value ->
            prefs.setAwakeningStoneContributionsEnabled(value)
            prefs.awakeningStoneContributionsEnabled.first { it == value }
        }
        snapshot.abilityListingContributionsEnabled?.let { value ->
            prefs.setAbilityListingContributionsEnabled(value)
            prefs.abilityListingContributionsEnabled.first { it == value }
        }
        snapshot.statusEffectContributionsEnabled?.let { value ->
            prefs.setStatusEffectContributionsEnabled(value)
            prefs.statusEffectContributionsEnabled.first { it == value }
        }
        snapshot.essencesAsAwakeningStonesEnabled?.let { value ->
            prefs.setEssencesAsAwakeningStonesEnabled(value)
            prefs.essencesAsAwakeningStonesEnabled.first { it == value }
        }
        snapshot.themeMode?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }?.let { mode ->
                prefs.setThemeMode(mode)
                prefs.themeMode.first { it == mode }
            }
        }
        snapshot.dynamicColorEnabled?.let { value ->
            prefs.setDynamicColorEnabled(value)
            prefs.dynamicColorEnabled.first { it == value }
        }
    }
}
