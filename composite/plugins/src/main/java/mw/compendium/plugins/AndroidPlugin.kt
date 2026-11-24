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
            // compile_sdk is "<major>" or "<major>.<minor>" (e.g. "37.2").
            val compileSdkLevel = findVersion("compile_sdk").split('.').map(String::toInt)
            compileSdk {
                version = release(compileSdkLevel[0]) { minorApiLevel = compileSdkLevel.getOrNull(1) }
            }
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
