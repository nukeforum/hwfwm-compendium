package wizardry.compendium.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `fromStoredValue returns System for null`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStoredValue(null))
    }

    @Test
    fun `fromStoredValue returns System for unknown`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStoredValue("Sepia"))
    }

    @Test
    fun `fromStoredValue parses System`() {
        assertEquals(ThemeMode.System, ThemeMode.fromStoredValue("System"))
    }

    @Test
    fun `fromStoredValue parses Light`() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStoredValue("Light"))
    }

    @Test
    fun `fromStoredValue parses Dark`() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromStoredValue("Dark"))
    }

    @Test
    fun `storedValue round-trips`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStoredValue(mode.storedValue))
        }
    }
}
