plugins {
    id("compendium.android")
}

android {
    namespace = "wizardry.compendium.share"
}

dependencies {
    implementation(project(":essences"))
    implementation(project(":wire"))
    implementation(project(":wire-repo"))
    implementation(project(":ability-preview"))

    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.kotlin.coroutines.test)
}
