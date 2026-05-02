package wizardry.compendium.wire.ksp

import com.squareup.kotlinpoet.FileSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigratorGeneratorTest {

    @Test
    fun `top-level rename produces a renameField call`() {
        val source = sourceOf(
            MigratorGenerator.fileSpec(
                fromVersion = 1,
                toVersion = 2,
                changes = listOf(
                    SchemaChange.FieldRenamed(
                        typeFqn = "wizardry.compendium.wire.Envelope",
                        fieldName = "description",
                        oldAlias = "desc",
                        newAlias = "d",
                        onEnvelope = true,
                    ),
                ),
            ),
        )
        assertTrue("class header present", source.contains("object MigratorV1ToV2"))
        assertTrue("renameField call emitted", source.contains("renameField(\"desc\", \"d\")"))
        assertFalse("no TODO when fully auto", source.contains("TODO("))
        assertFalse("no throw when fully auto", source.contains("NotImplementedError"))
    }

    @Test
    fun `manual migrator produces TODO and throws`() {
        val source = sourceOf(
            MigratorGenerator.fileSpec(
                fromVersion = 1,
                toVersion = 2,
                changes = listOf(
                    SchemaChange.FieldTypeChanged(
                        typeFqn = "wizardry.compendium.wire.Manifestation",
                        fieldName = "count",
                        oldType = "kotlin.Int",
                        newType = "kotlin.Long",
                    ),
                ),
            ),
        )
        assertTrue("TODO present", source.contains("TODO("))
        assertTrue("throws NotImplementedError", source.contains("NotImplementedError"))
        assertTrue(
            "describes the change",
            source.contains("count") && source.contains("Int") && source.contains("Long"),
        )
    }

    @Test
    fun `mechanical-only changes generate a no-op identity migrator`() {
        val source = sourceOf(
            MigratorGenerator.fileSpec(
                fromVersion = 1,
                toVersion = 2,
                changes = listOf(
                    SchemaChange.FieldAdded(
                        typeFqn = "wizardry.compendium.wire.Envelope",
                        fieldName = "newField",
                        alias = "x",
                        hasDefault = true,
                    ),
                ),
            ),
        )
        assertTrue("returns input", source.contains("return current"))
        assertFalse("no rename calls", source.contains("renameField"))
        assertFalse("no manual stub", source.contains("NotImplementedError"))
    }

    @Test
    fun `mixed auto and manual produces stub that fails loudly`() {
        val source = sourceOf(
            MigratorGenerator.fileSpec(
                fromVersion = 1,
                toVersion = 2,
                changes = listOf(
                    SchemaChange.FieldRenamed(
                        typeFqn = "wizardry.compendium.wire.Envelope",
                        fieldName = "description",
                        oldAlias = "desc",
                        newAlias = "d",
                        onEnvelope = true,
                    ),
                    SchemaChange.FieldTypeChanged(
                        typeFqn = "wizardry.compendium.wire.Manifestation",
                        fieldName = "count",
                        oldType = "kotlin.Int",
                        newType = "kotlin.Long",
                    ),
                ),
            ),
        )
        assertTrue("stubs the body", source.contains("NotImplementedError"))
        assertTrue(
            "documents both changes",
            source.contains("desc") && source.contains("count"),
        )
    }
}

private fun sourceOf(fileSpec: FileSpec): String = fileSpec.toString()
