plugins {
    id("compendium.jvm")
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("CompendiumDatabase") {
            packageName.set("wizardry.compendium.persistence")
            // Snapshot of the compiled schema at the current Schema.version, used by
            // verifyMigrations to confirm that applying every .sqm in order produces
            // the same schema as the .sq files. Regenerate after schema changes via
            //   ./gradlew :persistence:generateMainCompendiumDatabaseSchema
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.kotlin.reflect)
    implementation(libs.sqldelight.runtime)
    implementation(libs.sqldelight.coroutines)
    testImplementation(libs.sqldelight.sqlite.driver)
}
