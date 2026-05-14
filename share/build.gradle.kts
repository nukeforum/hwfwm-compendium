plugins {
    id("compendium.jvm")
}

dependencies {
    implementation(project(":essences"))
    implementation(project(":wire"))
    implementation(project(":wire-repo"))

    implementation(libs.kotlin.coroutines)
    implementation("javax.inject:javax.inject:1")

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.coroutines.test)
}
