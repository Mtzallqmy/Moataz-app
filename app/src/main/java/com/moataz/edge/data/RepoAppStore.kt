package com.moataz.edge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class RepoAppConfig(
    val id: String,
    val name: String,
    val repositoryUrl: String,
    val ref: String,
    val entryPoint: String,
    val handler: String,
    val enabled: Boolean,
    val autoSync: Boolean,
    val lastSyncedAt: Long,
    val versionHash: String,
    val lastError: String
) {
    val workerId: String get() = "repo:$id"
}

class RepoAppStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("edge_repo_apps", Context.MODE_PRIVATE)

    fun all(): List<RepoAppConfig> {
        val raw = preferences.getString(KEY_APPS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        RepoAppConfig(
                            id = item.getString("id"),
                            name = item.optString("name", "GitHub App"),
                            repositoryUrl = item.optString("repository_url"),
                            ref = item.optString("ref", "main"),
                            entryPoint = item.optString("entry_point", "edge_entry.py"),
                            handler = item.optString("handler", "process_update"),
                            enabled = item.optBoolean("enabled", true),
                            autoSync = item.optBoolean("auto_sync", true),
                            lastSyncedAt = item.optLong("last_synced_at", 0L),
                            versionHash = item.optString("version_hash"),
                            lastError = item.optString("last_error")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun add(
        name: String,
        repositoryUrl: String,
        ref: String,
        entryPoint: String,
        handler: String
    ): RepoAppConfig {
        val cleanUrl = normalizeRepositoryUrl(repositoryUrl)
        val app = RepoAppConfig(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { cleanUrl.substringAfterLast('/').removeSuffix(".git") },
            repositoryUrl = cleanUrl,
            ref = ref.trim().ifBlank { "main" },
            entryPoint = entryPoint.trim().ifBlank { "edge_entry.py" },
            handler = handler.trim().ifBlank { "process_update" },
            enabled = true,
            autoSync = true,
            lastSyncedAt = 0L,
            versionHash = "",
            lastError = ""
        )
        upsert(app)
        return app
    }

    fun upsert(app: RepoAppConfig) {
        val items = all().toMutableList()
        val index = items.indexOfFirst { it.id == app.id }
        if (index >= 0) items[index] = app else items += app
        saveAll(items)
    }

    fun get(id: String): RepoAppConfig? = all().firstOrNull { it.id == id }

    fun setEnabled(id: String, enabled: Boolean) {
        get(id)?.let { upsert(it.copy(enabled = enabled)) }
    }

    fun recordSync(id: String, versionHash: String) {
        get(id)?.let {
            upsert(
                it.copy(
                    lastSyncedAt = System.currentTimeMillis(),
                    versionHash = versionHash,
                    lastError = ""
                )
            )
        }
    }

    fun recordError(id: String, message: String) {
        get(id)?.let { upsert(it.copy(lastError = message.take(500))) }
    }

    fun delete(id: String) {
        saveAll(all().filterNot { it.id == id })
        appRoot(id).deleteRecursively()
    }

    fun currentDir(id: String): File = File(appRoot(id), "current")
    fun previousDir(id: String): File = File(appRoot(id), "previous")
    fun appRoot(id: String): File = File(context.filesDir, "repo_apps/$id")

    fun isInstalled(id: String): Boolean = currentDir(id).isDirectory

    private fun normalizeRepositoryUrl(value: String): String {
        val raw = value.trim().removeSuffix("/")
        require(raw.startsWith("https://github.com/")) { "Only https://github.com repositories are supported" }
        val path = raw.removePrefix("https://github.com/").removeSuffix(".git")
        val parts = path.split('/').filter { it.isNotBlank() }
        require(parts.size == 2) { "Repository URL must be https://github.com/owner/repo" }
        return "https://github.com/${parts[0]}/${parts[1]}"
    }

    private fun saveAll(items: List<RepoAppConfig>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("repository_url", item.repositoryUrl)
                    .put("ref", item.ref)
                    .put("entry_point", item.entryPoint)
                    .put("handler", item.handler)
                    .put("enabled", item.enabled)
                    .put("auto_sync", item.autoSync)
                    .put("last_synced_at", item.lastSyncedAt)
                    .put("version_hash", item.versionHash)
                    .put("last_error", item.lastError)
            )
        }
        preferences.edit().putString(KEY_APPS, array.toString()).apply()
    }

    private companion object {
        const val KEY_APPS = "repo_apps_json_v1"
    }
}
