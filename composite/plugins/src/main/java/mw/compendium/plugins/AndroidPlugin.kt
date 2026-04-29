package mw.compendium.plugins

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.library")
        apply(plugin = "com.google.devtools.ksp")

        configure<LibraryExtension> {
            compileSdk = findVersion("target_sdk").toInt()
            defaultConfig {
                minSdk = findVersion("min_sdk").toInt()
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            testOptions {
                targetSdk = findVersion("target_sdk").toInt()
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }
        }

        dependencies {
            add("implementation", findLibrary("kotlin-coroutines"))
            add("implementation", findLibrary("dagger"))
            add("ksp", findLibrary("dagger-compiler"))
            add("testImplementation", findLibrary("junit4"))
        }
    }
}
