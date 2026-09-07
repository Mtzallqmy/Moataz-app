package com.moataz.edge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moataz.edge.BuildConfig
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.FailedJob
import com.moataz.edge.data.LogEntry
import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.data.QueueStats
import com.moataz.edge.data.RouteConfig
import com.moataz.edge.data.RouteStore
import com.moataz.edge.runtime.DeviceMetrics
import com.moataz.edge.runtime.DeviceMonitor
import com.moataz.edge.runtime.NodeRuntime
import com.moataz.edge.runtime.NodeStatus
import com.moataz.edge.runtime.WorkerCatalog
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class EdgeTab(val title: String, val icon: EdgeIconType) {
    HOME("الرئيسية", EdgeIconType.HOME), FLOW("التدفق", EdgeIconType.FLOW), WORKERS("العمال", EdgeIconType.WORKERS),
    DIAGNOSTICS("التشخيص", EdgeIconType.DIAGNOSTICS), SETTINGS("الإعدادات", EdgeIconType.SETTINGS)
}

@Composable
fun EdgeApp(
    config: ConfigStore,
    database: NodeDatabase,
    routeStore: RouteStore,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    val context = LocalContext.current
    val monitor = remember { DeviceMonitor(context) }
    var tab by rememberSaveable { mutableStateOf(EdgeTab.HOME) }
    var metrics by remember { mutableStateOf(DeviceMetrics.EMPTY) }
    var routes by remember { mutableStateOf(routeStore.all()) }
    var queue by remember { mutableStateOf(database.queueStats()) }
    var logs by remember { mutableStateOf(database.recentLogs()) }
    var errors by remember { mutableStateOf(database.recentErrors()) }
    var failed by remember { mutableStateOf(database.failedJobs()) }
    var nodeName by remember { mutableStateOf(config.nodeName()) }

    fun refresh() {
        metrics = monitor.snapshot(); routes = routeStore.all(); queue = database.queueStats(); logs = database.recentLogs(); errors = database.recentErrors(); failed = database.failedJobs(); nodeName = config.nodeName()
    }

    LaunchedEffect(Unit) {
        while (true) { refresh(); delay(2500) }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF0A171F)) {
                    EdgeTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { EdgeIcon(item.icon, Modifier.size(23.dp)) },
                            label = { Text(item.title, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                BrandHeader(nodeName)
                when (tab) {
                    EdgeTab.HOME -> DashboardScreen(metrics, queue, logs, routes, onStart, onStop, onRestart)
                    EdgeTab.FLOW -> FlowScreen(routes, routeStore) { refresh() }
                    EdgeTab.WORKERS -> WorkersScreen()
                    EdgeTab.DIAGNOSTICS -> DiagnosticsScreen(context, metrics, queue, errors, failed, database) { refresh() }
                    EdgeTab.SETTINGS -> SettingsScreen(config, nodeName) { nodeName = config.nodeName(); refresh() }
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(nodeName: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF091820)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EdgeMark(Modifier.size(46.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("MOATAZ EDGE", style = MaterialTheme.typography.titleLarge, letterSpacing = 1.2.sp)
            Text(nodeName, color = EdgeMuted, style = MaterialTheme.typography.bodySmall)
        }
        StatusPill(NodeRuntime.status)
    }
}

@Composable
private fun StatusPill(status: NodeStatus) {
    val (text, color) = when (status) {
        NodeStatus.RUNNING -> "متصل" to EdgeMint
        NodeStatus.STARTING -> "يبدأ" to EdgeWarning
        NodeStatus.ERROR -> "خلل" to EdgeDanger
        NodeStatus.STOPPED -> "متوقف" to EdgeMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CircleShape).background(color.copy(alpha=.12f)).padding(horizontal=10.dp,vertical=6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(6.dp)); Text(text, color=color, style=MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DashboardScreen(metrics: DeviceMetrics, queue: QueueStats, logs: List<LogEntry>, routes: List<RouteConfig>, onStart:()->Unit, onStop:()->Unit, onRestart:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = EdgeSurfaceHigh), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مركز التحكم", style=MaterialTheme.typography.headlineSmall)
                Text(NodeRuntime.lastMessage, color=EdgeMuted)
                NodeRuntime.botUsername?.let { Text("@$it", color=EdgeMint, fontWeight=FontWeight.SemiBold) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("تشغيل", EdgeIconType.PLAY, NodeRuntime.status != NodeStatus.RUNNING, onStart)
                    ActionButton("إعادة", EdgeIconType.RESTART, true, onRestart)
                    OutlinedButton(onClick=onStop, enabled=NodeRuntime.status != NodeStatus.STOPPED) { EdgeIcon(EdgeIconType.STOP,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("إيقاف") }
                }
            }
        }

        SectionTitle("الموارد المحلية", "قراءة حية كل 2.5 ثانية")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("CPU التطبيق", "${metrics.appCpuPercent}%", "${Runtime.getRuntime().availableProcessors()} أنوية", Modifier.weight(1f))
            MetricCard("RAM التطبيق", "${metrics.appRamMb} MB", "متاح ${metrics.availableRamMb} MB", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("البطارية", "${metrics.batteryPercent}%", if(metrics.charging) "على الشاحن" else "${metrics.batteryTempC ?: 0f}°C", Modifier.weight(1f))
            MetricCard("الشبكة", metrics.network, "حرارة: ${metrics.thermal}", Modifier.weight(1f))
        }

        SectionTitle("صحة المحرك", "Telegram + Python + Queue + Routes")
        HealthRow("Telegram", if(NodeRuntime.status==NodeStatus.RUNNING) "متصل" else NodeRuntime.status.name, NodeRuntime.telegramLatencyMs?.let{"${it}ms"} ?: "—", NodeRuntime.status==NodeStatus.RUNNING)
        HealthRow("Python 3.13", "${NodeRuntime.pythonCalls} استدعاء", "${NodeRuntime.pythonFailures} فشل", NodeRuntime.pythonFailures==0L)
        HealthRow("Queue", "${queue.pending} انتظار", "${queue.failed} فشل", queue.failed==0)
        HealthRow("Routes", "${routes.count{it.enabled}} فعّال", "${routes.size} إجمالي", routes.any{it.enabled})

        SectionTitle("النشاط الأخير", "سجل مختصر قابل للتشخيص")
        if (logs.isEmpty()) EmptyState("لا يوجد نشاط بعد") else logs.take(5).forEach { CompactLog(it) }
    }
}

@Composable
private fun ActionButton(text:String, icon:EdgeIconType, enabled:Boolean, action:()->Unit){ Button(onClick=action,enabled=enabled){EdgeIcon(icon,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(text)} }

@Composable
private fun MetricCard(title:String,value:String,detail:String,modifier:Modifier=Modifier){ Card(modifier,colors=CardDefaults.cardColors(containerColor=EdgeSurface)){Column(Modifier.padding(14.dp)){Text(title,color=EdgeMuted,style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(5.dp));Text(value,style=MaterialTheme.typography.titleLarge,color=EdgeText);Text(detail,color=EdgeMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}} }

@Composable
private fun HealthRow(name:String,value:String,detail:String,healthy:Boolean){ Card(colors=CardDefaults.cardColors(containerColor=EdgeSurface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).clip(CircleShape).background(if(healthy)EdgeMint else EdgeWarning));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(name,fontWeight=FontWeight.SemiBold);Text(detail,color=EdgeMuted,style=MaterialTheme.typography.bodySmall)};Text(value,color=if(healthy)EdgeMint else EdgeWarning)}} }

@Composable
private fun FlowScreen(routes:List<RouteConfig>, store:RouteStore, onChanged:()->Unit){
    var name by rememberSaveable{mutableStateOf("")};var source by rememberSaveable{mutableStateOf("")};var destination by rememberSaveable{mutableStateOf("")};var keyword by rememberSaveable{mutableStateOf("")};var worker by rememberSaveable{mutableStateOf("default_worker")};var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionTitle("خريطة التدفق","رؤية واضحة لكل اتصال من المصدر حتى الوجهة")
        routes.forEach { route ->
            Card(colors=CardDefaults.cardColors(containerColor=EdgeSurfaceHigh),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(route.name,style=MaterialTheme.typography.titleMedium);Text(if(route.enabled)"مسار فعّال" else "متوقف",color=if(route.enabled)EdgeMint else EdgeMuted,style=MaterialTheme.typography.bodySmall)};Switch(checked=route.enabled,onCheckedChange={store.setEnabled(route.id,it);onChanged()})}
                    FlowNode("المصدر", route.source.ifBlank{"أي محادثة"}, EdgeMint)
                    ConnectorLine()
                    if(route.keyword.isNotBlank()){FlowNode("الفلتر","يحتوي: ${route.keyword}",EdgeGold);ConnectorLine()}
                    FlowNode("Python Worker", WorkerCatalog.workers.firstOrNull{it.id==route.worker}?.title ?: route.worker, EdgeGold)
                    ConnectorLine()
                    FlowNode("الوجهة", route.destination.ifBlank{"معالجة محلية فقط"}, EdgeMint)
                    TextButton(onClick={store.delete(route.id);onChanged()}){Text("حذف المسار",color=EdgeDanger)}
                }
            }
        }
        SectionTitle("إضافة مسار","Source → Filter → Worker → Destination")
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("اسم المسار")},singleLine=true)
        OutlinedTextField(source,{source=it},Modifier.fillMaxWidth(),label={Text("Source chat ID أو @username")},singleLine=true)
        OutlinedTextField(keyword,{keyword=it},Modifier.fillMaxWidth(),label={Text("كلمة فلترة اختيارية")},singleLine=true)
        Box{OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("Worker: ${WorkerCatalog.workers.first{it.id==worker}.title}")};DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){WorkerCatalog.workers.forEach{w->DropdownMenuItem(text={Text(w.title)},onClick={worker=w.id;expanded=false})}}}
        OutlinedTextField(destination,{destination=it},Modifier.fillMaxWidth(),label={Text("Destination chat ID أو @channel")},singleLine=true)
        Button(onClick={store.add(name,source,destination,worker,keyword);name="";source="";destination="";keyword="";onChanged()},modifier=Modifier.fillMaxWidth()){Text("إنشاء المسار")}
    }
}

