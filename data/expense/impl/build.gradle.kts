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
    implementation(project(":core:datastore:api"))
    implementation(project(":core:network:api"))
    implementation(project(":data:expense:api"))
    implementation(project(":data:expense-input:api"))
    implementation(project(":data:sharing:api"))
    implementation(project(":domain:expense:api"))
    implementation(project(":domain:participant:api"))
    implementation(project(":domain:receipt:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":data:sharing:impl"))
    testImplementation(libs.junit)
}
