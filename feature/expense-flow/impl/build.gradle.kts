plugins {
    id("evenup.android.compose.library")
}

dependencies {
    implementation(project(":core:camera:api"))
    implementation(project(":core:designsystem:api"))
    implementation(project(":core:navigation:api"))
    implementation(project(":domain:expense:api"))
    implementation(project(":domain:participant:api"))
    implementation(project(":domain:receipt:api"))
    implementation(project(":domain:sharing:api"))
    implementation(project(":feature:expense-flow:api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
