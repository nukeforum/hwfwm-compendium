package wizardry.compendium.drive.backup

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupBundleTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `roundtrip preserves all fields`() {
        val bundle = BackupBundle(
            schemaVersion = 1,
            contributions = "envelope-text",
            preferences = PreferencesSnapshot(
                schemaVersion = 1,
                essenceContributionsEnabled = true,
                awakeningStoneContributionsEnabled = false,
                abilityListingContributionsEnabled = true,
                statusEffectContributionsEnabled = true,
                essencesAsAwakeningStonesEnabled = false,
                themeMode = "Dark",
                dynamicColorEnabled = true,
            ),
        )
        val text = json.encodeToString(BackupBundle.serializer(), bundle)
        val decoded = json.decodeFromString(BackupBundle.serializer(), text)
        assertEquals(bundle, decoded)
    }

    @Test(expected = BackupBundleUnsupportedVersionException::class)
    fun `decode rejects schemaVersion above supported`() {
        val text = """{"schemaVersion":99,"contributions":"x","preferences":{"schemaVersion":1}}"""
        BackupBundle.decode(text)
    }

    @Test(expected = BackupBundleCorruptException::class)
    fun `decode rejects truncated json`() {
        BackupBundle.decode("{\"schemaVersion\":1,\"contri")
    }

    @Test
    fun `decode tolerates unknown top-level keys`() {
        val text = """{"schemaVersion":1,"contributions":"x","preferences":{"schemaVersion":1},"future":"ok"}"""
        val bundle = BackupBundle.decode(text)
        assertEquals("x", bundle.contributions)
    }
}
