plugins {
    id("evenup.kotlin.library")
}

dependencies {
    api(project(":domain:expense-input:api"))
    implementation(libs.kotlinx.coroutines.android)
}
