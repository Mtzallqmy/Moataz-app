package com.moataz.edge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.data.RouteStore
import com.moataz.edge.runtime.TelegramNodeService
import com.moataz.edge.ui.EdgeApp
import com.moataz.edge.ui.EdgeTheme

class MainActivity : ComponentActivity() {
    private lateinit var config: ConfigStore
    private lateinit var database: NodeDatabase
    private lateinit var routes: RouteStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        database = NodeDatabase(this)
        routes = RouteStore(this, config)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()

        setContent {
            EdgeTheme {
                EdgeApp(
                    config = config,
                    database = database,
                    routeStore = routes,
                    onStart = ::startNode,
                    onStop = ::stopNode,
                    onRestart = ::restartNode
                )
            }
        }
    }

    private fun startNode() {
        val intent = Intent(this, TelegramNodeService::class.java).setAction(TelegramNodeService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopNode() {
        stopService(Intent(this, TelegramNodeService::class.java))
    }

    private fun restartNode() {
        stopNode()
        Handler(Looper.getMainLooper()).postDelayed(::startNode, 500)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
