plugins {
    id("evenup.android.library")
}

dependencies {
    implementation(project(":core:speech:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
