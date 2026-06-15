plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:network:api"))
    implementation(project(":data:receipt:api"))
    implementation(project(":domain:receipt:api"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
