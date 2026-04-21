package mw.compendium.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.the
import kotlin.jvm.optionals.getOrNull

fun Project.findLibrary(key: String): MinimalExternalModuleDependency {
    val catalogsExtension = the<VersionCatalogsExtension>()
    return catalogsExtension.catalogNames.firstNotNullOfOrNull {
        catalogsExtension.named(it).findLibrary(key).getOrNull()?.orNull
    }
        .let {
            checkNotNull(it) { "Library $key not found in any catalog" }
        }
}

    fun Project.findVersion(key: String): String {
        val catalogsExtension = the<VersionCatalogsExtension>()
        return catalogsExtension.catalogNames
            .also { println("${it.count()} catalogs found") }
            .firstNotNullOfOrNull {
            println("catalog named: $it")
            catalogsExtension.named(it).findVersion(key).getOrNull()?.toString()
        }
            .orEmpty()
    }