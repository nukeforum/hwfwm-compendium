package wizardry.compendium.drive.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupBundle(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val contributions: String,
    val preferences: PreferencesSnapshot,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String): BackupBundle {
            val parsed = try {
                json.decodeFromString(serializer(), text)
            } catch (e: SerializationException) {
                throw BackupBundleCorruptException(e)
            } catch (e: IllegalArgumentException) {
                throw BackupBundleCorruptException(e)
            }
            if (parsed.schemaVersion > CURRENT_SCHEMA_VERSION) {
                throw BackupBundleUnsupportedVersionException(parsed.schemaVersion)
            }
            return parsed
        }
    }
}

class BackupBundleUnsupportedVersionException(val version: Int) :
    RuntimeException("BackupBundle schemaVersion $version not supported")

class BackupBundleCorruptException(cause: Throwable) :
    RuntimeException("BackupBundle is unreadable", cause)
