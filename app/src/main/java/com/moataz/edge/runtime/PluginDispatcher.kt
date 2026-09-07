package com.moataz.edge.runtime

import com.moataz.edge.data.NodeDatabase
import com.moataz.edge.data.PluginConfig
import com.moataz.edge.data.PluginStore
import com.moataz.edge.data.PluginType
import com.moataz.edge.data.RouteConfig
import com.moataz.edge.telegram.TelegramUpdate
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PluginDispatcher(
    private val store: PluginStore,
    private val database: NodeDatabase
) {
    fun deliver(plugin: PluginConfig, route: RouteConfig, update: TelegramUpdate, outputText: String?): String {
        require(plugin.enabled) { "Plugin ${plugin.name} is disabled" }
        return when (plugin.type) {
            PluginType.LOCAL_LOG -> {
                val text = outputText ?: update.text ?: "[non-text update ${update.updateId}]"
                database.log("PLUGIN", "${plugin.name}: ${text.take(500)}")
                "local-log"
            }
            PluginType.HTTP_JSON -> deliverHttp(plugin, route, update, outputText)
        }
    }

    private fun deliverHttp(plugin: PluginConfig, route: RouteConfig, update: TelegramUpdate, outputText: String?): String {
        require(plugin.endpoint.startsWith("https://")) { "HTTP plugin requires HTTPS" }
        val body = JSONObject()
            .put("event", "telegram.update")
            .put("route_id", route.id)
            .put("route_name", route.name)
            .put("update_id", update.updateId)
            .put("chat_id", update.chatId)
            .put("message_id", update.messageId)
            .put("chat_username", update.chatUsername)
            .put("input_text", update.text)
            .put("output_text", outputText)
            .put("raw_update", JSONObject(update.raw))
            .toString()

        val connection = (URL(plugin.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = plugin.timeoutSeconds * 1_000
            readTimeout = plugin.timeoutSeconds * 1_000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Moataz-Edge/${com.moataz.edge.BuildConfig.VERSION_NAME}")
            store.bearerToken(plugin.id)?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val response = runCatching {
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty().take(1000)
        }.getOrDefault("")
        connection.disconnect()
        require(code in 200..299) { "Plugin ${plugin.name} returned HTTP $code ${response.take(300)}" }
        database.log("PLUGIN", "${plugin.name} delivered route ${route.name}: HTTP $code")
        return "http-$code"
    }
}