@Composable private fun FlowNode(label:String,value:String,color:Color){Surface(color=color.copy(alpha=.08f),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,color.copy(alpha=.35f)),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(10.dp).clip(CircleShape).background(color));Spacer(Modifier.width(10.dp));Column{Text(label,color=color,style=MaterialTheme.typography.bodySmall);Text(value,style=MaterialTheme.typography.titleMedium)}}}}
@Composable private fun ConnectorLine(){Box(Modifier.padding(start=22.dp).width(2.dp).height(14.dp).background(MaterialTheme.colorScheme.outline))}

@Composable
private fun WorkersScreen(){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    SectionTitle("Workers المحلية","كلها داخل APK ولا تحتاج pip أو سيرفر خارجي")
    WorkerCatalog.workers.forEach{w->Card(colors=CardDefaults.cardColors(containerColor=EdgeSurfaceHigh),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){EdgeIcon(EdgeIconType.WORKERS,Modifier.size(28.dp),EdgeGold);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(w.title,style=MaterialTheme.typography.titleMedium);Text(w.description,color=EdgeMuted);Text(w.capability,color=EdgeMint,style=MaterialTheme.typography.bodySmall)}}}}
    SectionTitle("Runtime Pack","مكتبات مثبتة وقت بناء APK")
    WorkerCatalog.runtimeLibraries.forEach{lib->Card(colors=CardDefaults.cardColors(containerColor=EdgeSurface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp)){Column(Modifier.weight(1f)){Text(lib.name,fontWeight=FontWeight.SemiBold);Text(lib.purpose,color=EdgeMuted,style=MaterialTheme.typography.bodySmall)};Text(lib.version,color=EdgeMint)}}}
}}

