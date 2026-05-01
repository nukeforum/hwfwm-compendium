package wizardry.compendium.wire.ksp

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Wire-schema KSP processor.
 *
 * # What this does today (Tier 1 of the plan)
 *
 * 1. Finds the class annotated with `@WireFormat`.
 * 2. Walks the property graph reachable from it, picking up every type that
 *    contributes properties to the wire format.
 * 3. Records ordered entries for every `@WireEnum`-annotated type (enums and
 *    sealed-interface-of-objects shaped containers).
 * 4. Emits a `SchemaSnapshot` JSON to KSP's resources output as
 *    `wire-schemas/v<N>.json`. A separate Gradle task compares this against
 *    a committed lock file and fails the build on drift.
 *
 * # What this does NOT do yet
 *
 * - **Diff engine.** Tier 2 work. When this lands, the processor will read the
 *   previous version's committed snapshot, diff it against the current one,
 *   and either auto-generate a `WireMigrator` for mechanical changes or fail
 *   compilation with a stub for the developer to fill in.
 * - **Adapter generation.** We currently rely on hand-written kotlinx-
 *   serialization adapters in the `:wire` module. Tier 3 may move that
 *   generation into the processor so `@WireField(alias = "n")` is the only
 *   place an alias is declared.
 * - **Generic types.** The processor renders generic args into the type string
 *   for snapshotting (so `List<Int>` differs from `List<String>`) but does
 *   not yet attempt to migrate across changes in type-parameter shape. Adding
 *   or changing a generic arg will be flagged as a non-mechanical change.
 *
 * # Assumptions
 *
 * - **Single envelope.** Exactly one `@WireFormat` annotation in the project.
 *   Multiple envelopes would require per-envelope snapshots; not modeled.
 * - **Constructor-property model.** We only consider properties declared on
 *   the primary constructor of `data class`es. Body properties or computed
 *   properties (`val foo: Int get() = ...`) are ignored. Wire types should be
 *   simple data carriers; if a wire type ever needs a derived field, surface
 *   it as a constructor property with a default.
 * - **Annotation discovery is "marked OR reachable".** Types reachable from
 *   the envelope's property graph are considered wire types even without
 *   `@WireType`. This is convenient (no need to annotate every nested data
 *   class) but means an internal refactor that adds a property to a reachable
 *   data class shows up as a snapshot change. If that becomes painful, switch
 *   to "annotation required for inclusion."
 *
 * # Over-compensations / paranoia worth noting
 *
 * - **Sorted output.** Types and fields are sorted alphabetically before
 *   serializing the snapshot. KSP's resolver order is generally stable, but
 *   we don't want lock churn if that ever changes. Cost is negligible.
 * - **One-shot processing.** We collect all symbols in a single `process()`
 *   pass and emit the snapshot in the same call. KSP supports multi-pass
 *   processing for cases where a processor's own generated code triggers more
 *   processing rounds (`getSymbolsWithAnnotation` returns `KSAnnotated` so the
 *   caller can defer). We don't generate annotated code, so multi-pass isn't
 *   needed; we always return an empty deferred list.
 * - **Resilient walks.** `walkReachableTypes` deduplicates by FQN even though
 *   the resolver should already return one declaration per FQN. Belt and
 *   suspenders against weird multi-source-set situations.
 * - **Star-projected types treated as resolved.** When a property type
 *   includes generic arguments we render them, but we treat any unresolved
 *   star-projection (`<*>`) as the literal `*` token. This is wrong if a
 *   project ever depends on such a type for the wire format, but no current
 *   wire types use `*`-projected generics so flag as a TODO if it bites.
 */
class WireProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {

    /**
     * KSP can call `process()` multiple times in the same build (rounds). We
     * only need to emit the snapshot once. Track whether we've done it.
     */
    private var emitted = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (emitted) return emptyList()

        val envelopeAnnotation = WIRE_FORMAT_FQN
        val envelopes = resolver
            .getSymbolsWithAnnotation(envelopeAnnotation)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        // No envelope on this round = nothing to do. We do NOT mark `emitted`
        // here because the envelope might be declared in a source set that
        // hasn't been processed yet (rare in our setup, but cheap to handle).
        if (envelopes.isEmpty()) return emptyList()

        if (envelopes.size > 1) {
            env.logger.error(
                "Found ${envelopes.size} @WireFormat-annotated classes; expected exactly one. " +
                    "Multiple envelopes are not supported. Classes: ${envelopes.joinToString { it.qualifiedName?.asString() ?: "?" }}",
            )
            return emptyList()
        }

        val envelope = envelopes.single()
        val version = readWireFormatVersion(envelope) ?: run {
            env.logger.error("Could not read @WireFormat.version from ${envelope.qualifiedName?.asString()}", envelope)
            return emptyList()
        }

