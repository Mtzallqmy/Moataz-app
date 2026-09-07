package com.moataz.edge.python

import com.chaquo.python.Python
import org.json.JSONObject

data class WorkerExecution(val action: String, val outputText: String?, val note: String) {
    val shouldPass: Boolean get() = action.lowercase() != "drop"
}

class PythonWorker {
    fun processUpdate(moduleName: String, rawUpdate: String, configJson: String): WorkerExecution {
        val python = Python.getInstance()
        val module = python.getModule(moduleName)
        val rawResult = module.callAttr("process_update", rawUpdate, configJson).toString()
        val result = JSONObject(rawResult)
        return WorkerExecution(
            action = result.optString("action", "pass"),
            outputText = result.optString("output_text").takeIf { it.isNotBlank() },
            note = result.optString("note", "تمت المعالجة محليًا")
        )
    }

    fun selfTest(): String {
        val python = Python.getInstance()
        return python.getModule("runtime_info").callAttr("diagnostics").toString()
    }
}
