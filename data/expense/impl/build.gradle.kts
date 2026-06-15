plugins {
    id("evenup.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
