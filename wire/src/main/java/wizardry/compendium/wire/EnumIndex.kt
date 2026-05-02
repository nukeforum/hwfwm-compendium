package wizardry.compendium.wire

import wizardry.compendium.essences.model.AbilityType
import wizardry.compendium.essences.model.Amount
import wizardry.compendium.essences.model.Property
import wizardry.compendium.essences.model.Rank
import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.essences.model.Resource

/**
 * Wire-format integer indices for the model enums and sealed-interface
 * "enums" used by the wire format.
 *
 * # Stability
 *
 * Per `docs/contributions-import-export.md`, these tables are part of the
 * wire contract. Adding a new value at the END is non-breaking. Reordering
 * or removing values is a wire-version bump and requires a migrator.
 *
 * Today, this file is hand-maintained — if you add a value to one of these
 * sealed types or enums, append it here too. The KSP processor's @WireEnum
 * lock currently does NOT cover dependency-module declarations (the model
 * lives in `:essences`); a future tier will extend the processor to scan
 * a configurable list of FQNs from dependency modules and lock the order
 * automatically. Until then, code review is the gate.
 *
 * # Why a single file rather than a sub-table per enum
 *
 * Convenience for the human reviewer. When evaluating a model change that
 * touches one of these enums, the reviewer wants the wire impact in one
 * place. Splitting per-enum across files would just spread the cognitive
 * load. The cost is one ~200-line file; cheap.
 *
 * # Why two-direction lookup helpers
 *
 * Both encode (`X.toIndex()`) and decode (`xFromIndex(i)`) need to be
 * cheap and infallible-when-valid. We materialize the table as a
 * `List<X>` and use `indexOf` / `[i]`. `indexOf` is O(N) for sealed
 * subtypes; small N (max 52 for Property), no measurable cost.
 */
internal object EnumIndex {

    // -------------------------------------------------------------------------
    // Rarity
    // -------------------------------------------------------------------------

    private val RARITY_TABLE: List<Rarity> = listOf(
        Rarity.Common,
        Rarity.Uncommon,
        Rarity.Rare,
        Rarity.Epic,
        Rarity.Legendary,
        Rarity.Unknown,
    )

    fun Rarity.toIndex(): Int = ordinal
    fun rarityFromIndex(i: Int): Rarity = requireRange(RARITY_TABLE, i, "Rarity")

    // -------------------------------------------------------------------------
    // Rank
    // -------------------------------------------------------------------------

    private val RANK_TABLE: List<Rank> = listOf(
        Rank.Unranked,
        Rank.Iron,
        Rank.Bronze,
        Rank.Silver,
        Rank.Gold,
        Rank.Diamond,
        Rank.Transcendent,
    )

    fun Rank.toIndex(): Int = ordinal
    fun rankFromIndex(i: Int): Rank = requireRange(RANK_TABLE, i, "Rank")

    // -------------------------------------------------------------------------
    // AbilityType (sealed interface)
    // -------------------------------------------------------------------------

    private val ABILITY_TYPE_TABLE: List<AbilityType> = listOf(
        AbilityType.SpecialAttack,
        AbilityType.SpecialAbility,
        AbilityType.RacialAbility,
        AbilityType.Spell,
        AbilityType.Aura,
        AbilityType.Conjuration,
        AbilityType.Familiar,
        AbilityType.Summoning,
        AbilityType.Use,
    )

    fun AbilityType.toIndex(): Int = ABILITY_TYPE_TABLE.indexOf(this).also {
        require(it >= 0) { "AbilityType $this is not in the wire index table" }
    }
    fun abilityTypeFromIndex(i: Int): AbilityType = requireRange(ABILITY_TYPE_TABLE, i, "AbilityType")

    // -------------------------------------------------------------------------
    // Resource (sealed interface)
    // -------------------------------------------------------------------------

    private val RESOURCE_TABLE: List<Resource> = listOf(
        Resource.Mana,
        Resource.Stamina,
        Resource.Health,
    )

    fun Resource.toIndex(): Int = RESOURCE_TABLE.indexOf(this).also {
        require(it >= 0) { "Resource $this is not in the wire index table" }
    }
    fun resourceFromIndex(i: Int): Resource = requireRange(RESOURCE_TABLE, i, "Resource")

    // -------------------------------------------------------------------------
    // Amount (sealed interface)
    // -------------------------------------------------------------------------

    private val AMOUNT_TABLE: List<Amount> = listOf(
        Amount.None,
        Amount.VeryLow,
        Amount.Low,
        Amount.Moderate,
        Amount.High,
        Amount.VeryHigh,
        Amount.Extreme,
        Amount.BeyondExtreme,
    )

    fun Amount.toIndex(): Int = AMOUNT_TABLE.indexOf(this).also {
        require(it >= 0) { "Amount $this is not in the wire index table" }
    }
    fun amountFromIndex(i: Int): Amount = requireRange(AMOUNT_TABLE, i, "Amount")

    // -------------------------------------------------------------------------
    // Property (sealed interface, 52 entries)
    // -------------------------------------------------------------------------

    private val PROPERTY_TABLE: List<Property> = listOf(
        Property.Affliction,
        Property.Blood,
        Property.Boon,
        Property.Channel,
        Property.Cleanse,
        Property.Combination,
        Property.Conjuration,
        Property.Consumable,
        Property.CounterExecute,
        Property.Curse,
        Property.DamageOverTime,
        Property.Dark,
        Property.Darkness,
        Property.Dimension,
        Property.Disease,
        Property.Drain,
        Property.Elemental,
        Property.Essence,
        Property.Execute,
        Property.Fire,
        Property.HealOverTime,
        Property.Healing,
        Property.Holy,
        Property.Ice,
        Property.Illusion,
        Property.Light,
        Property.Lightning,
        Property.Magic,
        Property.ManaOverTime,
        Property.Melee,
        Property.Momentum,
        Property.Movement,
        Property.Nature,
        Property.Perception,
        Property.Poison,
        Property.Recovery,
        Property.Restoration,
        Property.Retributive,
        Property.Ritual,
        Property.Sacrifice,
        Property.ShapeChange,
        Property.Signal,
        Property.Stacking,
        Property.StaminaOverTime,
        Property.Summon,
        Property.Teleport,
        Property.Tracking,
        Property.Trap,
        Property.Unholy,
        Property.Vehicle,
        Property.Wounding,
        Property.Zone,
    )

    fun Property.toIndex(): Int = PROPERTY_TABLE.indexOf(this).also {
        require(it >= 0) { "Property $this is not in the wire index table" }
    }
    fun propertyFromIndex(i: Int): Property = requireRange(PROPERTY_TABLE, i, "Property")

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun <T> requireRange(table: List<T>, i: Int, label: String): T {
        require(i in table.indices) { "$label index $i is out of range (0..${table.size - 1})" }
        return table[i]
    }
}
