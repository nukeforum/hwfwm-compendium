package wizardry.compendium.wire.ksp

/**
 * Schema diff types and the engine that produces them.
 *
 * # Mental model
 *
 * Given two `SchemaSnapshot`s — `previous` (the committed lock for v(N-1))
 * and `current` (derived from current annotations, target version v(N)) —
 * the diff engine produces a flat list of `SchemaChange`s that capture every
 * way the wire shape has shifted.
 *
 * Each change has a `Classification`:
 *  - `Mechanical` — the wire reader handles this without help (e.g., a new
 *    field with a default; old envelopes simply lack the field and the
 *    decoder fills the default in). No migrator needed.
 *  - `AutoMigrator` — the processor can emit a `WireMigrator` to bridge old
 *    envelopes to the new shape (e.g., a field rename via `previousAlias`).
 *  - `NeedsManualMigrator` — the processor can't auto-generate the migrator
 *    safely; it emits a stub and fails the build. Most commonly: type
 *    changes, enum reorders, structural rearrangement.
 *
 * # Why this is a flat list and not a tree
 *
 * A schema change at the type level (e.g., `Manifestation` got a new field)
 * is naturally *attached* to a type, not a tree-shaped diff. Flat list keeps
 * the consumer code (the migrator generator) trivial: it walks once and
 * picks out the change kinds it knows how to handle.
 *
 * # What the diff engine does NOT compute
 *
 * - **Path from envelope to type.** When a field rename occurs on a nested
 *   type (e.g., `Manifestation.description`), the migrator needs to know
 *   *where in the JSON tree* manifestations live (`envelope.e[*]` in our
 *   seed schema). For tier 2, we punt: only top-level (envelope) field
 *   renames are auto-migrated. Nested-type renames are emitted as
 *   `NeedsManualMigrator` with a clear stub. Tier 3 can extend the snapshot
 *   to include path-from-envelope info for each type, enabling auto-migration
 *   of nested renames.
 * - **Default values.** We know whether a field has a default but not what
 *   the default is. So a "field added without default" can't be auto-migrated
 *   even if the developer "knows" what to fill in; they must supply a manual
 *   migrator.
 *
 * # Stability of the change list
 *
 * The diff engine sorts its output by (type FQN, then field name) so that
 * snapshot drift produces deterministic output between builds. The migrator
 * generator depends on this — it embeds the changes into the generated code,
 * and we don't want spurious diffs on regenerated migrators.
 */
sealed interface SchemaChange {
    val classification: Classification

    enum class Classification {
        Mechanical,
        AutoMigrator,
        NeedsManualMigrator,
    }

    /**
     * A new wire type appears in the current snapshot. Old envelopes simply
     * don't reference it, so reading them with new code is fine without a
     * migrator (the new field that holds these objects defaults to empty).
     */
    data class TypeAdded(val fqn: String) : SchemaChange {
        override val classification = Classification.Mechanical
    }

    /**
     * A wire type disappeared. New code can no longer decode old envelopes
     * that contain instances of this type — the developer needs to write a
     * migrator that drops or transforms the data into the new shape.
     */
    data class TypeRemoved(val fqn: String) : SchemaChange {
        override val classification = Classification.NeedsManualMigrator
    }

    /**
     * A new field appeared on an existing type.
     *
     * - With a default: old envelopes simply lack the field; decoder fills
     *   the default. Mechanical, no migrator.
     * - Without a default: old envelopes are missing a now-required field;
     *   the migrator must supply one. We can't guess; require a manual
     *   migrator.
     */
    data class FieldAdded(
        val typeFqn: String,
        val fieldName: String,
        val alias: String,
        val hasDefault: Boolean,
    ) : SchemaChange {
        override val classification =
            if (hasDefault) Classification.Mechanical else Classification.NeedsManualMigrator
    }

    /**
     * A field disappeared. With `ignoreUnknownKeys = true` on the decoder
     * (which our codec sets), old envelopes silently drop the value on read.
     * Lossy but mechanical.
     */
    data class FieldRemoved(
        val typeFqn: String,
        val fieldName: String,
        val alias: String,
    ) : SchemaChange {
        override val classification = Classification.Mechanical
    }

    /**
     * A field's wire alias changed. `previousAlias` on the new annotation
     * declares the rename — old envelopes with the old alias should still
     * import. Auto-migrated for top-level (envelope) fields in tier 2;
     * nested-type renames currently mark as NeedsManualMigrator.
     *
     * @param onEnvelope true if this field is on the envelope class itself
     *                   (auto-migratable), false if on a nested type
     *                   (manual until tier 3).
     */
    data class FieldRenamed(
        val typeFqn: String,
        val fieldName: String,
        val oldAlias: String,
        val newAlias: String,
        val onEnvelope: Boolean,
    ) : SchemaChange {
        override val classification =
            if (onEnvelope) Classification.AutoMigrator else Classification.NeedsManualMigrator
    }

    /**
     * A field's Kotlin type changed (e.g., `Int` → `Long`, `String` →
     * `List<String>`). Always non-mechanical; the developer must specify
     * how to convert.
     */
    data class FieldTypeChanged(
        val typeFqn: String,
        val fieldName: String,
        val oldType: String,
        val newType: String,
    ) : SchemaChange {
        override val classification = Classification.NeedsManualMigrator
    }

