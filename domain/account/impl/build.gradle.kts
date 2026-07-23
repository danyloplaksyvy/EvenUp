plugins {
    id("evenup.kotlin.library")
}

dependencies {
    implementation(project(":data:account:api"))
    implementation(project(":domain:account:api"))
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
