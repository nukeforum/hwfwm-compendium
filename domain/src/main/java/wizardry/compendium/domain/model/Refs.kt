package wizardry.compendium.domain.model

sealed interface AbilityRef {
    data class Canonical(val name: String) : AbilityRef
    data class Contributed(val id: Long) : AbilityRef
}

/**
 * Persisted reference to an Essence. Three variants:
 *
 * - [Canonical] — name lookup against the canonical content (book data).
 * - [Contributed.Manifestation] — id lookup against the user's contributed
 *   `manifestation` table.
 * - [Contributed.Confluence] — id lookup against the user's contributed
 *   `confluence` table.
 *
 * The Manifestation/Confluence split exists because the two contributed
 * tables maintain independent AUTOINCREMENT sequences in SQLite; a single
 * "contr:<id>" form would be ambiguous between manifestation #N and
 * confluence #N (and the v6 wire format was — see migration 6.sqm for the
 * one-time rewrite at v7).
 */
sealed interface EssenceRef {
    data class Canonical(val name: String) : EssenceRef

    sealed interface Contributed : EssenceRef {
        val id: Long

        data class Manifestation(override val id: Long) : Contributed
        data class Confluence(override val id: Long) : Contributed
    }
}

class MalformedRefException(val raw: String) :
    IllegalStateException("Malformed ref: \"$raw\"")

object RefCodec {

    private const val CANON_PREFIX = "canon:"
    private const val CONTR_PREFIX = "contr:"
    private const val CONTR_MANIFESTATION_PREFIX = "mcontr:"
    private const val CONTR_CONFLUENCE_PREFIX = "ccontr:"

    fun encodeAbilityRef(ref: AbilityRef): String = when (ref) {
        is AbilityRef.Canonical -> CANON_PREFIX + ref.name
        is AbilityRef.Contributed -> CONTR_PREFIX + ref.id
    }

    fun decodeAbilityRef(raw: String): AbilityRef = when {
        raw.startsWith(CANON_PREFIX) -> {
            val name = raw.substring(CANON_PREFIX.length)
            if (name.isEmpty()) throw MalformedRefException(raw)
            AbilityRef.Canonical(name)
        }
        raw.startsWith(CONTR_PREFIX) ->
            AbilityRef.Contributed(
                raw.substring(CONTR_PREFIX.length).toLongOrNull()
                    ?: throw MalformedRefException(raw)
            )
        else -> throw MalformedRefException(raw)
    }

    fun encodeEssenceRef(ref: EssenceRef): String = when (ref) {
        is EssenceRef.Canonical -> CANON_PREFIX + ref.name
        is EssenceRef.Contributed.Manifestation -> CONTR_MANIFESTATION_PREFIX + ref.id
        is EssenceRef.Contributed.Confluence -> CONTR_CONFLUENCE_PREFIX + ref.id
    }

    fun decodeEssenceRef(raw: String): EssenceRef = when {
        raw.startsWith(CANON_PREFIX) -> {
            val name = raw.substring(CANON_PREFIX.length)
            if (name.isEmpty()) throw MalformedRefException(raw)
            EssenceRef.Canonical(name)
        }
        raw.startsWith(CONTR_MANIFESTATION_PREFIX) ->
            EssenceRef.Contributed.Manifestation(
                raw.substring(CONTR_MANIFESTATION_PREFIX.length).toLongOrNull()
                    ?: throw MalformedRefException(raw)
            )
        raw.startsWith(CONTR_CONFLUENCE_PREFIX) ->
            EssenceRef.Contributed.Confluence(
                raw.substring(CONTR_CONFLUENCE_PREFIX.length).toLongOrNull()
                    ?: throw MalformedRefException(raw)
            )
        else -> throw MalformedRefException(raw)
    }
}
