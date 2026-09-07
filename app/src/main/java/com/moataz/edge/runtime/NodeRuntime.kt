package com.moataz.edge.runtime

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NodeStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

object NodeRuntime {
    private val mainHandler = Handler(Looper.getMainLooper())

    var status by mutableStateOf(NodeStatus.STOPPED)
        private set

    var botUsername by mutableStateOf<String?>(null)
        private set

    var processedUpdates by mutableStateOf(0L)
        private set

    var lastMessage by mutableStateOf("لم يتم تشغيل العقدة بعد")
        private set

    fun starting() = post {
        status = NodeStatus.STARTING
        lastMessage = "جارٍ تشغيل عقدة Telegram"
    }

    fun running(username: String?) = post {
        status = NodeStatus.RUNNING
        botUsername = username
        lastMessage = if (username.isNullOrBlank()) "العقدة تعمل" else "@$username متصل"
    }

    fun processed(message: String) = post {
        processedUpdates += 1
        lastMessage = message
    }

    fun error(message: String) = post {
        status = NodeStatus.ERROR
        lastMessage = message
    }

    fun stopped() = post {
        status = NodeStatus.STOPPED
        botUsername = null
        lastMessage = "العقدة متوقفة"
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
