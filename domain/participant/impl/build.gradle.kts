plugins {
    id("evenup.kotlin.library")
}

dependencies {
    implementation(project(":domain:participant:api"))

    testImplementation(libs.junit)
}
