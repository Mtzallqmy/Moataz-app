package com.moataz.edge.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.moataz.edge.MainActivity
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.python.PythonWorker
import com.moataz.edge.telegram.TelegramBotApi
import com.moataz.edge.telegram.TelegramUpdate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class TelegramNodeService : Service() {
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private lateinit var config: ConfigStore
    private lateinit var database: NodeDatabase
    private lateinit var pythonWorker: PythonWorker

    override fun onCreate() {
        super.onCreate()
        config = ConfigStore(this)
        database = NodeDatabase(this)
        pythonWorker = PythonWorker()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopNode()
            return START_NOT_STICKY
        }

        startAsForeground()
        if (running.compareAndSet(false, true)) {
            NodeRuntime.starting()
            workerThread = Thread(::runLoop, "moataz-telegram-node").apply { start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        workerThread?.interrupt()
        workerThread = null
        NodeRuntime.stopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun runLoop() {
        val token = config.botToken()
        if (token.isNullOrBlank()) {
            fail("لا يوجد Bot Token محفوظ")
            return
        }

        val api = TelegramBotApi(token)
        try {
            val username = api.getMe()
            database.log("INFO", "Telegram bot connected: ${username ?: "unknown"}")
            NodeRuntime.running(username)

            var offset = 0L
            while (running.get()) {
                processPendingQueue(api)

                val updates = api.getUpdates(offset)
                for (update in updates) {
                    database.enqueue(update.updateId, update.raw)
                    offset = max(offset, update.updateId + 1)
                }

                if (updates.isNotEmpty()) {
                    database.log("INFO", "Queued ${updates.size} Telegram update(s)")
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            if (running.get()) {
                database.log("ERROR", error.message ?: error.javaClass.simpleName)
                NodeRuntime.error(error.message ?: "تعذر تشغيل Telegram node")
                retryLoop(api)
            }
        } finally {
            if (running.get()) {
                running.set(false)
                stopSelf()
            }
        }
    }

    private fun retryLoop(api: TelegramBotApi) {
        while (running.get()) {
            try {
                Thread.sleep(RETRY_DELAY_MS)
                processPendingQueue(api)
                val username = api.getMe()
                NodeRuntime.running(username)
                runRecoveredLoop(api)
                return
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (error: Exception) {
                database.log("WARN", "Retry failed: ${error.message}")
                NodeRuntime.error("إعادة الاتصال فشلت: ${error.message ?: "خطأ شبكة"}")
            }
        }
    }

    private fun runRecoveredLoop(api: TelegramBotApi) {
        var offset = 0L
        while (running.get()) {
            processPendingQueue(api)
            val updates = api.getUpdates(offset)
            for (update in updates) {
                database.enqueue(update.updateId, update.raw)
                offset = max(offset, update.updateId + 1)
            }
        }
    }

    private fun processPendingQueue(api: TelegramBotApi) {
        val source = config.sourceChat()
        val destination = config.destinationChat()

        for (job in database.pendingJobs()) {
            if (!running.get()) return
            val update = TelegramBotApi.parseUpdate(job.payload)
            if (update == null) {
                database.markDone(job.id)
                continue
            }

            try {
                if (!matchesSource(source, update)) {
                    database.markDone(job.id)
                    continue
                }

                val pythonResult = pythonWorker.processUpdate(update.raw)
                database.log("PYTHON", pythonResult)

                if (destination.isNotBlank()) {
                    api.copyMessage(destination, update.chatId, update.messageId)
                }

                database.markDone(job.id)
                NodeRuntime.processed(
                    if (destination.isBlank()) {
                        "تمت معالجة update ${update.updateId} محليًا"
                    } else {
                        "تم تمرير update ${update.updateId} إلى $destination"
                    }
                )
            } catch (error: Exception) {
                database.markRetry(job.id, job.attempts, error.message ?: error.javaClass.simpleName)
                database.log("WARN", "Job ${job.updateId} failed: ${error.message}")
            }
        }
    }

    private fun matchesSource(source: String, update: TelegramUpdate): Boolean {
        if (source.isBlank()) return true
        if (source == update.chatId) return true
        val normalized = source.removePrefix("@").trim()
        return update.chatUsername?.equals(normalized, ignoreCase = true) == true
    }

    private fun fail(message: String) {
        database.log("ERROR", message)
        NodeRuntime.error(message)
        running.set(false)
        stopSelf()
    }

    private fun stopNode() {
        running.set(false)
        workerThread?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Moataz Edge Node")
            .setContentText("Telegram Long Polling + Python runtime يعمل محليًا")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Edge node runtime",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_START = "com.moataz.edge.action.START_NODE"
        const val ACTION_STOP = "com.moataz.edge.action.STOP_NODE"
        private const val CHANNEL_ID = "edge_node_runtime"
        private const val NOTIFICATION_ID = 1001
        private const val RETRY_DELAY_MS = 5_000L
    }
}
