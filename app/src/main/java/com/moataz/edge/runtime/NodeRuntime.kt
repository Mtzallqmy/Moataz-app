package com.moataz.edge.runtime

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NodeStatus { STOPPED, STARTING, RUNNING, ERROR }

object NodeRuntime {
    private val mainHandler = Handler(Looper.getMainLooper())

    var status by mutableStateOf(NodeStatus.STOPPED); private set
    var botUsername by mutableStateOf<String?>(null); private set
    var processedUpdates by mutableStateOf(0L); private set
    var pythonCalls by mutableStateOf(0L); private set
    var pythonFailures by mutableStateOf(0L); private set
    var reconnects by mutableStateOf(0L); private set
    var telegramLatencyMs by mutableStateOf<Long?>(null); private set
    var activeRoutes by mutableStateOf(0); private set
    var lastUpdateAt by mutableStateOf<Long?>(null); private set
    var startedAt by mutableStateOf<Long?>(null); private set
    var lastMessage by mutableStateOf("لم يتم تشغيل العقدة بعد"); private set
    var lastError by mutableStateOf<String?>(null); private set

    fun starting(routeCount: Int) = post {
        status = NodeStatus.STARTING; activeRoutes = routeCount; lastError = null
        lastMessage = "تهيئة Telegram وPython والـRoutes"
    }

    fun running(username: String?, latencyMs: Long? = null) = post {
        status = NodeStatus.RUNNING; botUsername = username; lastError = null
        if (startedAt == null) startedAt = System.currentTimeMillis()
        if (latencyMs != null) telegramLatencyMs = latencyMs
        lastMessage = if (username.isNullOrBlank()) "العقدة متصلة" else "@$username متصل"
    }

    fun processed(message: String) = post {
        processedUpdates += 1; lastUpdateAt = System.currentTimeMillis(); lastMessage = message
    }

    fun pythonCall(success: Boolean) = post {
        pythonCalls += 1
        if (!success) pythonFailures += 1
    }

    fun reconnect(message: String) = post {
        reconnects += 1; status = NodeStatus.ERROR; lastError = message; lastMessage = "محاولة إعادة اتصال"
    }

    fun error(message: String) = post {
        status = NodeStatus.ERROR; lastError = message; lastMessage = message
    }

    fun stopped() = post {
        status = NodeStatus.STOPPED; botUsername = null; startedAt = null; activeRoutes = 0
        lastMessage = "العقدة متوقفة"
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
