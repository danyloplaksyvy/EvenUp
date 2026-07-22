plugins {
    id("evenup.kotlin.library")
}

dependencies {
    implementation(project(":domain:expense-input:api"))
    implementation(project(":domain:expense:api"))
    implementation(project(":domain:participant:api"))
    implementation(project(":domain:receipt:api"))
    testImplementation(libs.junit)
}
