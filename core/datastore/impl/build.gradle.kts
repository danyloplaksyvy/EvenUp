plugins {
    id("evenup.android.library")
}

dependencies {
    implementation(project(":core:datastore:api"))

    implementation(libs.androidx.datastore.preferences)
}
