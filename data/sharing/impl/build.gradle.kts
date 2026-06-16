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
    implementation(project(":core:network:api"))
    implementation(project(":data:sharing:api"))
    implementation(project(":domain:expense:api"))
    implementation(project(":domain:sharing:api"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
