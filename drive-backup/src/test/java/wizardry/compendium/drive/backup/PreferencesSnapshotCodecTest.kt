package wizardry.compendium.drive.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wizardry.compendium.preferences.ThemeMode

class PreferencesSnapshotCodecTest {

    @Test
    fun `snapshot captures all flag values from prefs`() = runTest {
        val prefs = FakePreferencesRepository().apply {
            setEssenceContributionsEnabled(true)
            setAwakeningStoneContributionsEnabled(false)
            setAbilityListingContributionsEnabled(true)
            setStatusEffectContributionsEnabled(true)
            setEssencesAsAwakeningStonesEnabled(true)
            setThemeMode(ThemeMode.Dark)
            setDynamicColorEnabled(false)
        }
        val codec = PreferencesSnapshotCodec(prefs)
        val snapshot = codec.snapshot()
        assertEquals(true, snapshot.essenceContributionsEnabled)
        assertEquals(false, snapshot.awakeningStoneContributionsEnabled)
        assertEquals("Dark", snapshot.themeMode)
        assertEquals(false, snapshot.dynamicColorEnabled)
    }

    @Test
    fun `apply restores all flag values from snapshot`() = runTest {
        val prefs = FakePreferencesRepository()
        val codec = PreferencesSnapshotCodec(prefs)
        codec.apply(PreferencesSnapshot(
            schemaVersion = 1,
            essenceContributionsEnabled = true,
            statusEffectContributionsEnabled = false,
            themeMode = "Light",
            dynamicColorEnabled = false,
        ))
        assertTrue(prefs.isEssenceContributionsEnabled)
        assertFalse(prefs.isStatusEffectContributionsEnabled)
        assertEquals(ThemeMode.Light, prefs.isCurrentThemeMode)
        assertFalse(prefs.isDynamicColorEnabled)
    }

    @Test
    fun `apply silently ignores null fields (unknown or older snapshot)`() = runTest {
        val prefs = FakePreferencesRepository().apply { setThemeMode(ThemeMode.Dark) }
        val codec = PreferencesSnapshotCodec(prefs)
        codec.apply(PreferencesSnapshot())
        assertEquals(ThemeMode.Dark, prefs.isCurrentThemeMode)
    }

    @Test
    fun `apply ignores unrecognized themeMode string`() = runTest {
        val prefs = FakePreferencesRepository().apply { setThemeMode(ThemeMode.System) }
        val codec = PreferencesSnapshotCodec(prefs)
        codec.apply(PreferencesSnapshot(themeMode = "ChartreuseSparkle"))
        assertEquals(ThemeMode.System, prefs.isCurrentThemeMode)
    }
}