@Composable
private fun DiagnosticsScreen(context:Context,metrics:DeviceMetrics,queue:QueueStats,errors:List<LogEntry>,failed:List<FailedJob>,database:NodeDatabase,onChanged:()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionTitle("التشخيص","أسباب واضحة بدل رسائل خام")
        Card(colors=CardDefaults.cardColors(containerColor=EdgeSurfaceHigh),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("حالة العقدة",style=MaterialTheme.typography.titleMedium);Text("الحالة: ${NodeRuntime.status}");Text("إعادات الاتصال: ${NodeRuntime.reconnects}");Text("Python failures: ${NodeRuntime.pythonFailures}");Text("RAM: ${metrics.appRamMb} MB • CPU: ${metrics.appCpuPercent}%");Text("Battery: ${metrics.batteryPercent}% • Thermal: ${metrics.thermal}")}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={database.retryAllFailed();onChanged()},enabled=failed.isNotEmpty()){Text("إعادة الفاشلة")};OutlinedButton(onClick={database.clearCompleted();onChanged()}){Text("تنظيف المكتملة")};OutlinedButton(onClick={copyDiagnostics(context,metrics,queue,errors)}){Text("نسخ تقرير")}}
        if(failed.isNotEmpty()){SectionTitle("Jobs فاشلة","بعد 10 محاولات");failed.forEach{f->DiagnosticCard("Update ${f.updateId}",f.error,diagnosticHint(f.error),EdgeDanger)}}
        SectionTitle("الأخطاء الأخيرة","تحليل تلقائي للنمط المحتمل")
        if(errors.isEmpty()) EmptyState("لا توجد أخطاء مسجلة") else errors.forEach{e->DiagnosticCard(e.level,e.message,diagnosticHint(e.message),if(e.level=="WARN")EdgeWarning else EdgeDanger)}
        TextButton(onClick={database.clearLogs();onChanged()}){Text("مسح السجلات المحلية",color=EdgeMuted)}
    }
}

