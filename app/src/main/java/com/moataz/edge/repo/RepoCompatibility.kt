package com.moataz.edge.repo

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CompatibilityCheck(
    val key: String,
    val ok: Boolean,
    val detail: String,
    val blocking: Boolean = true
)

data class RepoManifest(
    val runtime: String = "python-3.12-managed",
    val mode: String = "function",
    val entryPoint: String = "edge_entry.py",
    val handler: String = "process_update",
    val packages: List<String> = emptyList(),
    val capabilities: List<String> = emptyList()
)

data class CompatibilityReport(
    val manifest: RepoManifest,
    val checks: List<CompatibilityCheck>
) {
    val compatible: Boolean get() = checks.none { it.blocking && !it.ok }
    val blockingErrors: List<CompatibilityCheck> get() = checks.filter { it.blocking && !it.ok }

    fun summary(): String {
        if (compatible) return "READY • ${manifest.runtime} • ${manifest.mode}"
        return blockingErrors.joinToString(" | ") { it.detail }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("compatible", compatible)
        .put("runtime", manifest.runtime)
        .put("mode", manifest.mode)
        .put("entry_point", manifest.entryPoint)
        .put("handler", manifest.handler)
        .put("checks", JSONArray().apply {
            checks.forEach { check ->
                put(JSONObject()
                    .put("key", check.key)
                    .put("ok", check.ok)
                    .put("blocking", check.blocking)
                    .put("detail", check.detail))
            }
        })
}

object RepoCompatibility {
    const val HOST_PYTHON = "3.12"

    private val bundled = setOf(
        "requests", "beautifulsoup4", "bs4", "python-dateutil", "dateutil",
        "urllib3", "certifi", "idna", "charset-normalizer", "soupsieve",
        "typing-extensions", "six", "aiogram", "aiohttp", "sqlalchemy",
        "aiosqlite", "pydantic", "pydantic-core", "pydantic-settings",
        "jinja2", "markupsafe", "python-multipart", "yt-dlp", "yarl",
        "multidict", "frozenlist", "aiosignal", "attrs", "magic-filter"
    )

    fun analyze(root: File, configuredEntry: String, configuredHandler: String): CompatibilityReport {
        val manifest = loadManifest(root) ?: RepoManifest(
            entryPoint = configuredEntry.ifBlank { "edge_entry.py" },
            handler = configuredHandler.ifBlank { "process_update" },
            packages = discoverDependencies(root)
        )
        val checks = mutableListOf<CompatibilityCheck>()

        val entry = File(root, manifest.entryPoint)
        checks += CompatibilityCheck(
            "entrypoint",
            entry.isFile,
            if (entry.isFile) "Entry point موجود: ${manifest.entryPoint}" else "Entry point غير موجود: ${manifest.entryPoint}"
        )

        val pythonSpec = discoverPythonSpec(root)
        val pythonOk = pythonSpec.isBlank() || acceptsPython312(pythonSpec)
        checks += CompatibilityCheck(
            "python",
            pythonOk,
            if (pythonSpec.isBlank()) "Python 3.12 Runtime" else "Python $pythonSpec • Host 3.12"
        )

        val unsupported = manifest.packages
            .map(::normalizeRequirement)
            .filter { it.isNotBlank() && it !in bundled && !isVendored(root, it) }
            .distinct()
        checks += CompatibilityCheck(
            "packages",
            unsupported.isEmpty(),
            if (unsupported.isEmpty()) "Runtime packages متوفرة محليًا"
            else "حزم غير متوفرة في Runtime Pack: ${unsupported.joinToString()}"
        )

        val nativeRequested = manifest.capabilities.filter { it in setOf("docker", "shell", "native-exec", "postgres") }
        checks += CompatibilityCheck(
            "capabilities",
            nativeRequested.isEmpty(),
            if (nativeRequested.isEmpty()) "Capabilities مناسبة لـ Android host"
            else "Capabilities غير مدعومة محليًا: ${nativeRequested.joinToString()}"
        )

        val ffmpegRequested = manifest.capabilities.any { it == "ffmpeg" }
        checks += CompatibilityCheck(
            "ffmpeg",
            !ffmpegRequested,
            if (ffmpegRequested) "FFmpeg native pack غير مثبت في هذا الإصدار" else "لا يتطلب FFmpeg",
            blocking = ffmpegRequested
        )

        checks += CompatibilityCheck(
            "mode",
            manifest.mode in setOf("function", "telegram-service"),
            "Mode: ${manifest.mode}"
        )

        return CompatibilityReport(manifest, checks)
    }

    fun loadManifest(root: File): RepoManifest? {
        val file = File(root, "edge.json")
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        return RepoManifest(
            runtime = json.optString("runtime", "python-3.12-managed"),
            mode = json.optString("mode", "function"),
            entryPoint = json.optString("entrypoint", "edge_entry.py"),
            handler = json.optString("handler", "process_update"),
            packages = json.optJSONArray("packages").toStringList(),
            capabilities = json.optJSONArray("capabilities").toStringList()
        )
    }

    fun discoverDependencies(root: File): List<String> {
        val requirements = File(root, "requirements.txt")
        if (requirements.isFile) {
            return requirements.readLines().mapNotNull(::requirementLine)
        }
        val pyproject = File(root, "pyproject.toml")
        if (!pyproject.isFile) return emptyList()
        val text = pyproject.readText()
        val block = Regex("(?s)dependencies\\s*=\\s*\\[(.*?)]").find(text)?.groupValues?.getOrNull(1).orEmpty()
        return Regex("[\"']([^\"']+)[\"']")
            .findAll(block)
            .map { it.groupValues[1] }
            .toList()
    }

    fun discoverPythonSpec(root: File): String {
        val pyproject = File(root, "pyproject.toml")
        if (pyproject.isFile) {
            Regex("requires-python\\s*=\\s*[\"']([^\"']+)[\"']")
                .find(pyproject.readText())
                ?.groupValues?.getOrNull(1)
                ?.let { return it }
        }
        val version = File(root, ".python-version")
        return if (version.isFile) version.readText().trim() else ""
    }

    private fun acceptsPython312(spec: String): Boolean {
        val normalized = spec.replace(" ", "")
        if (normalized.matches(Regex("3\\.12(?:\\.\\d+)?"))) return true
        if (normalized.contains("<3.12") || normalized.contains("<=3.11") || normalized.contains(">=3.13") || normalized.contains(">3.12")) return false
        return true
    }

    private fun requirementLine(line: String): String? {
        val value = line.substringBefore('#').trim()
        if (value.isBlank() || value.startsWith("-") || value.startsWith("git+")) return null
        return value
    }

    private fun normalizeRequirement(raw: String): String {
        return raw.substringBefore(';')
            .substringBefore('[')
            .split("==", ">=", "<=", "~=", "!=", ">", "<")
            .firstOrNull().orEmpty()
            .trim().lowercase().replace('_', '-')
    }

    private fun isVendored(root: File, requirement: String): Boolean {
        val vendor = File(root, "vendor")
        if (!vendor.isDirectory) return false
        val names = listOf(requirement, requirement.replace('-', '_'), requirement.replace("-", ""))
        return names.any { File(vendor, it).exists() }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList { for (i in 0 until length()) add(optString(i)) }
    }
}
