plugins {
    id("evenup.kotlin.library")
}

dependencies {
    api(project(":domain:expense:api"))
    api(project(":domain:sharing:api"))
}
