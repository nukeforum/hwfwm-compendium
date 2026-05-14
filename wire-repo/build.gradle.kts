plugins {
    id("compendium.jvm")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":repositories:api"))
    implementation(project(":wire"))

    testImplementation(libs.kotlin.coroutines.test)
}
