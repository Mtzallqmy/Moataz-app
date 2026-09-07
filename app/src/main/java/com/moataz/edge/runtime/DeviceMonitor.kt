package com.moataz.edge.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import kotlin.math.roundToInt

data class DeviceMetrics(
    val appCpuPercent: Int,
    val appRamMb: Int,
    val availableRamMb: Int,
    val totalRamMb: Int,
    val batteryPercent: Int,
    val batteryTempC: Float?,
    val charging: Boolean,
    val thermal: String,
    val network: String,
    val freeStorageGb: Float,
    val uptimeMinutes: Long
) {
    companion object {
        val EMPTY = DeviceMetrics(0, 0, 0, 0, 0, null, false, "—", "—", 0f, 0)
    }
}

class DeviceMonitor(private val context: Context) {
    private var previousCpuMs = Process.getElapsedCpuTime()
    private var previousWallMs = SystemClock.elapsedRealtime()

    fun snapshot(): DeviceMetrics {
        val nowCpu = Process.getElapsedCpuTime()
        val nowWall = SystemClock.elapsedRealtime()
        val deltaCpu = (nowCpu - previousCpuMs).coerceAtLeast(0)
        val deltaWall = (nowWall - previousWallMs).coerceAtLeast(1)
        previousCpuMs = nowCpu; previousWallMs = nowWall
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cpu = ((deltaCpu.toDouble() / deltaWall.toDouble()) * 100.0 / cores).roundToInt().coerceIn(0, 100)

        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val appRam = (Debug.getPss() / 1024).coerceAtLeast(0)

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPercent = if (level >= 0) (level * 100 / scale.coerceAtLeast(1)) else 0
        val tempRaw = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temp = tempRaw.takeIf { it != Int.MIN_VALUE }?.div(10f)
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "طبيعي"
                PowerManager.THERMAL_STATUS_LIGHT -> "خفيف"
                PowerManager.THERMAL_STATUS_MODERATE -> "متوسط"
                PowerManager.THERMAL_STATUS_SEVERE -> "مرتفع"
                PowerManager.THERMAL_STATUS_CRITICAL -> "حرج"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "طوارئ"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "إيقاف"
                else -> "غير معروف"
            }
        } else temp?.let { if (it >= 43f) "مرتفع" else "طبيعي" } ?: "غير متاح"

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val network = when {
            caps == null -> "غير متصل"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "بيانات الهاتف"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "متصل"
        }

        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val freeGb = stat.availableBytes / 1_073_741_824f

        return DeviceMetrics(
            appCpuPercent = cpu,
            appRamMb = appRam,
            availableRamMb = (mem.availMem / 1_048_576L).toInt(),
            totalRamMb = (mem.totalMem / 1_048_576L).toInt(),
            batteryPercent = batteryPercent,
            batteryTempC = temp,
            charging = charging,
            thermal = thermal,
            network = network,
            freeStorageGb = freeGb,
            uptimeMinutes = SystemClock.elapsedRealtime() / 60_000L
        )
    }
}