private fun copyDiagnostics(context:Context,metrics:DeviceMetrics,queue:QueueStats,errors:List<LogEntry>):()->Unit = {
    val report=buildString{appendLine("Moataz Edge ${BuildConfig.VERSION_NAME}");appendLine("Status=${NodeRuntime.status}");appendLine("Bot=@${NodeRuntime.botUsername ?: "none"}");appendLine("Routes=${NodeRuntime.activeRoutes}");appendLine("Processed=${NodeRuntime.processedUpdates}");appendLine("Python=${NodeRuntime.pythonCalls}/${NodeRuntime.pythonFailures}");appendLine("Reconnects=${NodeRuntime.reconnects}");appendLine("CPU=${metrics.appCpuPercent}% RAM=${metrics.appRamMb}MB Battery=${metrics.batteryPercent}% Thermal=${metrics.thermal} Network=${metrics.network}");appendLine("Queue pending=${queue.pending} failed=${queue.failed} done=${queue.done}");errors.take(8).forEach{appendLine("${it.level}: ${it.message}")}}
    val cm=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText("Moataz Edge diagnostics",report))
}

@Composable private fun DiagnosticCard(title:String,message:String,hint:String,color:Color){Card(colors=CardDefaults.cardColors(containerColor=color.copy(alpha=.08f)),border=BorderStroke(1.dp,color.copy(alpha=.25f)),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(title,color=color,fontWeight=FontWeight.SemiBold);Text(message,style=MaterialTheme.typography.bodySmall);HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.4f));Text(hint,color=EdgeMuted,style=MaterialTheme.typography.bodySmall)}}}
private fun diagnosticHint(message:String):String{val m=message.lowercase();return when{ "401" in m||"unauthorized" in m->"تحقق من Bot Token؛ Telegram رفض بيانات المصادقة.";"403" in m||"forbidden" in m->"تحقق من صلاحيات البوت داخل المجموعة أو القناة ومن إمكانية الإرسال.";"timeout" in m||"timed out" in m->"مهلة شبكة؛ افحص Wi‑Fi/البيانات واستثناءات توفير البطارية.";"chat not found" in m->"معرّف الوجهة غير صحيح أو أن البوت ليس عضوًا فيها.";"python" in m||"module" in m->"Worker Python لم يعمل؛ افحص اسم Worker والمكتبات المضمّنة.";else->"راجع المسار المرتبط بهذا الحدث، ثم اختبر الاتصال والصلاحيات قبل إعادة Job."}}

