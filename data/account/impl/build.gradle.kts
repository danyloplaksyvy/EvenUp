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
    implementation(project(":core:auth:api"))
    implementation(project(":core:database:api"))
    implementation(project(":core:datastore:api"))
    implementation(project(":core:network:api"))
    implementation(project(":data:account:api"))
    implementation(project(":domain:account:api"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
