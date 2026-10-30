package com.vipuljha.dupedigger.data.storage

import android.content.Context
import android.os.BatteryManager
import com.vipuljha.dupedigger.domain.repository.PowerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidPowerState @Inject constructor(@param:ApplicationContext private val context: Context) : PowerState {
    override fun isCharging(): Boolean = context.getSystemService(BatteryManager::class.java)?.isCharging == true
}
