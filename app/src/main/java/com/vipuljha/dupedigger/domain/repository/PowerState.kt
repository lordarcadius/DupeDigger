package com.vipuljha.dupedigger.domain.repository

interface PowerState {
    /** Whether the device is charging right now. */
    fun isCharging(): Boolean
}
