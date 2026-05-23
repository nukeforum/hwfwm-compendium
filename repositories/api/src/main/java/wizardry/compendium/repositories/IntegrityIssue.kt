package wizardry.compendium.repositories

sealed interface IntegrityIssue {

    /** A ref column or status-token whose tagged form couldn't be parsed at all. */
    data class MalformedRef(val location: String, val raw: String) : IntegrityIssue

    /** A contr:<id> ref whose target row is absent from the contributions cache. */
    data class OrphanedContributedRef(
        val location: String,
        val id: Long,
        val kind: Kind,
    ) : IntegrityIssue {
        enum class Kind { Ability, Essence }
    }

    /** A canon:<name> ref whose target is absent from the canonical cache. */
    data class OrphanedCanonicalRef(
        val location: String,
        val name: String,
        val kind: Kind,
    ) : IntegrityIssue {
        enum class Kind { Ability, Essence }
    }

    /** A {status:NAME} token in a contributed ability description whose target is missing. */
    data class OrphanedStatusToken(
        val abilityName: String,
        val effectOrdinal: Long,
        val missingStatusName: String,
    ) : IntegrityIssue
}
