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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Dupe Digger"
include(":app")
// The NPU runtime modules are arm64 only, which an x86 emulator can't install; -PnpuFeatures=false leaves them out.
if (providers.gradleProperty("npuFeatures").orNull != "false") {
    include(":npu:qualcomm")
    include(":npu:mediatek")
    include(":npu:google_tensor")
}
include(":benchmark")
 