import com.android.build.api.dsl.LibraryExtension

plugins {
    id("evenup.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<LibraryExtension>("android") {
    buildFeatures {
        compose = true
    }
}
