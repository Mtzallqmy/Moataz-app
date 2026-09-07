package com.moataz.edge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.PluginConfig
import com.moataz.edge.data.PluginStore
import com.moataz.edge.data.RepoAppConfig
import com.moataz.edge.data.RepoAppStore
import com.moataz.edge.repo.GitHubRepoManager
import com.moataz.edge.runtime.WorkerCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AppsScreen(
    config: ConfigStore,
    repoStore: RepoAppStore,
    pluginStore: PluginStore,
    repoApps: List<RepoAppConfig>,
    plugins: List<PluginConfig>,
    onChanged: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember { GitHubRepoManager(context, config, repoStore) }
    val scope = rememberCoroutineScope()

    var githubToken by rememberSaveable { mutableStateOf("") }
    var repoName by rememberSaveable { mutableStateOf("") }
    var repoUrl by rememberSaveable { mutableStateOf("") }
    var repoRef by rememberSaveable { mutableStateOf("main") }
    var entryPoint by rememberSaveable { mutableStateOf("edge_entry.py") }
    var handler by rememberSaveable { mutableStateOf("process_update") }
    var busyRepo by remember { mutableStateOf<String?>(null) }

    var pluginName by rememberSaveable { mutableStateOf("") }
    var pluginUrl by rememberSaveable { mutableStateOf("") }
    var pluginToken by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppsSectionTitle("مركز التطبيقات والـPlugins", "GitHub Repository Runtime + Connectors + Python Workers")

        Surface(
            color = EdgeGold.copy(alpha = .08f),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, EdgeGold.copy(alpha = .28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("تشغيل مستودعات GitHub بشكل مُدار", color = EdgeGold, fontWeight = FontWeight.SemiBold)
                Text(
                    "يتم تنزيل ZIP من GitHub، فحص المسارات والحجم والـrequirements، حفظ نسخة سابقة للـRollback، ثم تشغيل Python entry point داخل Runtime المضمن. لا يتم تنفيذ shell/npm/gradle أو pip عشوائي على الهاتف.",
                    color = EdgeMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "العقد: def process_update(raw_update, config_json) ويعيد dict أو str أو None. استخدم مستودعات موثوقة فقط.",
                    color = EdgeMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        AppsSectionTitle("GitHub Access", "اختياري للمستودعات العامة ومطلوب للمستودعات الخاصة")
        OutlinedTextField(
            value = githubToken,
            onValueChange = { githubToken = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (config.hasGithubToken()) "GitHub Token محفوظ — اتركه فارغًا للإبقاء عليه" else "GitHub fine-grained token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Button(
            onClick = {
                runCatching {
                    config.saveGithubToken(githubToken)
                    githubToken = ""
                    message = "تم حفظ GitHub Token داخل Android Keystore"
                }.onFailure { message = it.message.orEmpty() }
            },
            enabled = githubToken.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("حفظ GitHub Token") }

        AppsSectionTitle("إضافة مشروع GitHub", "حوّل مستودع Python إلى Worker/Bot على الهاتف")
        OutlinedTextField(repoName, { repoName = it }, Modifier.fillMaxWidth(), label = { Text("اسم التطبيق") }, singleLine = true)
        OutlinedTextField(repoUrl, { repoUrl = it }, Modifier.fillMaxWidth(), label = { Text("https://github.com/owner/repo") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(repoRef, { repoRef = it }, Modifier.weight(1f), label = { Text("Branch / Tag") }, singleLine = true)
            OutlinedTextField(entryPoint, { entryPoint = it }, Modifier.weight(1f), label = { Text("Entry point") }, singleLine = true)
        }
        OutlinedTextField(handler, { handler = it }, Modifier.fillMaxWidth(), label = { Text("Handler function") }, singleLine = true)
        Button(
            onClick = {
                runCatching {
                    val app = repoStore.add(repoName, repoUrl, repoRef, entryPoint, handler)
                    repoName = ""
                    repoUrl = ""
                    repoRef = "main"
                    entryPoint = "edge_entry.py"
                    handler = "process_update"
                    message = "تمت إضافة ${app.name}. اضغط مزامنة لتنزيله وفحصه."
                    onChanged()
                }.onFailure { message = it.message.orEmpty() }
            },
            enabled = repoUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("إضافة المستودع") }

        if (repoApps.isEmpty()) AppsEmpty("لا توجد تطبيقات GitHub بعد")
        repoApps.forEach { app ->
            RepoAppCard(
                app = app,
                installed = repoStore.isInstalled(app.id),
                compatibility = manager.compatibility(app),
                busy = busyRepo == app.id,
                onToggle = { repoStore.setEnabled(app.id, it); onChanged() },
                onSync = {
                    busyRepo = app.id
                    message = "تتم مزامنة ${app.name}…"
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { manager.sync(app) } }
                        message = result.fold(
                            onSuccess = { "${app.name}: تم تفعيل ${it.versionHash} • ${it.files} ملف" },
                            onFailure = { "${app.name}: ${it.message}" }
                        )
                        busyRepo = null
                        onChanged()
                    }
                },
                onRollback = {
                    busyRepo = app.id
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { manager.rollback(app) }
                        message = if (ok) "${app.name}: تم الرجوع للنسخة السابقة" else "${app.name}: لا توجد نسخة سابقة"
                        busyRepo = null
                        onChanged()
                    }
                },
                onDelete = { repoStore.delete(app.id); onChanged() }
            )
        }

        HorizontalDivider()
        AppsSectionTitle("Connector Plugins", "Telegram مدمج، ويمكن إضافة HTTP JSON أو Local Log كوجهة Route")
        BuiltInPluginCard("Telegram", "Source + Destination", if (config.hasBotToken()) "مهيأ" else "يحتاج Bot Token", config.hasBotToken())
        BuiltInPluginCard("GitHub Repo Runtime", "Processor", "Managed Python 3.13", true)
        BuiltInPluginCard("Python Runtime Pack", "Processor", "${WorkerCatalog.workers.size} Workers مدمجة", true)

        plugins.forEach { plugin ->
            PluginCard(
                plugin = plugin,
                onToggle = { pluginStore.setEnabled(plugin.id, it); onChanged() },
                onDelete = { pluginStore.delete(plugin.id); onChanged() }
            )
        }

        AppsSectionTitle("إضافة HTTP JSON Plugin", "POST إلى HTTPS endpoint مع Bearer Token مشفر اختياريًا")
        OutlinedTextField(pluginName, { pluginName = it }, Modifier.fillMaxWidth(), label = { Text("اسم الـPlugin") }, singleLine = true)
        OutlinedTextField(pluginUrl, { pluginUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS endpoint") }, singleLine = true)
        OutlinedTextField(
            pluginToken,
            { pluginToken = it },
            Modifier.fillMaxWidth(),
            label = { Text("Bearer token اختياري") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )
        Button(
            onClick = {
                runCatching {
                    val plugin = pluginStore.addHttp(pluginName, pluginUrl, pluginToken)
                    pluginName = ""
                    pluginUrl = ""
                    pluginToken = ""
                    message = "تم إنشاء ${plugin.name}. وجهة Route: ${plugin.destinationToken}"
                    onChanged()
                }.onFailure { message = it.message.orEmpty() }
            },
            enabled = pluginUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("إضافة HTTP Plugin") }
        OutlinedButton(
            onClick = {
                val plugin = pluginStore.addLocalLog("Local Log")
                message = "تم إنشاء ${plugin.name}. وجهة Route: ${plugin.destinationToken}"
                onChanged()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("إضافة Local Log Plugin") }

        AppsSectionTitle("Runtime Pack", "الحزم المتاحة لأي GitHub Repo بدون pip على الهاتف")
        WorkerCatalog.runtimeLibraries.forEach { lib ->
            Card(colors = CardDefaults.cardColors(containerColor = EdgeSurface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lib.name, fontWeight = FontWeight.SemiBold)
                        Text(lib.purpose, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(lib.version, color = EdgeMint)
                }
            }
        }

        if (message.isNotBlank()) {
            Surface(color = EdgeMint.copy(alpha = .08f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Text(message, Modifier.padding(14.dp), color = EdgeText)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RepoAppCard(
    app: RepoAppConfig,
    installed: Boolean,
    compatibility: String,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onSync: () -> Unit,
    onRollback: () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = EdgeSurfaceHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EdgeIcon(EdgeIconType.WORKERS, Modifier.size(26.dp), EdgeGold)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium)
                    Text(app.repositoryUrl, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = app.enabled, onCheckedChange = onToggle)
            }
            Text("${app.ref} • ${app.entryPoint} → ${app.handler}", color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
            Text(if (installed) compatibility else "لم تتم المزامنة بعد", color = if (installed) EdgeMint else EdgeWarning, style = MaterialTheme.typography.bodySmall)
            if (app.versionHash.isNotBlank()) {
                val date = if (app.lastSyncedAt > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(app.lastSyncedAt)) else "—"
                Text("نسخة ${app.versionHash} • $date", color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (app.lastError.isNotBlank()) Text(app.lastError, color = EdgeDanger, style = MaterialTheme.typography.bodySmall)
            Text("Worker ID: ${app.workerId}", color = EdgeGold, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSync, enabled = !busy) { Text(if (busy) "انتظر…" else "مزامنة") }
                OutlinedButton(onClick = onRollback, enabled = !busy && installed) { Text("Rollback") }
                TextButton(onClick = onDelete, enabled = !busy) { Text("حذف", color = EdgeDanger) }
            }
        }
    }
}

@Composable
private fun PluginCard(plugin: PluginConfig, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = EdgeSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(if (plugin.enabled) EdgeMint else EdgeMuted))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(plugin.name, fontWeight = FontWeight.SemiBold)
                    Text(plugin.type.name, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = plugin.enabled, onCheckedChange = onToggle)
            }
            if (plugin.endpoint.isNotBlank()) Text(plugin.endpoint, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
            Text("Route destination: ${plugin.destinationToken}", color = EdgeGold, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDelete) { Text("حذف الـPlugin", color = EdgeDanger) }
        }
    }
}

@Composable
private fun BuiltInPluginCard(name: String, capability: String, state: String, healthy: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = EdgeSurface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (healthy) EdgeMint else EdgeWarning))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(capability, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(state, color = if (healthy) EdgeMint else EdgeWarning, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppsSectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AppsEmpty(text: String) {
    Surface(color = EdgeSurface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(18.dp), color = EdgeMuted)
    }
}
