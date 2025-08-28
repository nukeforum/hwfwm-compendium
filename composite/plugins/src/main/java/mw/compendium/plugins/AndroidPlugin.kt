package mw.compendium.plugins

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

class AndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        apply(plugin = "org.jetbrains.kotlin.kapt")

        configure<LibraryExtension>() {
            compileSdk = findVersion("target_sdk").toInt()
            defaultConfig {
                minSdk = findVersion("min_sdk").toInt()
                @Suppress("DEPRECATION")
                targetSdk = findVersion("target_sdk").toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        extensions.configure<HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions>>("kotlin") {
            compilerOptions {
                jvmTarget.set(JvmTarget.fromTarget(JavaVersion.VERSION_21.toString()))
            }
        }

        dependencies {
            add("implementation", checkNotNull(findLibrary("kotlin-coroutines")))
            add("implementation", checkNotNull(findLibrary("dagger")))
            add("kapt", checkNotNull(findLibrary("dagger-compiler")))
            add("testImplementation", checkNotNull(findLibrary("junit4")))
        }
    }
}
