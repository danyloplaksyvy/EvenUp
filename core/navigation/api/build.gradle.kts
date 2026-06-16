plugins {
    id("evenup.android.library")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.javax.inject)
}
