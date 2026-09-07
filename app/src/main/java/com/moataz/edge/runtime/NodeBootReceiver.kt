package com.moataz.edge.runtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.NodeDatabase

class NodeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val config = ConfigStore(context)
        if (!config.autoStart() || !config.hasBotToken()) return

        runCatching {
            val service = Intent(context, TelegramNodeService::class.java).setAction(TelegramNodeService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service) else context.startService(service)
        }.onFailure {
            NodeDatabase(context).log("WARN", "Auto-start failed: ${it.message}")
        }
    }
}
