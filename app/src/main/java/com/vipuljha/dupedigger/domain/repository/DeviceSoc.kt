package com.vipuljha.dupedigger.domain.repository

/** The chip this device runs on, which decides whether an NPU build of a model exists for it. */
interface DeviceSoc {
    /** The platform's own name for the chip, for example `SM8650`; blank when the platform doesn't say. */
    fun model(): String

    /** Where the app's bundled native libraries live, which the NPU runtime is loaded from. */
    fun nativeLibraryDir(): String

    /** Whether a vendor dispatch library is actually there, which only a bundle installed from Play delivers. */
    fun hasNpuRuntime(): Boolean
}
