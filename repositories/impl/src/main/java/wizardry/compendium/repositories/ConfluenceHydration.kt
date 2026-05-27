package wizardry.compendium.repositories

import wizardry.compendium.domain.model.ConfluenceSet
import wizardry.compendium.domain.model.Essence
import wizardry.compendium.domain.model.MalformedRefException
import wizardry.compendium.persistence.IdentifiedConfluence

/**
 * Shared hydration helper for [IdentifiedConfluence] -> [Essence.Confluence]
 * conversion. Both [DefaultEssenceRepository] (when reading the contributions
 * cache as part of a merged essence list) and [DefaultCharacterBuildRepository]
 * (when resolving a build attribute slot pointing at a contributed confluence
 * id) need to walk a confluence's three-member sets and decode each member
 * ref into a Manifestation.
 *
 * The caller supplies a [resolveMember] lambda that decodes a single tagged
 * ref string into a [Essence.Manifestation] or null. The lambda owns the
 * RefCodec call and the lookup maps; this helper owns the set-filtering
 * (drop sets whose three members don't all resolve) and the confluence-
 * dropping (drop confluences with no remaining sets) policy.
 *
 * [MalformedRefException] thrown by the lambda is caught here and treated
 * as an unresolvable ref. Returns null when the confluence has no surviving
 * sets, otherwise an [Essence.Confluence] composed of the resolved members.
 */
internal fun hydrateConfluence(
    raw: IdentifiedConfluence,
    resolveMember: (ref: String) -> Essence.Manifestation?,
): Essence.Confluence? {
    val sets = raw.sets.mapNotNull { setRow ->
        val members = listOf(setRow.essence1Ref, setRow.essence2Ref, setRow.essence3Ref)
            .mapNotNull { ref ->
                try {
                    resolveMember(ref)
                } catch (e: MalformedRefException) {
                    null
                }
            }
        if (members.size != 3) {
            null
        } else {
            ConfluenceSet(set = members.toSet(), isRestricted = setRow.isRestricted)
        }
    }
    if (sets.isEmpty()) return null
    return Essence.of(
        name = raw.name,
        restricted = raw.isRestricted,
        confluences = sets.toTypedArray(),
    )
}
