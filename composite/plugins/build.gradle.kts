plugins {
    `kotlin-dsl`
    id("java-gradle-plugin")
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())

    compileOnly(composite.agp)
    compileOnly(composite.kotlin.gradle)
    compileOnly(composite.ksp)
}

gradlePlugin {
    plugins {
        create("compendium.jvm") {
            id = "compendium.jvm"
            implementationClass = "mw.compendium.plugins.JvmPlugin"
        }
        create("compendium.android") {
            id = "compendium.android"
            implementationClass = "mw.compendium.plugins.AndroidPlugin"
        }
    }
}
