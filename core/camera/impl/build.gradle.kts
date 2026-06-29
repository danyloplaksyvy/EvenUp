plugins {
    id("evenup.android.library")
}

dependencies {
    implementation(project(":core:camera:api"))

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
}
