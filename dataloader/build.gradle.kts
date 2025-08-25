plugins {
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(project(":essences"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1")

    implementation("com.google.dagger:dagger:2.44")
    kapt("com.google.dagger:dagger-compiler:2.44")

    testImplementation("junit:junit:4.13.2")
}