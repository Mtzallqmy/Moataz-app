package com.moataz.edge.python

import com.chaquo.python.Python

class PythonWorker {
    fun processUpdate(rawUpdate: String): String {
        val python = Python.getInstance()
        val worker = python.getModule("worker")
        return worker.callAttr("process_update", rawUpdate).toString()
    }
}
