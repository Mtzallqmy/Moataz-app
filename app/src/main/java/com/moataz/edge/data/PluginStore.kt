package com.moataz.edge.data

import android.content.Context
import com.moataz.edge.security.SecretStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PluginType {
    HTTP_JSON,
    LOCAL_LOG
}

data class PluginConfig(
    val id: String,
    val name: String,
    val type: PluginType,
    val enabled: Boolean,
    val endpoint: String,
    val timeoutSeconds: Int,
    val createdAt: Long
) {
    val destinationToken: String get() = "plugin:$id"
}

class PluginStore(context: Context) {
    private val preferences = context.getSharedPreferences("edge_plugins", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    fun all(): List<PluginConfig> {
        val raw = preferences.getString(KEY_PLUGINS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val type = runCatching { PluginType.valueOf(item.optString("type")) }.getOrDefault(PluginType.HTTP_JSON)
                    add(
                        PluginConfig(
                            id = item.getString("id"),
                            name = item.optString("name", "Plugin"),
                            type = type,
                            enabled = item.optBoolean("enabled", true),
                            endpoint = item.optString("endpoint"),
                            timeoutSeconds = item.optInt("timeout_seconds", 20).coerceIn(3, 60),
                            createdAt = item.optLong("created_at", 0L)
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun addHttp(name: String, endpoint: String, bearerToken: String = ""): PluginConfig {
        val cleanEndpoint = endpoint.trim()
        require(cleanEndpoint.startsWith("https://")) { "HTTP plugin endpoint must use HTTPS" }
        val plugin = PluginConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "HTTP JSON" },
            type = PluginType.HTTP_JSON,
            enabled = true,
            endpoint = cleanEndpoint,
            timeoutSeconds = 20,
            createdAt = System.currentTimeMillis()
        )
        upsert(plugin)
        if (bearerToken.isNotBlank()) secrets.put(secretKey(plugin.id), bearerToken.trim())
        return plugin
    }

    fun addLocalLog(name: String = "Local Log"): PluginConfig {
        val plugin = PluginConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Local Log" },
            type = PluginType.LOCAL_LOG,
            enabled = true,
            endpoint = "",
            timeoutSeconds = 5,
            createdAt = System.currentTimeMillis()
        )
        upsert(plugin)
        return plugin
    }

    fun bearerToken(id: String): String? = secrets.get(secretKey(id))

    fun setEnabled(id: String, enabled: Boolean) {
        all().firstOrNull { it.id == id }?.let { upsert(it.copy(enabled = enabled)) }
    }

    fun delete(id: String) = saveAll(all().filterNot { it.id == id })

    fun get(id: String): PluginConfig? = all().firstOrNull { it.id == id }

    fun upsert(plugin: PluginConfig) {
        val items = all().toMutableList()
        val index = items.indexOfFirst { it.id == plugin.id }
        if (index >= 0) items[index] = plugin else items += plugin
        saveAll(items)
    }

    private fun saveAll(items: List<PluginConfig>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("type", item.type.name)
                    .put("enabled", item.enabled)
                    .put("endpoint", item.endpoint)
                    .put("timeout_seconds", item.timeoutSeconds)
                    .put("created_at", item.createdAt)
            )
        }
        preferences.edit().putString(KEY_PLUGINS, array.toString()).apply()
    }

    private fun secretKey(id: String) = "plugin_bearer_$id"

    private companion object {
        const val KEY_PLUGINS = "plugins_json_v1"
    }
}
