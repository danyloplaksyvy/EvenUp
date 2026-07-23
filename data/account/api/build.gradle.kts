plugins {
    id("evenup.kotlin.library")
}

dependencies {
    implementation(project(":domain:account:api"))
    implementation(libs.kotlinx.coroutines.android)
}
