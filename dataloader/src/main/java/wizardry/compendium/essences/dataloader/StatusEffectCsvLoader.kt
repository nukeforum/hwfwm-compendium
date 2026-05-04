package wizardry.compendium.essences.dataloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.StatusEffect
import wizardry.compendium.essences.model.StatusType
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Reads `assets/status_effects.csv` with header
 * `name,type,stackable,description,properties`.
 *
 * - `type` is the canonical token form `Affliction.Curse` / `Boon.Holy`.
 * - `stackable` is `true` / `false` (case-insensitive).
 * - `properties` is pipe-separated `Property.toString()` tokens. Pipe rather
 *   than comma so descriptions/CSV-style content don't collide with the
 *   column delimiter.
 *
 * Empty file (header-only) returns an empty list — that's the canonical
 * starting state for status effects until canonical data is curated.
 */
class StatusEffectCsvLoader
@Inject constructor(
    private val source: FileStreamSource,
) : StatusEffectDataLoader {

    override suspend fun loadStatusEffectData(): List<StatusEffect> = withContext(Dispatchers.IO) {
        source.getInputStreamFor(STATUS_EFFECT_FILE_NAME).use { stream ->
            val lines = stream.reader().readLines()
            if (lines.size <= 1) return@withContext emptyList()
            lines.drop(1)
                .filter { it.isNotBlank() }
                .map(::parseRow)
                .sortedBy { it.name }
        }
    }

    private fun parseRow(row: String): StatusEffect {
        val cols = row.split(",", limit = 5)
        require(cols.size == 5) { "Malformed status_effects.csv row (expected 5 cols): $row" }
        val (name, typeToken, stackableText, description, propertiesText) = cols
        return StatusEffect(
            name = name,
            type = decodeType(typeToken),
            stackable = stackableText.equals("true", ignoreCase = true),
            description = description,
            properties = if (propertiesText.isBlank()) emptyList()
            else propertiesText.split("|").map { token ->
                propertyByToken[token] ?: error("Unknown Property in status_effects.csv: $token")
            },
        )
    }

    private val propertyByToken: Map<String, Property> by lazy {
        val klass: KClass<Property> = Property::class
        klass.sealedSubclasses
            .mapNotNull { it.objectInstance }
            .associateBy { it.toString() }
    }

    private fun decodeType(token: String): StatusType = when (token) {
        "Affliction.Curse" -> StatusType.Affliction.Curse
        "Affliction.Disease" -> StatusType.Affliction.Disease
        "Affliction.Elemental" -> StatusType.Affliction.Elemental
        "Affliction.Holy" -> StatusType.Affliction.Holy
        "Affliction.Magic" -> StatusType.Affliction.Magic
        "Affliction.Poison" -> StatusType.Affliction.Poison
        "Affliction.Unholy" -> StatusType.Affliction.Unholy
        "Affliction.Wound" -> StatusType.Affliction.Wound
        "Affliction.UnTyped" -> StatusType.Affliction.UnTyped
        "Boon.Holy" -> StatusType.Boon.Holy
        "Boon.Magic" -> StatusType.Boon.Magic
        "Boon.Unholy" -> StatusType.Boon.Unholy
        "Boon.UnTyped" -> StatusType.Boon.UnTyped
        else -> error("Unknown StatusType token in status_effects.csv: $token")
    }

    companion object {
        private const val STATUS_EFFECT_FILE_NAME = "status_effects.csv"
    }
}
