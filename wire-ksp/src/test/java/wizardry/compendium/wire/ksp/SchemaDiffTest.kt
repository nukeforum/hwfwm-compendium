package wizardry.compendium.wire.ksp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaDiffTest {

    @Test
    fun `unchanged snapshot produces no changes`() {
        val s = snapshot(
            types = listOf(envelopeType()),
        )
        assertEquals(emptyList<SchemaChange>(), diffSnapshots(s, s))
    }

    @Test
    fun `type added is mechanical`() {
        val prev = snapshot(types = listOf(envelopeType()))
        val curr = snapshot(types = listOf(envelopeType(), manifestationType()))
        val changes = diffSnapshots(prev, curr)
        assertEquals(1, changes.size)
        val added = changes.first() as SchemaChange.TypeAdded
        assertEquals("wizardry.compendium.wire.Manifestation", added.fqn)
        assertEquals(SchemaChange.Classification.Mechanical, added.classification)
    }

    @Test
    fun `type removed needs manual migrator`() {
        val prev = snapshot(types = listOf(envelopeType(), manifestationType()))
        val curr = snapshot(types = listOf(envelopeType()))
        val changes = diffSnapshots(prev, curr)
        assertEquals(1, changes.size)
        assertTrue(changes.first() is SchemaChange.TypeRemoved)
        assertEquals(
            SchemaChange.Classification.NeedsManualMigrator,
            changes.first().classification,
        )
    }

    @Test
    fun `field added with default is mechanical`() {
        val prev = snapshot(types = listOf(envelopeType()))
        val curr = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("color", "c", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldAdded
        assertEquals("color", change.fieldName)
        assertEquals(SchemaChange.Classification.Mechanical, change.classification)
    }

    @Test
    fun `field added without default needs manual migrator`() {
        val prev = snapshot(types = listOf(envelopeType()))
        val curr = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("color", "c", "", "kotlin.String", omitOnDefault = false, hasDefault = false),
            ),
        ))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldAdded
        assertEquals(SchemaChange.Classification.NeedsManualMigrator, change.classification)
    }

    @Test
    fun `field removed is mechanical`() {
        val prev = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("color", "c", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val curr = snapshot(types = listOf(envelopeType()))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldRemoved
        assertEquals("color", change.fieldName)
        assertEquals(SchemaChange.Classification.Mechanical, change.classification)
    }

    @Test
    fun `field rename declared via previousAlias is auto-migratable on envelope`() {
        val prev = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("description", "desc", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val curr = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("description", "d", "desc", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldRenamed
        assertEquals("desc", change.oldAlias)
        assertEquals("d", change.newAlias)
        assertTrue(change.onEnvelope)
        assertEquals(SchemaChange.Classification.AutoMigrator, change.classification)
    }

    @Test
    fun `field rename on nested type still needs manual migrator in tier 2`() {
        val prev = snapshot(types = listOf(
            envelopeType(),
            manifestationType().withFields(
                FieldEntry("description", "desc", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val curr = snapshot(types = listOf(
            envelopeType(),
            manifestationType().withFields(
                FieldEntry("description", "d", "desc", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldRenamed
        assertTrue(!change.onEnvelope)
        assertEquals(SchemaChange.Classification.NeedsManualMigrator, change.classification)
    }

    @Test
    fun `alias change without previousAlias produces remove plus add`() {
        // Footgun: alias changed but the dev forgot to declare previousAlias.
        // We surface this loudly so the dev makes an explicit choice rather
        // than silently breaking old envelopes.
        val prev = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("description", "desc", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val curr = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("description", "d", "", "kotlin.String", omitOnDefault = true, hasDefault = true),
            ),
        ))
        val changes = diffSnapshots(prev, curr)
        assertEquals(2, changes.size)
        assertTrue(changes.any { it is SchemaChange.FieldRemoved && it.alias == "desc" })
        assertTrue(changes.any { it is SchemaChange.FieldAdded && it.alias == "d" })
    }

    @Test
    fun `field type changed needs manual migrator`() {
        val prev = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("count", "c", "", "kotlin.Int", omitOnDefault = false, hasDefault = false),
            ),
        ))
        val curr = snapshot(types = listOf(
            envelopeType().withFields(
                FieldEntry("count", "c", "", "kotlin.Long", omitOnDefault = false, hasDefault = false),
            ),
        ))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.FieldTypeChanged
        assertEquals("kotlin.Int", change.oldType)
        assertEquals("kotlin.Long", change.newType)
        assertEquals(SchemaChange.Classification.NeedsManualMigrator, change.classification)
    }

    @Test
    fun `enum value appended is mechanical`() {
        val prev = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "B", "C"))))
        val curr = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "B", "C", "D"))))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.EnumValueAppended
        assertEquals("D", change.value)
        assertEquals(SchemaChange.Classification.Mechanical, change.classification)
    }

    @Test
    fun `enum reordered needs manual migrator`() {
        val prev = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "B", "C"))))
        val curr = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "C", "B"))))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.EnumReshaped
        assertEquals(SchemaChange.Classification.NeedsManualMigrator, change.classification)
    }

    @Test
    fun `enum entry removed mid-list needs manual migrator`() {
        val prev = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "B", "C"))))
        val curr = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "C"))))
        val change = diffSnapshots(prev, curr).single() as SchemaChange.EnumReshaped
        assertEquals(SchemaChange.Classification.NeedsManualMigrator, change.classification)
    }

    @Test
    fun `multiple enum entries appended produce one change each`() {
        val prev = snapshot(enums = listOf(EnumEntry("test.E", listOf("A"))))
        val curr = snapshot(enums = listOf(EnumEntry("test.E", listOf("A", "B", "C"))))
        val changes = diffSnapshots(prev, curr)
        assertEquals(2, changes.size)
        assertTrue(changes.all { it is SchemaChange.EnumValueAppended })
    }
}

private fun snapshot(
    version: Int = 2,
    envelopeFqn: String = "wizardry.compendium.wire.Envelope",
    types: List<TypeEntry> = listOf(envelopeType()),
    enums: List<EnumEntry> = emptyList(),
) = SchemaSnapshot(
    version = version,
    envelope = envelopeFqn,
    types = types,
    enums = enums,
)

private fun envelopeType() = TypeEntry(
    fqn = "wizardry.compendium.wire.Envelope",
    typeAlias = "envelope",
    fields = emptyList(),
)

private fun manifestationType() = TypeEntry(
    fqn = "wizardry.compendium.wire.Manifestation",
    typeAlias = "manifestation",
    fields = emptyList(),
)

private fun TypeEntry.withFields(vararg fields: FieldEntry) = copy(fields = fields.toList())
