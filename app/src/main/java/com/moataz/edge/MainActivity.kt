package com.moataz.edge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.LogEntry
import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.runtime.NodeRuntime
import com.moataz.edge.runtime.TelegramNodeService
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var config: ConfigStore
    private lateinit var database: NodeDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        database = NodeDatabase(this)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Dashboard(
                        config = config,
                        database = database,
                        onStart = ::startNode,
                        onStop = ::stopNode
                    )
                }
            }
        }
    }

    private fun startNode() {
        val intent = Intent(this, TelegramNodeService::class.java).setAction(TelegramNodeService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopNode() {
        stopService(Intent(this, TelegramNodeService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}

@Composable
private fun Dashboard(
    config: ConfigStore,
    database: NodeDatabase,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var token by rememberSaveable { mutableStateOf("") }
    var sourceChat by rememberSaveable { mutableStateOf(config.sourceChat()) }
    var destinationChat by rememberSaveable { mutableStateOf(config.destinationChat()) }
    var hasToken by remember { mutableStateOf(config.hasBotToken()) }
    var logs by remember { mutableStateOf(database.recentLogs()) }
    var queueCounts by remember { mutableStateOf(database.queueCounts()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Moataz Edge Node", style = MaterialTheme.typography.headlineMedium)
        Text("عقدة Android محلية لـ Telegram وPython", style = MaterialTheme.typography.bodyMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الحالة: ${NodeRuntime.status}", style = MaterialTheme.typography.titleMedium)
                Text(NodeRuntime.lastMessage)
                NodeRuntime.botUsername?.let { Text("البوت: @$it") }
                Text("المهام المعالجة: ${NodeRuntime.processedUpdates}")
                Text("Queue: ${queueCounts.first} معلقة / ${queueCounts.second} فاشلة")
            }
        }

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (hasToken) "Bot Token (محفوظ — اتركه فارغًا للإبقاء عليه)" else "Bot Token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        OutlinedTextField(
            value = sourceChat,
            onValueChange = { sourceChat = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Source chat ID أو @username") },
            supportingText = { Text("اتركه فارغًا لمعالجة جميع الرسائل التي يستقبلها البوت") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true
        )

        OutlinedTextField(
            value = destinationChat,
            onValueChange = { destinationChat = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Destination chat ID أو @channel") },
            supportingText = { Text("اتركه فارغًا لتشغيل Python فقط بدون إعادة إرسال") },
            singleLine = true
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (token.isNotBlank()) {
                    config.saveBotToken(token)
                    token = ""
                    hasToken = true
                }
                config.saveRouting(sourceChat, destinationChat)
                onStart()
            }) {
                Text("حفظ وتشغيل")
            }
            TextButton(onClick = onStop) {
                Text("إيقاف")
            }
            TextButton(onClick = {
                logs = database.recentLogs()
                queueCounts = database.queueCounts()
            }) {
                Text("تحديث")
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("آخر السجلات", style = MaterialTheme.typography.titleMedium)
        if (logs.isEmpty()) {
            Text("لا توجد سجلات بعد")
        } else {
            logs.forEach { LogRow(it) }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${entry.level} • ${DateFormat.getTimeInstance().format(Date(entry.createdAt))}")
            Text(entry.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
