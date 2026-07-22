plugins {
    id("evenup.android.compose.library")
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:camera:api"))
    implementation(project(":core:designsystem:api"))
    implementation(project(":core:navigation:api"))
    implementation(project(":core:network:api"))
    implementation(project(":core:speech:api"))
    implementation(project(":data:expense-input:api"))
    implementation(project(":data:expense:api"))
    implementation(project(":data:participant:api"))
    implementation(project(":data:receipt:api"))
    implementation(project(":domain:expense:api"))
    implementation(project(":domain:participant:api"))
    implementation(project(":domain:receipt:api"))
    implementation(project(":domain:sharing:api"))
    implementation(project(":domain:expense-input:api"))
    implementation(project(":feature:expense-flow:api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
