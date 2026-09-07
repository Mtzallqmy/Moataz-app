package com.moataz.edge.python

import com.chaquo.python.Python
import com.moataz.edge.data.RepoAppConfig
import com.moataz.edge.data.RepoAppStore
import org.json.JSONObject

class RepoPythonWorker(private val store: RepoAppStore) {
    fun processUpdate(app: RepoAppConfig, rawUpdate: String, configJson: String): WorkerExecution {
        require(app.enabled) { "Repository app is disabled" }
        val root = store.currentDir(app.id)
        require(root.isDirectory) { "Repository app is not installed. Sync it first." }
        val entry = java.io.File(root, app.entryPoint)
        require(entry.isFile) { "Repository entry point is missing: ${app.entryPoint}" }

        val python = Python.getInstance()
        val rawResult = python.getModule("repo_runner").callAttr(
            "process_update",
            root.absolutePath,
            entry.absolutePath,
            app.handler,
            rawUpdate,
            configJson
        ).toString()
        val result = JSONObject(rawResult)
        return WorkerExecution(
            action = result.optString("action", "pass"),
            outputText = result.optString("output_text").takeIf { it.isNotBlank() },
            note = result.optString("note", "GitHub repository handler completed")
        )
    }
}
