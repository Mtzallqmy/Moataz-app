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
        RuntimeLibrary("Python", "3.12", "Managed async runtime + stdlib + SQLite"),
        RuntimeLibrary("aiogram", "3.31.0", "Telegram bot framework"),
        RuntimeLibrary("aiohttp", "3.10.10", "Android ARM64 async HTTP transport"),
        RuntimeLibrary("SQLAlchemy", "2.0.52", "ORM + local SQLite apps"),
        RuntimeLibrary("aiosqlite", "0.21+", "Async SQLite database"),
        RuntimeLibrary("yt-dlp", "2026.8+", "Media extraction/download engine"),
        RuntimeLibrary("pydantic-settings", "2.10+", "Environment/configuration"),
        RuntimeLibrary("requests", "2.32.5", "HTTP client"),
        RuntimeLibrary("BeautifulSoup", "4.13.5", "HTML parsing"),
        RuntimeLibrary("python-dateutil", "2.9.0", "Date/time parsing"),
        RuntimeLibrary("Jinja2", "3.1+", "Templates"),
        RuntimeLibrary("python-multipart", "0.0.20+", "Multipart parsing")
    )
}