@Composable
private fun SettingsScreen(config:ConfigStore,currentName:String,onSaved:()->Unit){var name by rememberSaveable{mutableStateOf(currentName)};var token by rememberSaveable{mutableStateOf("")};var autoStart by remember{mutableStateOf(config.autoStart())};var saved by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        SectionTitle("إعدادات العقدة","أسرار محلية داخل Android Keystore")
        OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("اسم العقدة")},singleLine=true)
        OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text(if(config.hasBotToken())"Bot Token محفوظ — اتركه فارغًا للإبقاء عليه" else "Bot Token")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        Card(colors=CardDefaults.cardColors(containerColor=EdgeSurface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("تشغيل بعد إعادة الهاتف",fontWeight=FontWeight.SemiBold);Text("يحاول تشغيل العقدة تلقائيًا عند BOOT_COMPLETED",color=EdgeMuted,style=MaterialTheme.typography.bodySmall)};Switch(checked=autoStart,onCheckedChange={autoStart=it})}}
        Button(onClick={config.setNodeName(name);config.setAutoStart(autoStart);if(token.isNotBlank()){config.saveBotToken(token);token=""};saved=true;onSaved()},modifier=Modifier.fillMaxWidth()){Text("حفظ الإعدادات")}
        if(saved) Text("تم حفظ الإعدادات محليًا",color=EdgeMint)
        HorizontalDivider()
        Text("الإصدار ${BuildConfig.VERSION_NAME} • ARM64 • Android 8+",color=EdgeMuted)
        Text("هذا Release تطويري موقّع بمفتاح Debug لتسهيل الاختبار. قبل توزيع عام سننقله إلى مفتاح توقيع خاص ثابت.",color=EdgeWarning,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SectionTitle(title:String,subtitle:String){Column{Text(title,style=MaterialTheme.typography.titleLarge);Text(subtitle,color=EdgeMuted,style=MaterialTheme.typography.bodySmall)}}
@Composable private fun CompactLog(entry:LogEntry){Card(colors=CardDefaults.cardColors(containerColor=EdgeSurface),modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).clip(CircleShape).background(if(entry.level=="ERROR"||entry.level=="PYTHON_ERROR")EdgeDanger else if(entry.level=="WARN")EdgeWarning else EdgeMint));Spacer(Modifier.width(9.dp));Column(Modifier.weight(1f)){Text(entry.level,style=MaterialTheme.typography.bodySmall,color=EdgeMuted);Text(entry.message,maxLines=2,overflow=TextOverflow.Ellipsis)};Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.createdAt)),color=EdgeMuted,style=MaterialTheme.typography.bodySmall)}}}
@Composable private fun EmptyState(text:String){Surface(color=EdgeSurface,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()){Text(text,Modifier.padding(18.dp),color=EdgeMuted)}}
