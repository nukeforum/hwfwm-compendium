package wizardry.compendium.drive.backup

import kotlinx.serialization.Serializable

@Serializable
data class PreferencesSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val essenceContributionsEnabled: Boolean? = null,
    val awakeningStoneContributionsEnabled: Boolean? = null,
    val abilityListingContributionsEnabled: Boolean? = null,
    val statusEffectContributionsEnabled: Boolean? = null,
    val essencesAsAwakeningStonesEnabled: Boolean? = null,
    val themeMode: String? = null,
    val dynamicColorEnabled: Boolean? = null,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