    /**
     * Enum entry added at the end of the declaration list. Indices of
     * existing entries unchanged; old envelopes still decode correctly.
     */
    data class EnumValueAppended(
        val fqn: String,
        val value: String,
    ) : SchemaChange {
        override val classification = Classification.Mechanical
    }

    /**
     * Enum entries reordered or removed. Existing wire indices now point at
     * different entries, breaking old envelopes. Manual migrator must remap.
     */
    data class EnumReshaped(
        val fqn: String,
        val previousEntries: List<String>,
        val currentEntries: List<String>,
    ) : SchemaChange {
        override val classification = Classification.NeedsManualMigrator
    }
}

/**
 * Computes the diff between two snapshots.
 *
 * Both inputs must be valid (versions can differ; types and enums are
 * compared by FQN). Output is sorted for determinism.
 */
fun diffSnapshots(previous: SchemaSnapshot, current: SchemaSnapshot): List<SchemaChange> {
    val changes = mutableListOf<SchemaChange>()

    val prevTypes = previous.types.associateBy { it.fqn }
    val currTypes = current.types.associateBy { it.fqn }
    val envelopeFqn = current.envelope

    // Type-level adds and removes.
    for (fqn in (prevTypes.keys - currTypes.keys).sorted()) {
        changes += SchemaChange.TypeRemoved(fqn)
    }
    for (fqn in (currTypes.keys - prevTypes.keys).sorted()) {
        changes += SchemaChange.TypeAdded(fqn)
    }

    // Field-level diffs for types in both.
    for (fqn in (prevTypes.keys.intersect(currTypes.keys)).sorted()) {
        changes += diffFields(
            typeFqn = fqn,
            previous = prevTypes.getValue(fqn),
            current = currTypes.getValue(fqn),
            onEnvelope = fqn == envelopeFqn,
        )
    }

    // Enum-level diffs.
    val prevEnums = previous.enums.associateBy { it.fqn }
    val currEnums = current.enums.associateBy { it.fqn }
    for (fqn in (prevEnums.keys.intersect(currEnums.keys)).sorted()) {
        val prev = prevEnums.getValue(fqn).entries
        val curr = currEnums.getValue(fqn).entries
        if (prev == curr) continue
        // "Appended at end" is the cheap stable case: prev is a strict prefix
        // of curr. Anything else is a reshape.
        if (curr.size > prev.size && curr.subList(0, prev.size) == prev) {
            for (added in curr.drop(prev.size)) {
                changes += SchemaChange.EnumValueAppended(fqn, added)
            }
        } else {
            changes += SchemaChange.EnumReshaped(fqn, previousEntries = prev, currentEntries = curr)
        }
    }
    // Enum types added or removed wholesale: treat as type-level for now.
    // Removed: surfaces as NeedsManualMigrator (data may reference dropped indices).
    // Added: mechanical (new envelopes only).
    for (fqn in (prevEnums.keys - currEnums.keys).sorted()) {
        changes += SchemaChange.EnumReshaped(
            fqn = fqn,
            previousEntries = prevEnums.getValue(fqn).entries,
            currentEntries = emptyList(),
        )
    }
    // Added enums are not interesting (no old envelopes reference them).
    // Intentionally not emitting a SchemaChange for that case.

    return changes
}

private fun diffFields(
    typeFqn: String,
    previous: TypeEntry,
    current: TypeEntry,
    onEnvelope: Boolean,
): List<SchemaChange> {
    val out = mutableListOf<SchemaChange>()
    val prevByName = previous.fields.associateBy { it.name }
    val currByName = current.fields.associateBy { it.name }

    // Pure adds (new property name).
    for (name in (currByName.keys - prevByName.keys).sorted()) {
        val f = currByName.getValue(name)
        out += SchemaChange.FieldAdded(typeFqn, fieldName = name, alias = f.alias, hasDefault = f.hasDefault)
    }

    // Pure removes (gone property name).
    for (name in (prevByName.keys - currByName.keys).sorted()) {
        val f = prevByName.getValue(name)
        out += SchemaChange.FieldRemoved(typeFqn, fieldName = name, alias = f.alias)
    }

    // Same property name, possible alias rename or type change.
    for (name in prevByName.keys.intersect(currByName.keys).sorted()) {
        val p = prevByName.getValue(name)
        val c = currByName.getValue(name)
        if (p.alias != c.alias) {
            // Treat as a rename only if the new field's previousAlias mentions the prior alias.
            // Otherwise, treat as remove+add to surface the discrepancy loudly.
            val priorAliases = c.previousAlias.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (p.alias in priorAliases) {
                out += SchemaChange.FieldRenamed(
                    typeFqn = typeFqn,
                    fieldName = name,
                    oldAlias = p.alias,
                    newAlias = c.alias,
                    onEnvelope = onEnvelope,
                )
            } else {
                // Alias changed but rename wasn't declared. This is a footgun
                // we want to surface. Manual migration required so the dev
                // makes an explicit choice.
                out += SchemaChange.FieldRemoved(typeFqn, fieldName = name, alias = p.alias)
                out += SchemaChange.FieldAdded(typeFqn, fieldName = name, alias = c.alias, hasDefault = c.hasDefault)
            }
        }
        if (p.type != c.type) {
            out += SchemaChange.FieldTypeChanged(
                typeFqn = typeFqn,
                fieldName = name,
                oldType = p.type,
                newType = c.type,
            )
        }
    }

    return out
}
