package com.moataz.edge.runtime

data class WorkerDefinition(val id: String, val title: String, val description: String, val capability: String)
data class RuntimeLibrary(val name: String, val version: String, val purpose: String)

object WorkerCatalog {
    val workers = listOf(
        WorkerDefinition("default_worker", "الممر الافتراضي", "يفحص التحديث محليًا ويمرره كما هو.", "Routing"),
        WorkerDefinition("cleaner_worker", "منظف النص", "ينظف المسافات والأسطر ويرسل النص المنظف محليًا.", "Transform"),
        WorkerDefinition("keyword_worker", "حارس الكلمات", "يسمح فقط بالنصوص المطابقة لكلمة المسار عند وجودها.", "Filter")
    )

    val runtimeLibraries = listOf(
        RuntimeLibrary("requests", "2.32.5", "HTTP client"),
        RuntimeLibrary("BeautifulSoup", "4.13.5", "HTML parsing"),
        RuntimeLibrary("python-dateutil", "2.9.0", "Date/time parsing"),
        RuntimeLibrary("Python stdlib", "3.13", "JSON, SQLite, asyncio, XML/RSS, regex, pathlib وغيرها")
    )
}