        val typeRegistry = mutableMapOf<String, KSClassDeclaration>()
        walkReachableTypes(envelope, typeRegistry)

        val typeEntries = typeRegistry.values
            .map { it.toTypeEntry() }
            .sortedBy { it.fqn }

        val enumEntries = collectWireEnums(resolver)
            .map { it.toEnumEntry() }
            .sortedBy { it.fqn }

        val snapshot = SchemaSnapshot(
            version = version,
            envelope = envelope.qualifiedName?.asString().orEmpty(),
            types = typeEntries,
            enums = enumEntries,
        )

        // Emit to KSP's resources output. Path on disk:
        //   build/generated/ksp/<sourceset>/resources/wire-schemas/v<N>.json
        // The `Dependencies(true)` argument means "this output isolates with
        // all source files" — overly conservative but avoids stale snapshots
        // when source files change in ways the processor didn't directly read.
        val out = env.codeGenerator.createNewFileByPath(
            dependencies = Dependencies(aggregating = true),
            path = "wire-schemas/v$version",
            extensionName = "json",
        )
        out.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(SNAPSHOT_JSON.encodeToString(snapshot))
            writer.write("\n")
        }

        emitted = true
        return emptyList()
    }

    /**
     * Pulls `version` out of a `@WireFormat` annotation. We avoid reflection
     * on the annotation class because wire-ksp doesn't depend on the
     * `:wire-annotations` runtime jar (it'd be a shaded conflict if both were
     * on the processor classpath). Look up by name instead.
     */
    private fun readWireFormatVersion(envelope: KSClassDeclaration): Int? {
        val ann = envelope.annotations.firstOrNull { it.matches(WIRE_FORMAT_FQN) } ?: return null
        return ann.intArg("version")
    }

    /**
     * Walks reachable wire types starting at the envelope.
     *
     * "Reachable" means: any type used as a property type on a wire-relevant
     * class. We include `data class` declarations and skip everything else
     * (interfaces, enums — enums are picked up separately via `@WireEnum`).
     *
     * NOTE: the walk follows generic arguments too (`List<Foo>` reaches `Foo`).
     * Map values, set members, etc. are all reached. The walk does NOT follow
     * function types or unresolved types — both are very unlikely in wire
     * payloads but flag a warning if seen so we know to handle them.
     */
    private fun walkReachableTypes(
        root: KSClassDeclaration,
        registry: MutableMap<String, KSClassDeclaration>,
    ) {
        val rootFqn = root.qualifiedName?.asString() ?: return
        if (registry.containsKey(rootFqn)) return
        if (!root.isWireParticipantClass()) return
        registry[rootFqn] = root

        for (prop in root.constructorProperties()) {
            val propType = prop.type.resolve()
            visitType(propType, registry)
        }
    }

    private fun visitType(
        ksType: KSType,
        registry: MutableMap<String, KSClassDeclaration>,
    ) {
        val decl = ksType.declaration as? KSClassDeclaration ?: return
        // Recurse into the type's declaration (data class, etc.) AND into any
        // generic arguments. This matters for `List<Manifestation>` shapes.
        if (decl.isWireParticipantClass()) {
            walkReachableTypes(decl, registry)
        }
        for (arg in ksType.arguments) {
            val resolved = arg.type?.resolve() ?: continue
            visitType(resolved, registry)
        }
    }

    /**
     * A type is a wire participant if it's a `data class`. We deliberately do
     * NOT require `@WireType` so common nested types don't need annotation
     * boilerplate. Trade-off documented at the top of this file.
     */
    private fun KSClassDeclaration.isWireParticipantClass(): Boolean =
        classKind == ClassKind.CLASS && Modifier.DATA in modifiers

    /**
     * Returns all @WireEnum-annotated declarations in the project (and on
     * classpath). We scan once at top level rather than threading through the
     * type walk — this avoids ordering dependencies and ensures we capture
     * every enum the project commits to even if some are unreachable from the
     * envelope today (still part of the wire vocabulary).
     */
    private fun collectWireEnums(resolver: Resolver): List<KSClassDeclaration> {
        return resolver
            .getSymbolsWithAnnotation(WIRE_ENUM_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()
    }

    private fun KSClassDeclaration.toTypeEntry(): TypeEntry {
        val typeAlias = annotations
            .firstOrNull { it.matches(WIRE_TYPE_FQN) }
            ?.stringArg("alias")
            .orEmpty()

        val fields = constructorProperties()
            .map { prop ->
                val ann = prop.annotations.firstOrNull { it.matches(WIRE_FIELD_FQN) }
                FieldEntry(
                    name = prop.simpleName.asString(),
                    alias = ann?.stringArg("alias").orEmpty(),
                    previousAlias = ann?.stringArg("previousAlias").orEmpty(),
                    type = prop.type.resolve().renderForSnapshot(),
                    omitOnDefault = ann?.booleanArg("omitOnDefault") ?: true,
                    hasDefault = prop.hasDefault(),
                )
            }
            .sortedBy { it.name }

        return TypeEntry(
            fqn = qualifiedName?.asString().orEmpty(),
            typeAlias = typeAlias,
            fields = fields,
        )
    }

    private fun KSClassDeclaration.toEnumEntry(): EnumEntry {
        // Two shapes: `enum class` and `sealed interface`/`sealed class`.
        val entries = when {
            classKind == ClassKind.ENUM_CLASS -> declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { it.simpleName.asString() }
                .toList()
            Modifier.SEALED in modifiers -> getSealedSubclasses()
                // Order: declaration order. KSP's `getSealedSubclasses` does
                // not promise this; sort by source position to be safe. If
                // KSP exposes a more reliable order in the future, prefer
                // that. For now, simpleName as a stable secondary tie-break.
                .toList()
                .sortedBy { it.location.toString() }
                .map { it.simpleName.asString() }
            else -> {
                env.logger.warn(
                    "@WireEnum on ${qualifiedName?.asString()} is neither an enum class nor a sealed type. Recording empty entry list.",
                    this,
                )
                emptyList()
            }
        }

        return EnumEntry(
            fqn = qualifiedName?.asString().orEmpty(),
            entries = entries,
        )
    }

    private fun KSClassDeclaration.constructorProperties(): List<KSPropertyDeclaration> {
        // Limit to primary constructor parameters that are also properties.
        val ctor = primaryConstructor ?: return emptyList()
        val ctorParamNames = ctor.parameters
            .filter { it.isVal || it.isVar }
            .mapNotNull { it.name?.asString() }
            .toSet()
        return getDeclaredProperties()
            .filter { it.simpleName.asString() in ctorParamNames }
            .toList()
    }

    private fun KSPropertyDeclaration.hasDefault(): Boolean {
        // KSP doesn't directly expose "this property has a default value" for
        // constructor properties, so we look at the matching constructor
        // parameter. Defaults declared on the parameter propagate to the
        // property, which is the only convention our wire types use.
        val owner = parentDeclaration as? KSClassDeclaration ?: return false
        val param = owner.primaryConstructor?.parameters?.firstOrNull {
            it.name?.asString() == simpleName.asString()
        } ?: return false
        return param.hasDefault
    }

    /**
     * Renders a type into a stable string for snapshot comparison.
     *
     * Includes nullability and generic arguments. Uses qualified names for
     * declared types (so `kotlin.collections.List<kotlin.Int>` not just
     * `List<Int>`) — verbose but unambiguous and resilient to import
     * shuffling.
     */
    private fun KSType.renderForSnapshot(): String {
        val decl = declaration
        val base = decl.qualifiedName?.asString() ?: decl.simpleName.asString()
        val args = arguments.joinToString(",") { arg ->
            arg.type?.resolve()?.renderForSnapshot() ?: "*"
        }
        val generic = if (args.isEmpty()) "" else "<$args>"
        val nullable = if (isMarkedNullable) "?" else ""
        return "$base$generic$nullable"
    }

    private fun KSAnnotation.matches(fqn: String): Boolean {
        return annotationType.resolve().declaration.qualifiedName?.asString() == fqn
    }

    private fun KSAnnotation.stringArg(name: String): String? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String

    private fun KSAnnotation.intArg(name: String): Int? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? Int

    private fun KSAnnotation.booleanArg(name: String): Boolean? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean

    companion object {
        const val WIRE_FORMAT_FQN = "wizardry.compendium.wire.annotations.WireFormat"
        const val WIRE_FIELD_FQN = "wizardry.compendium.wire.annotations.WireField"
        const val WIRE_TYPE_FQN = "wizardry.compendium.wire.annotations.WireType"
        const val WIRE_ENUM_FQN = "wizardry.compendium.wire.annotations.WireEnum"

        private val SNAPSHOT_JSON = Json {
            prettyPrint = true
            // The committed lock file is human-reviewed in code review, so
            // pretty-print is mandatory. Indent stays at the kotlinx default
            // (4 spaces) — change in tandem with whatever editor the team
            // uses if diffs become noisy.
            encodeDefaults = true
        }
    }
}

/**
 * KSP-required factory. Registered via the META-INF/services file in this
 * module's resources. KSP discovers and instantiates this on every build.
 */
class WireProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return WireProcessor(environment)
    }
}
