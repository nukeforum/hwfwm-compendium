plugins {
    id("compendium.jvm")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("CompendiumDatabase") {
            packageName.set("wizardry.compendium.persistence")
        }
    }
}

dependencies {
    implementation(project(":essences"))
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.sqldelight.sqlite.driver)
}
