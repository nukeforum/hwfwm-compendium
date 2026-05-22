package wizardry.compendium.domain.model

sealed interface AbilityRef {
    data class Canonical(val name: String) : AbilityRef
    data class Contributed(val id: Long) : AbilityRef
}

sealed interface EssenceRef {
    data class Canonical(val name: String) : EssenceRef
    data class Contributed(val id: Long) : EssenceRef
}

class MalformedRefException(val raw: String) :
    IllegalStateException("Malformed ref: \"$raw\"")

object RefCodec {

    private const val CANON_PREFIX = "canon:"
    private const val CONTR_PREFIX = "contr:"

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
        is EssenceRef.Contributed -> CONTR_PREFIX + ref.id
    }

    fun decodeEssenceRef(raw: String): EssenceRef = when {
        raw.startsWith(CANON_PREFIX) -> {
            val name = raw.substring(CANON_PREFIX.length)
            if (name.isEmpty()) throw MalformedRefException(raw)
            EssenceRef.Canonical(name)
        }
        raw.startsWith(CONTR_PREFIX) ->
            EssenceRef.Contributed(
                raw.substring(CONTR_PREFIX.length).toLongOrNull()
                    ?: throw MalformedRefException(raw)
            )
        else -> throw MalformedRefException(raw)
    }
}
