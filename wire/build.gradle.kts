import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":wire-annotations"))
    implementation(project(":essences"))

    // Apply our own KSP processor to this module so any @WireFormat-annotated
    // classes here produce a snapshot.
    ksp(project(":wire-ksp"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.coroutines.test)
}

ksp {
    // Tell the wire-ksp processor where to look for the previous version's
    // committed snapshot when diffing. Path is absolute so the processor
    // doesn't need to know about the project's rootDir.
    arg("wireSchemaDir", layout.projectDirectory.dir("wire-schemas").asFile.absolutePath)
}

// =============================================================================
// Wire schema lock-check tasks.
//
// The KSP processor writes the current snapshot to:
//   build/generated/ksp/main/resources/wire-schemas/v<N>.json
// This file is a build artifact. The committed lock lives at:
//   wire-schemas/v<N>.json (relative to this module)
//
// `checkWireSchemaLock` compares the two and fails the build on drift.
// `updateWireSchemaLock` overwrites the committed lock with the generated
// snapshot — run this only when you intentionally changed the schema.
//
// ## Why two tasks rather than one auto-overwriting task
// Auto-overwriting on every build would silently absorb breaking changes. We
// want intentional diff churn, with the developer running the update task
// after they understand what changed.
//
// ## Wired into `check` but not `assemble`
// The lock check is a correctness gate (like a test), not a packaging
// requirement. Devs running `assembleDebug` for quick iteration shouldn't be
// blocked. CI should always run `check`.
// =============================================================================

val generatedWireSchemaDir = layout.buildDirectory.dir("generated/ksp/main/resources/wire-schemas")
val committedWireSchemaDir = layout.projectDirectory.dir("wire-schemas").asFile

val checkWireSchemaLock = tasks.register("checkWireSchemaLock") {
    group = "verification"
    description = "Verifies the wire-schema lock matches the current annotated wire format. Fails on drift."
    dependsOn("kspKotlin")

    doLast {
        val generatedDir = generatedWireSchemaDir.get().asFile
        val committedDir = committedWireSchemaDir

        if (!generatedDir.exists()) {
            throw GradleException(
                """
                No generated wire schemas were produced.
                Expected at: ${generatedDir.absolutePath}
                Did the KSP processor run? Did you forget to annotate an envelope class with @WireFormat?
                """.trimIndent(),
            )
        }

        val generatedFiles = generatedDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        if (generatedFiles.isEmpty()) {
            throw GradleException(
                "No generated wire-schema JSON files found in ${generatedDir.absolutePath}.",
            )
        }

        for (generated in generatedFiles) {
            val committed = committedDir.resolve(generated.name)
            if (!committed.exists()) {
                throw GradleException(
                    """
                    Wire schema lock missing for ${generated.name}.
                    Committed:  ${committed.absolutePath}
                    Generated:  ${generated.absolutePath}

                    If this is the initial snapshot for a new wire-format version, run:
                        ./gradlew :wire:updateWireSchemaLock
                    and commit the resulting file.
                    """.trimIndent(),
                )
            }
            val generatedText = generated.readText().normalizeLineEndings()
            val committedText = committed.readText().normalizeLineEndings()
            if (generatedText != committedText) {
                throw GradleException(
                    """
                    Wire schema lock drift detected for ${generated.name}.

                    Generated:  ${generated.absolutePath}
                    Committed:  ${committed.absolutePath}

                    The annotated wire format has changed in a way that affects the schema snapshot.
                    Resolve by EITHER:
                      (a) Reverting the change if it was unintentional, OR
                      (b) Bumping the wire format version and writing a migrator (see docs/contributions-import-export.md), then
                          running ./gradlew :wire:updateWireSchemaLock and committing both files together.
                    """.trimIndent(),
                )
            }
        }
    }
}

tasks.named("check") { dependsOn(checkWireSchemaLock) }

// Helper extension: Windows checkouts often have CRLF line endings on
// committed files while build outputs are LF. Normalize both sides for
// comparison so the lock check doesn't fail on whitespace.
fun String.normalizeLineEndings(): String = this.replace("\r\n", "\n")

tasks.register<Copy>("updateWireSchemaLock") {
    group = "verification"
    description = "Overwrites the committed wire-schema lock with the freshly generated snapshot. Use after an intentional schema change."
    dependsOn("kspKotlin")
    from(generatedWireSchemaDir)
    into(committedWireSchemaDir)
}
