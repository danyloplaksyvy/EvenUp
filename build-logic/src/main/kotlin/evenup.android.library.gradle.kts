import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension>("android") {
    namespace = buildString {
        append("com.dps.evenup")
        project.path
            .split(":")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                append(".")
                append(segment.replace("-", ""))
            }
    }

    compileSdk = 37

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val createDebugTypedefsPlaceholder = tasks.register("createDebugTypedefsPlaceholder") {
    val typedefsFile = layout.buildDirectory.file(
        "intermediates/annotations_typedef_file/debug/extractDebugAnnotations/typedefs.txt"
    )
    outputs.file(typedefsFile)

    doLast {
        val file = typedefsFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("")
    }
}

tasks.matching { it.name == "extractDebugAnnotations" }.configureEach {
    enabled = false
}

tasks.matching { it.name == "syncDebugLibJars" }.configureEach {
    dependsOn(createDebugTypedefsPlaceholder)
}
