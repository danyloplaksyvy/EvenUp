plugins {
    id("evenup.android.compose.library")
}

dependencies {
    implementation(project(":core:designsystem:api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
