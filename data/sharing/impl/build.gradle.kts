plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:network:api"))
    implementation(project(":data:sharing:api"))
    implementation(project(":domain:sharing:api"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
