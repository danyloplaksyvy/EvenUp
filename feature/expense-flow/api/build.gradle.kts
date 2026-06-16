plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:navigation:api"))
    implementation(libs.kotlinx.serialization.json)
}
