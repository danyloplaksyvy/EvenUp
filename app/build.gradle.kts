import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.dps.evenup"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dps.evenup"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:camera:api"))
    implementation(project(":core:camera:impl"))
    implementation(project(":core:datastore:api"))
    implementation(project(":core:datastore:impl"))
    implementation(project(":core:designsystem:api"))
    implementation(project(":core:designsystem:impl"))
    implementation(project(":core:navigation:api"))
    implementation(project(":core:navigation:impl"))
    implementation(project(":core:network:api"))
    implementation(project(":core:network:impl"))
    implementation(project(":data:expense:impl"))
    implementation(project(":data:expense:api"))
    implementation(project(":data:participant:api"))
    implementation(project(":data:participant:impl"))
    implementation(project(":data:receipt:api"))
    implementation(project(":data:receipt:impl"))
    implementation(project(":data:sharing:api"))
    implementation(project(":data:sharing:impl"))
    implementation(project(":domain:expense:impl"))
    implementation(project(":domain:participant:impl"))
    implementation(project(":domain:receipt:impl"))
    implementation(project(":domain:sharing:api"))
    implementation(project(":domain:sharing:impl"))
    implementation(project(":feature:expense-flow:api"))
    implementation(project(":feature:expense-flow:impl"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
