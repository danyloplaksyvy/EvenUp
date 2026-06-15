plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:network:api"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
