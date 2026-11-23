package com.example.update

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val hasApkAsset: Boolean,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

object AppUpdateChecker {
    const val REPO_URL = "https://github.com/xialia-cheng/ripple-plan"
    const val RELEASES_PAGE_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/xialia-cheng/ripple-plan/releases/latest"
    const val AUTO_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /**
     * Normalizes a version string or GitHub tag such as "v2.1.2", "V2.2.0-beta.1"
     * into a clean display version without the leading 'v' prefix.
     */
    fun normalizeVersionDisplay(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Compares two semantic version strings (e.g. "v2.2.0" vs "2.1.2", "2.1.10" vs "2.1.2").
     * Returns > 0 if [first] > [second], < 0 if [first] < [second], and 0 if equal.
     */
    fun compareSemanticVersions(first: String, second: String): Int {
        val parsedFirst = parseSemanticVersion(first)
        val parsedSecond = parseSemanticVersion(second)
        val maxSegments = maxOf(parsedFirst.numbers.size, parsedSecond.numbers.size)
        for (i in 0 until maxSegments) {
            val a = parsedFirst.numbers.getOrElse(i) { 0 }
            val b = parsedSecond.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        val preA = parsedFirst.preRelease
        val preB = parsedSecond.preRelease
        if (preA == null && preB == null) return 0
        if (preA == null) return 1 // Stable release > pre-release with same core version
        if (preB == null) return -1
        return comparePreReleaseTags(preA, preB)
    }

    /**
     * Returns true strictly when [remoteTagOrVersion] represents a newer version
     * than [currentVersion].
     */
    fun isRemoteVersionNewer(
        remoteTagOrVersion: String,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): Boolean {
        val parsedRemote = parseSemanticVersion(remoteTagOrVersion)
        if (parsedRemote.numbers.all { it == 0 }) return false
        return compareSemanticVersions(remoteTagOrVersion, currentVersion) > 0
    }

    fun parseLatestReleaseJson(
        jsonText: String,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): UpdateCheckResult {
        return runCatching {
            val root = JSONObject(jsonText)
            val tagName = root.optString("tag_name").trim()
            if (tagName.isBlank()) {
                return UpdateCheckResult.Error("版本信息格式异常，请稍后重试")
            }

            val normalizedVersion = normalizeVersionDisplay(tagName)
            val htmlUrl = root.optString("html_url")
                .trim()
                .takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: RELEASES_PAGE_URL
            val releaseNotes = root.optString("body")
                .trim()
                .ifEmpty { "本次发布包含功能优化与稳定性改进，可前往发布页查看详情。" }

            var apkDownloadUrl: String? = null
            val assets = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name").trim()
                    val url = asset.optString("browser_download_url").trim()
                    if (name.endsWith(".apk", ignoreCase = true) &&
                        (url.startsWith("https://") || url.startsWith("http://"))
                    ) {
                        apkDownloadUrl = url
                        break
                    }
                }
            }

            if (isRemoteVersionNewer(tagName, currentVersion)) {
                UpdateCheckResult.UpdateAvailable(
                    ReleaseInfo(
                        tagName = tagName,
                        versionName = normalizedVersion,
                        releaseNotes = releaseNotes,
                        downloadUrl = apkDownloadUrl ?: htmlUrl,
                        releasePageUrl = htmlUrl,
                        hasApkAsset = apkDownloadUrl != null,
                    ),
                )
            } else {
                UpdateCheckResult.UpToDate(currentVersion = currentVersion)
            }
        }.getOrElse {
            UpdateCheckResult.Error("解析更新信息失败，请稍后重试")
        }
    }

    suspend fun fetchLatestRelease(
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RipplePlan-Android/$currentVersion")
            }

            val code = connection.responseCode
            val body = runCatching {
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            when {
                code in 200..299 -> parseLatestReleaseJson(body, currentVersion)
                code == 404 -> UpdateCheckResult.Error("仓库暂无公开发布版本，请稍后再试")
                code == 403 || code == 429 ->
                    UpdateCheckResult.Error("GitHub 接口请求过于频繁，请稍后再试")
                else -> UpdateCheckResult.Error("检查更新失败（HTTP $code），不影响本地正常使用")
            }
        } catch (_: SocketTimeoutException) {
            UpdateCheckResult.Error("连接 GitHub 超时，请检查网络后重试")
        } catch (_: UnknownHostException) {
            UpdateCheckResult.Error("当前无网络连接或无法访问 GitHub，不影响本地正常使用")
        } catch (_: IOException) {
            UpdateCheckResult.Error("网络请求失败，请检查网络连接后重试")
        } catch (_: Exception) {
            UpdateCheckResult.Error("检查更新失败，不影响本地正常使用")
        } finally {
            connection?.disconnect()
        }
    }

    private data class ParsedVersion(
        val numbers: List<Int>,
        val preRelease: String?,
    )

    private fun parseSemanticVersion(raw: String): ParsedVersion {
        val cleaned = normalizeVersionDisplay(raw).substringBefore('+').trim()
        if (cleaned.isEmpty()) return ParsedVersion(listOf(0), null)

        val dashIndex = cleaned.indexOf('-')
        val corePart = if (dashIndex >= 0) cleaned.substring(0, dashIndex) else cleaned
        val prePart = if (dashIndex >= 0) {
            cleaned.substring(dashIndex + 1).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val numbers = corePart
            .split('.')
            .map { segment ->
                val digits = segment.takeWhile { it.isDigit() }
                digits.toIntOrNull() ?: 0
            }
            .ifEmpty { listOf(0) }

        return ParsedVersion(numbers = numbers, preRelease = prePart)
    }

    private fun comparePreReleaseTags(first: String, second: String): Int {
        val partsA = first.split('.')
        val partsB = second.split('.')
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val a = partsA.getOrNull(i) ?: return -1
            val b = partsB.getOrNull(i) ?: return 1
            val numA = a.toIntOrNull()
            val numB = b.toIntOrNull()
            val cmp = when {
                numA != null && numB != null -> numA.compareTo(numB)
                numA != null -> -1
                numB != null -> 1
                else -> a.compareTo(b, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
