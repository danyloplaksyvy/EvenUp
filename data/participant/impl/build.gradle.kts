plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":core:datastore:api"))
    implementation(project(":data:participant:api"))
    implementation(project(":domain:participant:api"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
