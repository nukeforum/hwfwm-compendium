package wizardry.compendium.share

import wizardry.compendium.essences.model.Rarity
import wizardry.compendium.wire.Envelope

/**
 * Snapshot of a pasted confluence-bundle ready to render in the
 * read-only review sheet.
 *
 * `unresolvableNames` is the set of essence names referenced by combinations
 * that are neither bundled in the share nor present in the receiver's
 * library. Names are stored lowercase. The Save button is disabled when
 * this set is non-empty.
 */
data class ConfluenceImportPreview(
    val envelope: Envelope,
    val confluenceName: String,
    val isRestricted: Boolean,
    val combinations: List<PreviewCombination>,
    val essences: List<PreviewEssence>,
    val unresolvableNames: Set<String>,
)

data class PreviewCombination(
    val essence1: String,
    val essence2: String,
    val essence3: String,
    val isRestricted: Boolean,
)

data class PreviewEssence(
    val name: String,
    val rarity: Rarity,
    val isNew: Boolean,
)
