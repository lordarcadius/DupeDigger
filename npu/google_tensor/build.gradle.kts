plugins {
    alias(libs.plugins.android.dynamic.feature)
}

android {
    namespace = "com.vipuljha.dupedigger.npu.google_tensor"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 31
    }
}

dependencies {
    implementation(project(":app"))
    implementation(libs.litert.npu.runtime.google.tensor)
}
