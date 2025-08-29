package mw.compendium.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

class JvmPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        plugins.apply("org.jetbrains.kotlin.jvm")
        plugins.apply("com.google.devtools.ksp")

        extensions.configure<HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions>>("kotlin") {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(JavaVersion.VERSION_21.toString()))
            }
        }

        dependencies {
            add("implementation", checkNotNull(findLibrary("kotlin-coroutines")))
            add("implementation", checkNotNull(findLibrary("dagger")))

            add("ksp", checkNotNull(findLibrary("dagger-compiler")))

            add("testImplementation", checkNotNull(findLibrary("junit4")))
        }
    }
}
