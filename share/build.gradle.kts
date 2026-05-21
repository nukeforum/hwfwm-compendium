plugins {
    id("compendium.android")
}

android {
    namespace = "wizardry.compendium.share"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":repositories:api"))
    implementation(project(":wire"))
    implementation(project(":wire-repo"))

    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.kotlin.coroutines.test)
}
