package wizardry.compendium.domain.model

/**
 * A reusable race definition: a [name] plus the race's racial abilities.
 *
 * The abilities are always [Ability.Listing]s whose effects are all typed
 * [AbilityType.RacialAbility] — the same "racial" classification the character
 * build editor uses (see `CharacterBuildContributionsViewModel.racialAbilityCandidates`).
 * This mirrors [CharacterBuild.racialAbilities]; the difference is the count
 * rule: a *finished* race template holds exactly [RACIAL_ABILITY_COUNT] racial
 * abilities.
 *
 * The `count <= RACIAL_ABILITY_COUNT` invariant here is the read-path-safe cap
 * (a stored template may momentarily resolve fewer than six if a referenced
 * ability was deleted). The stronger *exactly six* rule is a save-time business
 * constraint enforced in the contributions ViewModel, so partially-resolved
 * templates never fail construction.
 */
public data class RaceTemplate(
    override val name: String,
    public val racialAbilities: List<Ability.Listing>,
) : Entity {
    init {
        assert(racialAbilities.all { listing -> listing.effects.all { it.type == AbilityType.RacialAbility } })
        assert(racialAbilities.count() <= RACIAL_ABILITY_COUNT)
    }

    public companion object {
        /** The exact number of racial abilities a completed race template must have. */
        public const val RACIAL_ABILITY_COUNT: Int = 6
    }
}
