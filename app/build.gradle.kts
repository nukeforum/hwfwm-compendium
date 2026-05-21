import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing inputs are resolved in this order, per key:
//   1. Environment variable `<KEY>` (CI-friendly).
//   2. `local.properties` entry `<KEY>` (gitignored, per-checkout).
//   3. Gradle property `COMPENDIUM_<KEY>` (typically in ~/.gradle/gradle.properties,
//      shared across checkouts).
// If any of the four keys is unresolved, the release build falls back to
// debug signing so `assembleRelease` still produces an installable APK for
// R8 smoke-testing. See RELEASE.md for keystore generation + key configuration.
val releaseSigning: Map<String, String>? = run {
    val keys = listOf("KEYSTORE_FILE", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    val localProps = rootProject.file("local.properties").takeIf { it.exists() }?.let {
        Properties().apply { it.inputStream().use(::load) }
    }
    val resolved = keys.associateWith { key ->
        System.getenv(key)?.takeIf { it.isNotEmpty() }
            ?: localProps?.getProperty(key)?.takeIf { it.isNotEmpty() }
            ?: (findProperty("COMPENDIUM_$key") as String?)?.takeIf { it.isNotEmpty() }
            ?: ""
    }
    resolved.takeIf { it.values.all { v -> v.isNotEmpty() } }
}

android {
    namespace = "wizardry.compendium"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "wizardry.compendium"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.target.sdk.get().toInt()
        versionCode = 4
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning["KEYSTORE_FILE"]!!)
                storePassword = releaseSigning["KEYSTORE_PASSWORD"]
                keyAlias = releaseSigning["KEY_ALIAS"]
                keyPassword = releaseSigning["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(project(":ability-listing:contributions"))
    implementation(project(":ability-listing:details"))
    implementation(project(":ability-listing:search"))
    implementation(project(":ability-preview"))
    implementation(project(":awakening-stone:contributions"))
    implementation(project(":awakening-stone:details"))
    implementation(project(":awakening-stone:search"))
    implementation(project(":character-build:contributions"))
    implementation(project(":character-build:details"))
    implementation(project(":character-build:search"))
    implementation(project(":conflicts"))
    implementation(project(":design"))
    implementation(project(":essence:contributions"))
    implementation(project(":essence:details"))
    implementation(project(":essence:search"))
    implementation(project(":model-core"))
    implementation(project(":randomizer"))
    implementation(project(":repositories:impl"))
    implementation(project(":settings"))
    implementation(project(":status-effect:contributions"))
    implementation(project(":status-effect:details"))
    implementation(project(":status-effect:search"))
    implementation(project(":wire"))
    implementation(project(":wire-repo"))
    implementation(project(":drive-backup"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.sqldelight.android.driver)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
