import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val hasGoogleServicesConfig = listOf(
    "google-services.json",
    "src/dev/google-services.json",
    "src/staging/google-services.json",
    "src/prod/google-services.json",
).any { file(it).isFile }
fun hasGoogleServicesConfigFor(flavor: String): Boolean =
    file("src/$flavor/google-services.json").isFile || file("google-services.json").isFile
fun environmentValue(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orElse(fallback).get()

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
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
        manifestPlaceholders["authHost"] = "evenup-dev.firebaseapp.com"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "WORKER_BASE_URL", "\"https://evenup-worker.danyaplaksyvy.workers.dev\"")
            buildConfigField("String", "AUTH_WEB_CLIENT_ID", "\"${environmentValue("EVENUP_DEV_AUTH_WEB_CLIENT_ID")}\"")
            buildConfigField("String", "AUTH_LINK_DOMAIN", "\"${environmentValue("EVENUP_DEV_AUTH_LINK_DOMAIN", "evenup-dev.firebaseapp.com")}\"")
            buildConfigField("String", "TERMS_URL", "\"https://evenup.app/terms\"")
            buildConfigField("String", "PRIVACY_URL", "\"https://evenup.app/privacy\"")
            buildConfigField("String", "TERMS_VERSION", "\"2026-07-23\"")
            buildConfigField("String", "PRIVACY_VERSION", "\"2026-07-23\"")
            buildConfigField("boolean", "AUTH_CONFIGURED", hasGoogleServicesConfigFor("dev").toString())
            buildConfigField("boolean", "APP_CHECK_DEBUG", "true")
            manifestPlaceholders["authHost"] = environmentValue("EVENUP_DEV_AUTH_LINK_DOMAIN", "evenup-dev.firebaseapp.com")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "WORKER_BASE_URL", "\"https://staging-api.evenup.app\"")
            buildConfigField("String", "AUTH_WEB_CLIENT_ID", "\"${environmentValue("EVENUP_STAGING_AUTH_WEB_CLIENT_ID")}\"")
            buildConfigField("String", "AUTH_LINK_DOMAIN", "\"${environmentValue("EVENUP_STAGING_AUTH_LINK_DOMAIN", "evenup-staging.firebaseapp.com")}\"")
            buildConfigField("String", "TERMS_URL", "\"https://evenup.app/terms\"")
            buildConfigField("String", "PRIVACY_URL", "\"https://evenup.app/privacy\"")
            buildConfigField("String", "TERMS_VERSION", "\"2026-07-23\"")
            buildConfigField("String", "PRIVACY_VERSION", "\"2026-07-23\"")
            buildConfigField("boolean", "AUTH_CONFIGURED", hasGoogleServicesConfigFor("staging").toString())
            buildConfigField("boolean", "APP_CHECK_DEBUG", "false")
            manifestPlaceholders["authHost"] = environmentValue("EVENUP_STAGING_AUTH_LINK_DOMAIN", "evenup-staging.firebaseapp.com")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "WORKER_BASE_URL", "\"https://api.evenup.app\"")
            buildConfigField("String", "AUTH_WEB_CLIENT_ID", "\"${environmentValue("EVENUP_PROD_AUTH_WEB_CLIENT_ID")}\"")
            buildConfigField("String", "AUTH_LINK_DOMAIN", "\"${environmentValue("EVENUP_PROD_AUTH_LINK_DOMAIN", "auth.evenup.app")}\"")
            buildConfigField("String", "TERMS_URL", "\"https://evenup.app/terms\"")
            buildConfigField("String", "PRIVACY_URL", "\"https://evenup.app/privacy\"")
            buildConfigField("String", "TERMS_VERSION", "\"2026-07-23\"")
            buildConfigField("String", "PRIVACY_VERSION", "\"2026-07-23\"")
            buildConfigField("boolean", "AUTH_CONFIGURED", hasGoogleServicesConfigFor("prod").toString())
            buildConfigField("boolean", "APP_CHECK_DEBUG", "false")
            manifestPlaceholders["authHost"] = environmentValue("EVENUP_PROD_AUTH_LINK_DOMAIN", "auth.evenup.app")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        buildConfig = true
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
    implementation(project(":core:speech:api"))
    implementation(project(":core:speech:impl"))
    implementation(project(":core:auth:api"))
    implementation(project(":core:auth:impl"))
    implementation(project(":core:database:api"))
    implementation(project(":core:database:impl"))
    implementation(project(":data:account:api"))
    implementation(project(":data:account:impl"))
    implementation(project(":data:expense:impl"))
    implementation(project(":data:expense:api"))
    implementation(project(":data:participant:api"))
    implementation(project(":data:participant:impl"))
    implementation(project(":data:receipt:api"))
    implementation(project(":data:receipt:impl"))
    implementation(project(":data:sharing:api"))
    implementation(project(":data:sharing:impl"))
    implementation(project(":data:expense-input:api"))
    implementation(project(":data:expense-input:impl"))
    implementation(project(":domain:expense:impl"))
    implementation(project(":domain:participant:impl"))
    implementation(project(":domain:receipt:impl"))
    implementation(project(":domain:sharing:api"))
    implementation(project(":domain:sharing:impl"))
    implementation(project(":domain:expense-input:api"))
    implementation(project(":domain:expense-input:impl"))
    implementation(project(":domain:account:api"))
    implementation(project(":domain:account:impl"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:account:impl"))
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
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
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
