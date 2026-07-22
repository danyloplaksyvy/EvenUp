plugins {
    id("evenup.kotlin.library")
}

dependencies {
    api(project(":domain:expense:api"))
    api(project(":domain:participant:api"))
    api(project(":domain:receipt:api"))
    testImplementation(libs.junit)
}
