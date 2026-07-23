pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("build-logic")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EvenUp"
include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem:api")
include(":core:designsystem:impl")
include(":core:navigation:api")
include(":core:navigation:impl")
include(":core:network:api")
include(":core:network:impl")
include(":core:datastore:api")
include(":core:datastore:impl")
include(":core:camera:api")
include(":core:camera:impl")
include(":core:speech:api")
include(":core:speech:impl")
include(":core:auth:api")
include(":core:auth:impl")
include(":core:database:api")
include(":core:database:impl")
include(":domain:receipt:api")
include(":domain:receipt:impl")
include(":domain:expense:api")
include(":domain:expense:impl")
include(":domain:participant:api")
include(":domain:participant:impl")
include(":domain:sharing:api")
include(":domain:sharing:impl")
include(":domain:expense-input:api")
include(":domain:expense-input:impl")
include(":domain:account:api")
include(":domain:account:impl")
include(":data:receipt:api")
include(":data:receipt:impl")
include(":data:expense:api")
include(":data:expense:impl")
include(":data:participant:api")
include(":data:participant:impl")
include(":data:sharing:api")
include(":data:sharing:impl")
include(":data:expense-input:api")
include(":data:expense-input:impl")
include(":data:account:api")
include(":data:account:impl")
include(":feature:expense-flow:api")
include(":feature:expense-flow:impl")
include(":feature:account:api")
include(":feature:account:impl")
