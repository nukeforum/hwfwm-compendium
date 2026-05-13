plugins {
    id("compendium.jvm")
}

dependencies {
    implementation(project(":essences"))
    implementation(project(":wire"))

    testImplementation(libs.kotlin.coroutines.test)
}
