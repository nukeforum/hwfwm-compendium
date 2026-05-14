package wizardry.compendium.share

/**
 * Result of decoding a paste-buffer for a single contributed entity.
 * The `Loaded` model is what the contribute form pre-fills from;
 * `Failed.reason` is the user-facing error string.
 */
sealed interface DecodedSingle<out T> {
    data class Loaded<T>(val model: T) : DecodedSingle<T>
    data class Failed(val reason: String) : DecodedSingle<Nothing>
}
