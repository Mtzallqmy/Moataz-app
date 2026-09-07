package com.moataz.edge.repo

import android.content.Context
import com.moataz.edge.data.ConfigStore
import com.moataz.edge.data.RepoAppConfig
import com.moataz.edge.data.RepoAppStore
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class RepoSyncResult(
    val versionHash: String,
    val files: Int,
    val bytes: Long,
    val entryPoint: String,
    val handler: String,
    val runtime: String,
    val mode: String,
    val compatibility: String
)

class GitHubRepoManager(
    private val context: Context,
    private val config: ConfigStore,
    private val store: RepoAppStore
) {
    fun sync(app: RepoAppConfig): RepoSyncResult {
        val (owner, repo) = parseRepo(app.repositoryUrl)
        val appRoot = store.appRoot(app.id).apply { mkdirs() }
        val archive = File(appRoot, "download.zip")
        val staging = File(appRoot, "staging")
        archive.delete()
        staging.deleteRecursively()
        staging.mkdirs()

        try {
            downloadArchive(owner, repo, app.ref, archive)
            val digest = sha256(archive)
            val stats = unzipSafely(archive, staging)
            val sourceRoot = singleSourceRoot(staging)
            val report = RepoCompatibility.analyze(sourceRoot, app.entryPoint, app.handler)
            store.recordCompatibility(app.id, report.toJson().toString())

            require(report.compatible) {
                val reasons = report.blockingErrors.joinToString(" • ") { it.detail }
                "Repository is not compatible with local runtime: $reasons"
            }

            val resolvedApp = app.copy(
                entryPoint = report.manifest.entryPoint,
                handler = report.manifest.handler,
                runtime = report.manifest.runtime,
                mode = report.manifest.mode,
                compatibilityJson = report.toJson().toString()
            )
            store.upsert(resolvedApp)
            activate(resolvedApp, sourceRoot, digest.take(16))
            store.recordSync(resolvedApp.id, digest.take(16))
            return RepoSyncResult(
                versionHash = digest.take(16),
                files = stats.first,
                bytes = stats.second,
                entryPoint = resolvedApp.entryPoint,
                handler = resolvedApp.handler,
                runtime = resolvedApp.runtime,
                mode = resolvedApp.mode,
                compatibility = report.summary()
            )
        } catch (error: Exception) {
            store.recordError(app.id, error.message ?: error.javaClass.simpleName)
            throw error
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    fun rollback(app: RepoAppConfig): Boolean {
        val root = store.appRoot(app.id)
        val current = store.currentDir(app.id)
        val previous = store.previousDir(app.id)
        if (!previous.isDirectory) return false
        val swap = File(root, "rollback_swap")
        swap.deleteRecursively()
        if (current.exists() && !current.renameTo(swap)) return false
        if (!previous.renameTo(current)) {
            if (swap.exists()) swap.renameTo(current)
            return false
        }
        if (swap.exists()) swap.renameTo(previous)
        store.recordSync(app.id, File(current, ".edge-version").takeIf { it.isFile }?.readText()?.trim().orEmpty().ifBlank {
            "rollback-${System.currentTimeMillis()}"
        })
        val report = RepoCompatibility.analyze(current, app.entryPoint, app.handler)
        store.recordCompatibility(app.id, report.toJson().toString())
        return true
    }

    fun compatibilityReport(app: RepoAppConfig): CompatibilityReport? {
        val root = store.currentDir(app.id)
        if (!root.isDirectory) return null
        return RepoCompatibility.analyze(root, app.entryPoint, app.handler)
    }

    fun compatibility(app: RepoAppConfig): String {
        return compatibilityReport(app)?.summary() ?: "غير مثبت"
    }

    private fun downloadArchive(owner: String, repo: String, ref: String, target: File) {
        val safeRef = URLEncoder.encode(ref, StandardCharsets.UTF_8.name()).replace("+", "%20")
        var url = URL("https://api.github.com/repos/$owner/$repo/zipball/$safeRef")
        var redirects = 0
        val githubToken = config.githubToken()?.trim().orEmpty()

        while (true) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Moataz-Edge/${com.moataz.edge.BuildConfig.VERSION_NAME}")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                if (githubToken.isNotBlank() && (url.host == "api.github.com" || url.host == "github.com")) {
                    setRequestProperty("Authorization", "Bearer $githubToken")
                }
            }
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: error("GitHub archive redirect is missing Location")
                connection.disconnect()
                redirects += 1
                require(redirects <= 5) { "Too many GitHub redirects" }
                url = URL(location)
                continue
            }
            if (code !in 200..299) {
                val body = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull().orEmpty().take(500)
                connection.disconnect()
                error("GitHub download failed: HTTP $code ${body.ifBlank { "" }}".trim())
            }
            val length = connection.contentLengthLong
            if (length > MAX_ARCHIVE_BYTES) {
                connection.disconnect()
                error("Repository archive is too large (${length / 1_048_576} MB). Limit is ${MAX_ARCHIVE_BYTES / 1_048_576} MB")
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ARCHIVE_BYTES) { "Repository archive exceeded size limit" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            connection.disconnect()
            return
        }
    }

    private fun unzipSafely(archive: File, destination: File): Pair<Int, Long> {
        var files = 0
        var totalBytes = 0L
        val rootPath = destination.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(destination, entry.name)
                val canonical = output.canonicalPath
                require(canonical == destination.canonicalPath || canonical.startsWith(rootPath)) { "Unsafe ZIP path: ${entry.name}" }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    files += 1
                    require(files <= MAX_FILES) { "Repository contains too many files" }
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            require(totalBytes <= MAX_UNPACKED_BYTES) { "Repository unpacked size exceeded limit" }
                            stream.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return files to totalBytes
    }

    private fun singleSourceRoot(staging: File): File {
        val children = staging.listFiles()?.filter { it.name != "__MACOSX" }.orEmpty()
        return if (children.size == 1 && children[0].isDirectory) children[0] else staging
    }

    private fun activate(app: RepoAppConfig, sourceRoot: File, versionHash: String) {
        val root = store.appRoot(app.id)
        val current = store.currentDir(app.id)
        val previous = store.previousDir(app.id)
        val next = File(root, "next")
        next.deleteRecursively()
        copyDirectory(sourceRoot, next)
        File(next, ".edge-version").writeText(versionHash)
        previous.deleteRecursively()
        if (current.exists()) require(current.renameTo(previous)) { "Unable to preserve previous repository version" }
        if (!next.renameTo(current)) {
            current.deleteRecursively()
            if (previous.exists()) previous.renameTo(current)
            error("Unable to activate repository version")
        }
    }

    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles().orEmpty().forEach { child -> copyDirectory(child, File(destination, child.name)) }
        } else {
            destination.parentFile?.mkdirs()
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        }
    }

    private fun parseRepo(url: String): Pair<String, String> {
        val path = url.removePrefix("https://github.com/").removeSuffix("/").removeSuffix(".git")
        val parts = path.split('/').filter { it.isNotBlank() }
        require(parts.size == 2) { "Invalid GitHub repository URL" }
        return parts[0] to parts[1]
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_ARCHIVE_BYTES = 80L * 1024L * 1024L
        const val MAX_UNPACKED_BYTES = 300L * 1024L * 1024L
        const val MAX_FILES = 10_000
    }
}
