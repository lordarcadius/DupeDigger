plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.vipuljha.dupedigger.benchmark"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Test APKs paired with the app's benchmark build types, which are what gets measured.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("nonMinifiedBenchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    jvmToolchain(21)
}

// Macrobenchmark against a debuggable app gives meaningless numbers, so the debug variant isn't built or run.
androidComponents {
    beforeVariants(selector().withBuildType("debug")) { it.enable = false }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
