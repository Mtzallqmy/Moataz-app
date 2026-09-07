package com.moataz.edge.python

import com.chaquo.python.Python
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.RepoAppConfig
import com.moataz.edge.data.RepoAppStore
import org.json.JSONObject
import java.io.File

class RepoPythonWorker(
    private val store: RepoAppStore,
    private val config: ConfigStore
) {
    fun processUpdate(app: RepoAppConfig, rawUpdate: String, configJson: String): WorkerExecution {
        require(app.enabled) { "Repository app is disabled" }
        val root = store.currentDir(app.id)
        require(root.isDirectory) { "Repository app is not installed. Sync it first." }
        val entry = File(root, app.entryPoint)
        require(entry.isFile) { "Repository entry point is missing: ${app.entryPoint}" }

        val python = Python.getInstance()
        val rawResult = python.getModule("repo_runner").callAttr(
            "process_update",
            root.absolutePath,
            entry.absolutePath,
            app.handler,
            rawUpdate,
            configJson,
            runtimeJson(app)
        ).toString()
        val result = JSONObject(rawResult)
        return WorkerExecution(
            action = result.optString("action", "pass"),
            outputText = result.optString("output_text").takeIf { it.isNotBlank() },
            note = result.optString("note", "GitHub repository handler completed")
        )
    }

    fun health(app: RepoAppConfig): JSONObject {
        val root = store.currentDir(app.id)
        if (!root.isDirectory) return JSONObject().put("ok", false).put("detail", "not installed")
        val entry = File(root, app.entryPoint)
        if (!entry.isFile) return JSONObject().put("ok", false).put("detail", "entry point missing")
        return runCatching {
            val raw = Python.getInstance().getModule("repo_runner").callAttr(
                "health",
                root.absolutePath,
                entry.absolutePath,
                runtimeJson(app)
            ).toString()
            JSONObject(raw)
        }.getOrElse {
            JSONObject().put("ok", false).put("detail", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun runtimeJson(app: RepoAppConfig): String {
        val dataDir = File(store.appRoot(app.id), "data").apply { mkdirs() }
        val cacheDir = File(store.appRoot(app.id), "cache").apply { mkdirs() }
        val downloadsDir = File(dataDir, "downloads").apply { mkdirs() }
        return JSONObject()
            .put("runtime", "python-managed-3.12")
            .put("app_id", app.id)
            .put("data_dir", dataDir.absolutePath)
            .put("cache_dir", cacheDir.absolutePath)
            .put("downloads_dir", downloadsDir.absolutePath)
            .put("database_url", "sqlite+aiosqlite:///${File(dataDir, "app.db").absolutePath}")
            .put("bot_token", config.botToken().orEmpty())
            .toString()
    }
}
