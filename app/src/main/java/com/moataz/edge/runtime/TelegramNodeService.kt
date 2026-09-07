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
import android.os.SystemClock
import com.moataz.edge.MainActivity
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.data.PluginStore
import com.moataz.edge.data.RepoAppStore
import com.moataz.edge.data.RouteConfig
import com.moataz.edge.data.RouteStore
import com.moataz.edge.python.PythonWorker
import com.moataz.edge.python.RepoPythonWorker
import com.moataz.edge.python.WorkerExecution
import com.moataz.edge.repo.GitHubRepoManager
import com.moataz.edge.telegram.TelegramBotApi
import com.moataz.edge.telegram.TelegramUpdate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class TelegramNodeService : Service() {
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private lateinit var config: ConfigStore
    private lateinit var routes: RouteStore
    private lateinit var database: NodeDatabase
    private lateinit var plugins: PluginStore
    private lateinit var repoApps: RepoAppStore
    private lateinit var pythonWorker: PythonWorker
    private lateinit var repoWorker: RepoPythonWorker
    private lateinit var repoManager: GitHubRepoManager
    private lateinit var pluginDispatcher: PluginDispatcher

    override fun onCreate() {
        super.onCreate()
        config = ConfigStore(this)
        routes = RouteStore(this, config)
        database = NodeDatabase(this)
        plugins = PluginStore(this)
        repoApps = RepoAppStore(this)
        pythonWorker = PythonWorker()
        repoWorker = RepoPythonWorker(repoApps, config)
        repoManager = GitHubRepoManager(this, config, repoApps)
        pluginDispatcher = PluginDispatcher(plugins, database)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopNode(); return START_NOT_STICKY }
        startAsForeground()
        if (running.compareAndSet(false, true)) {
            NodeRuntime.starting(routes.all().count { it.enabled })
            workerThread = Thread(::runLoop, "moataz-edge-node").apply { start() }
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
        if (token.isNullOrBlank()) { fail("لا يوجد Bot Token محفوظ"); return }

        syncAutoRepoApps()
        prewarmRepoApps()

        val api = TelegramBotApi(token)
        var offset = config.telegramOffset()
        var retryDelay = 2_000L
        var connected = false

        while (running.get()) {
            try {
                if (!connected) {
                    val started = SystemClock.elapsedRealtime()
                    val username = api.getMe()
                    val latency = SystemClock.elapsedRealtime() - started
                    database.log("INFO", "Telegram connected as @${username ?: "unknown"} (${latency}ms)")
                    NodeRuntime.running(username, latency)
                    connected = true
                    retryDelay = 2_000L
                }

                processPendingQueue(api)
                val updates = api.getUpdates(offset)
                for (update in updates) {
                    database.enqueue(update.updateId, update.raw)
                    offset = max(offset, update.updateId + 1)
                }
                if (updates.isNotEmpty()) {
                    config.saveTelegramOffset(offset)
                    database.log("INFO", "Received ${updates.size} Telegram update(s)")
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (error: Exception) {
                connected = false
                val message = error.message ?: error.javaClass.simpleName
                database.log("ERROR", message)
                NodeRuntime.reconnect(message)
                try { Thread.sleep(retryDelay) } catch (_: InterruptedException) { return }
                retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun syncAutoRepoApps() {
        val now = System.currentTimeMillis()
        val syncAge = 6L * 60L * 60L * 1000L
        repoApps.all()
            .filter { it.enabled && it.autoSync }
            .filter { !repoApps.isInstalled(it.id) || now - it.lastSyncedAt >= syncAge }
            .forEach { app ->
                if (!running.get()) return
                try {
                    val result = repoManager.sync(app)
                    database.log("REPO", "${app.name} synced ${result.versionHash} (${result.files} files)")
                } catch (error: Exception) {
                    database.log("WARN", "Repo sync ${app.name}: ${error.message}")
                }
            }
    }

    private fun prewarmRepoApps() {
        repoApps.all().filter { it.enabled && repoApps.isInstalled(it.id) }.forEach { app ->
            if (!running.get()) return
            val health = repoWorker.health(app)
            val ok = health.optBoolean("ok", false)
            val detail = health.optString("detail", if (ok) "ready" else "health failed")
            database.log(if (ok) "REPO" else "ERROR", "${app.name} health: $detail")
            if (!ok) repoApps.recordError(app.id, detail)
        }
    }

    private fun processPendingQueue(api: TelegramBotApi) {
        val enabledRoutes = routes.all().filter { it.enabled }
        for (job in database.pendingJobs()) {
            if (!running.get()) return
            val update = TelegramBotApi.parseUpdate(job.payload)
            if (update == null) { database.markDone(job.id); continue }

            val matching = enabledRoutes.filter { matchesRoute(it, update) }
            if (matching.isEmpty()) { database.markDone(job.id); continue }

            try {
                var delivered = 0
                for (route in matching) {
                    val execution = try {
                        executeWorker(route, update).also { NodeRuntime.pythonCall(true) }
                    } catch (error: Exception) {
                        NodeRuntime.pythonCall(false)
                        database.log("PYTHON_ERROR", "${route.name}: ${error.message}")
                        throw error
                    }

                    database.log("PYTHON", "${route.name} • ${execution.note}")
                    if (!execution.shouldPass) continue
                    if (route.destination.isNotBlank()) {
                        deliver(route, update, execution, api)
                        delivered += 1
                    }
                }
                database.markDone(job.id)
                NodeRuntime.processed("update ${update.updateId} • ${matching.size} route(s) • $delivered delivery")
            } catch (error: Exception) {
                database.markRetry(job.id, job.attempts, error.message ?: error.javaClass.simpleName)
                database.log("WARN", "Job ${job.updateId} retry ${job.attempts + 1}: ${error.message}")
            }
        }
    }

    private fun executeWorker(route: RouteConfig, update: TelegramUpdate): WorkerExecution {
        return if (route.worker.startsWith(REPO_WORKER_PREFIX)) {
            val id = route.worker.removePrefix(REPO_WORKER_PREFIX)
            val app = repoApps.get(id) ?: error("Repository app not found: $id")
            repoWorker.processUpdate(app, update.raw, route.workerConfigJson())
        } else {
            pythonWorker.processUpdate(route.worker, update.raw, route.workerConfigJson())
        }
    }

    private fun deliver(route: RouteConfig, update: TelegramUpdate, execution: WorkerExecution, api: TelegramBotApi) {
        val destination = route.destination.trim()
        when {
            destination == SOURCE_DESTINATION -> {
                if (!execution.outputText.isNullOrBlank()) api.sendMessage(update.chatId, execution.outputText)
                else api.copyMessage(update.chatId, update.chatId, update.messageId)
            }
            destination.startsWith(PLUGIN_DESTINATION_PREFIX) -> {
                val pluginId = destination.removePrefix(PLUGIN_DESTINATION_PREFIX)
                val plugin = plugins.get(pluginId) ?: error("Plugin not found: $pluginId")
                pluginDispatcher.deliver(plugin, route, update, execution.outputText)
            }
            else -> {
                if (!execution.outputText.isNullOrBlank()) api.sendMessage(destination, execution.outputText)
                else api.copyMessage(destination, update.chatId, update.messageId)
            }
        }
    }

    private fun matchesRoute(route: RouteConfig, update: TelegramUpdate): Boolean {
        val sourceOk = when {
            route.source.isBlank() -> true
            route.source == update.chatId -> true
            else -> update.chatUsername?.equals(route.source.removePrefix("@").trim(), ignoreCase = true) == true
        }
        if (!sourceOk) return false
        if (route.keyword.isBlank()) return true
        return update.text?.contains(route.keyword, ignoreCase = true) == true
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
        } else startForeground(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder
            .setContentTitle("Moataz Edge • ${config.nodeName()}")
            .setContentText("Local PaaS + Telegram + GitHub Apps تعمل على الهاتف")
            .setSmallIcon(com.moataz.edge.R.drawable.ic_edge_mark)
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Edge node runtime", NotificationManager.IMPORTANCE_LOW))
        }
    }

    companion object {
        const val ACTION_START = "com.moataz.edge.action.START_NODE"
        const val ACTION_STOP = "com.moataz.edge.action.STOP_NODE"
        const val SOURCE_DESTINATION = "@source"
        const val REPO_WORKER_PREFIX = "repo:"
        const val PLUGIN_DESTINATION_PREFIX = "plugin:"
        private const val CHANNEL_ID = "edge_node_runtime"
        private const val NOTIFICATION_ID = 1001
    }
}
