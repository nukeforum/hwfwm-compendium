package wizardry.compendium.wire

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import wizardry.compendium.wire.generated.WireMigrators
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Encodes and decodes the contribution import/export wire envelope.
 *
 * # Pipeline
 *
 * Encode:
 *
 *     Envelope --(JSON.encodeToString)--> JSON text
 *              --(GZIPOutputStream)-----> gzip bytes
 *              --(Base64.encodeToString)> base64 string
 *
 * Decode:
 *
 *     base64 string --(Base64.decode)----> gzip bytes
 *                   --(GZIPInputStream)--> JSON text
 *                   --(parseToJsonObject)> JsonObject
 *                   --(migrators)--------> JsonObject (current version)
 *                   --(decodeFromJson)--> Envelope
 *
 * The migrator chain runs strictly between parse and decode so that old
 * envelopes never see the typed `Envelope` data class. The data class only
 * describes the *current* version; older versions are normalized to it.
 *
 * # Why we expose `EncodeResult` rather than a plain string
 *
 * Callers need to know the byte size to enforce the share-size ceiling
 * before handing the string to an Android Intent. We could compute size
 * after the fact, but bundling encode + size into one return value avoids
 * accidental skip of the check.
 *
 * # Why `String` for share, not `ByteArray`
 *
 * `ACTION_SEND` `EXTRA_TEXT` is `CharSequence`. Base64 is text-safe and
 * keeps the share path uniform with paste-from-clipboard. The export-to-
 * file path can also use the same string (since it's text-safe), keeping
 * one canonical wire form. A future tier could add a raw-gzip file form
 * to save 33% — defer until we feel the size pain.
 *
 * # Failure modes
 *
 * - Decoding a base64 string that isn't valid base64 → `IllegalArgumentException`
 * - Decoding gzip that isn't valid gzip → `java.util.zip.ZipException` from GZIPInputStream
 * - Decoding JSON that isn't a valid envelope → `kotlinx.serialization.SerializationException`
 * - Migrator throws (non-mechanical migrator stub not yet filled in) → `NotImplementedError`
 * - Envelope has unknown wire version (newer than this build supports) →
 *   `WireVersionUnsupported` (sealed in this file)
 *
 * Callers (the importer) should catch these and surface user-facing errors.
 */
object EnvelopeCodec {

    /**
     * Hard cap on the base64-encoded output size for the *share* path.
     * Above this, callers should fall back to file export.
     *
     * Rationale: Android binder transactions soft-cap around 1 MB total IPC
     * but practical text-share targets (Discord, IMEs, clipboards) clip well
     * below that. 100 KB is empirically safe across most receivers.
     */
    const val ShareSizeLimitBytes: Int = 100 * 1024

    /**
     * The wire format version this build emits. New envelopes go out with
     * this version; incoming envelopes with an older version are migrated
     * up; envelopes with a newer version are rejected.
     */
    const val CurrentVersion: Int = 1

    private val json = Json {
        // Single source of truth for "skip falsy / empty defaults":
        encodeDefaults = false
        // Tolerate unknown keys so adding a new optional field doesn't break
        // older readers (the additive part of "evolution without migrators").
        ignoreUnknownKeys = true
        // Don't pretty-print for the wire — every byte counts.
        prettyPrint = false
    }

    /**
     * Result of an encode call.
     */
    data class EncodeResult(
        val text: String,
        val byteSize: Int,
    ) {
        val fitsInShareLimit: Boolean get() = byteSize <= ShareSizeLimitBytes
    }

    fun encode(envelope: Envelope): EncodeResult {
        require(envelope.version == CurrentVersion) {
            "Envelope version ${envelope.version} doesn't match codec's CurrentVersion $CurrentVersion. " +
                "Construct envelopes with `version = EnvelopeCodec.CurrentVersion`."
        }
        val jsonText = json.encodeToString(Envelope.serializer(), envelope)
        val gzipped = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { gz -> gz.write(jsonText.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val base64 = Base64.getEncoder().encodeToString(gzipped)
        return EncodeResult(text = base64, byteSize = base64.length)
    }

    fun decode(text: String): Envelope {
        val trimmed = text.trim()
        val gzipBytes = try {
            Base64.getDecoder().decode(trimmed)
        } catch (e: IllegalArgumentException) {
            throw WireDecodeException("Input is not valid base64", e)
        }
        val jsonText = try {
            ByteArrayInputStream(gzipBytes).use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            throw WireDecodeException("Gzip decompression failed (input is not a wire envelope)", e)
        }

        val parsed = try {
            json.parseToJsonElement(jsonText) as? JsonObject
                ?: throw WireDecodeException("Wire payload is not a JSON object")
        } catch (e: Exception) {
            if (e is WireDecodeException) throw e
            throw WireDecodeException("Wire payload is not valid JSON", e)
        }

        val incomingVersion = (parsed["v"]?.toString()?.toIntOrNull())
            ?: throw WireDecodeException("Wire payload missing required `v` (version) field")

        if (incomingVersion > CurrentVersion) {
            throw WireVersionUnsupported(incoming = incomingVersion, current = CurrentVersion)
        }

        // Run migrators in order from `incomingVersion` up to `CurrentVersion`.
        // The generated WireMigrators registry lists every consecutive pair;
        // we filter to those we need.
        var currentJson = parsed
        for (migrator in WireMigrators.all.filter { it.from >= incomingVersion && it.to <= CurrentVersion }) {
            currentJson = migrator.migrate(currentJson)
        }

        return try {
            json.decodeFromJsonElement(Envelope.serializer(), currentJson)
        } catch (e: Exception) {
            throw WireDecodeException("Wire payload didn't match the v$CurrentVersion envelope shape", e)
        }
    }
}

/**
 * Thrown by `EnvelopeCodec.decode` when the input can't be parsed for any
 * reason: invalid base64, bad gzip, malformed JSON, doesn't match the
 * envelope shape. Catch and surface a user-friendly error in the importer.
 */
class WireDecodeException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when an envelope declares a wire version newer than this build's
 * `CurrentVersion`. This means the receiver is behind — they need to update
 * the app before they can import this share.
 *
 * Distinct from `WireDecodeException` because the user-facing message is
 * specific ("update your app") rather than generic.
 */
class WireVersionUnsupported(
    val incoming: Int,
    val current: Int,
) : Exception("Wire envelope is v$incoming but this build supports up to v$current; please update the app")
