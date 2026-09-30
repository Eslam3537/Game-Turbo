package com.example.engine.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.engine.overlay.FloatingMonitorService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * IN-APP UPDATE MANAGER - GITHUB RELEASES ENGINE
 * ============================================================================
 *
 * Checks for authentic updates directly from GitHub Releases API:
 * https://api.github.com/repos/<GITHUB_OWNER>/<GITHUB_REPO>/releases/latest
 *
 * Versioning Logic:
 * - Semantic Versioning (major.minor.patch) comparison as numbers (Int).
 * - Tag is cleaned of 'v'/'V' prefix.
 * - Shows update ONLY if remoteVersion > localVersion.
 * - If remote <= local or on network/API errors, NO update dialog is shown.
 */
object InAppUpdateManager {
    private const val TAG = "InAppUpdateManager"

    // ========================================================================
    // 1. REPOSITORY CONSTANTS (عدّل هذين الثابتين بسهولة هنا)
    // ========================================================================
    const val GITHUB_OWNER = "Eslam3537"
    const val GITHUB_REPO = "Game-Turbo"

    private const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    // Throttling & Snoozing
    private const val PREFS_NAME = "in_app_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_timestamp"
    private const val MIN_CHECK_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours
    private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L   // 24 hours

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // OkHttpClient with 10-second connection timeout (no stale cache)
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _dialogState = MutableStateFlow<UpdateDialogState>(UpdateDialogState.Hidden)
    val dialogState: StateFlow<UpdateDialogState> = _dialogState.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    /**
     * Data structure for Semantic Versioning (major.minor.patch).
     */
    data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
        override fun compareTo(other: SemVer): Int {
            if (this.major != other.major) return this.major.compareTo(other.major)
            if (this.minor != other.minor) return this.minor.compareTo(other.minor)
            return this.patch.compareTo(other.patch)
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    /**
     * Parses a string into a Semantic Version (e.g. "v1.0.1", "V1.0", "1.2.3").
     * Returns null if string does not contain a valid major version number.
     */
    fun parseSemVer(versionStr: String): SemVer? {
        val clean = versionStr.trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()

        if (clean.isBlank()) return null

        val parts = clean.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return SemVer(major, minor, patch)
    }

    /**
     * Checks for updates from GitHub Releases API.
     * @param context Application or Activity context.
     * @param isUserInitiated True if clicked manually by user (Settings). Bypasses throttle and shows Toast.
     */
    fun checkForUpdates(context: Context, isUserInitiated: Boolean = false) {
        val appContext = context.applicationContext

        // Suppress automatic checks if floating monitor is active (to protect game performance)
        if (!isUserInitiated && FloatingMonitorService.isOverlayRunning.value) {
            Log.d(TAG, "Automatic update check suppressed: Floating monitor is running")
            return
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (!isUserInitiated) {
            val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
            if (now < snoozeUntil) {
                Log.d(TAG, "Update check snoozed until: $snoozeUntil")
                return
            }

            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
            if (now - lastCheck < MIN_CHECK_INTERVAL_MS) {
                Log.d(TAG, "Update check throttled. Checked recently.")
                return
            }
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        scope.launch {
            try {
                val currentVersionName = BuildConfig.VERSION_NAME
                val currentSemVer = parseSemVer(currentVersionName) ?: SemVer(1, 0, 0)

                val payload = fetchLatestReleaseFromGitHub(currentSemVer)

                if (payload != null) {
                    // Real new update detected!
                    _dialogState.value = UpdateDialogState.Visible(
                        updatePayload = payload,
                        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        currentVersionName = currentVersionName
                    )
                } else {
                    // No update or equal/older version
                    _dialogState.value = UpdateDialogState.Hidden
                    if (isUserInitiated) {
                        showToastOnMain(appContext, "التطبيق محدث إلى آخر إصدار (Game Turbo v$currentVersionName)")
                    }
                    Log.d(TAG, "App is up to date: local v$currentVersionName")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error checking GitHub release: ${e.message}", e)
                _dialogState.value = UpdateDialogState.Hidden
                if (isUserInitiated) {
                    showToastOnMain(appContext, "تعذر التحقق من التحديثات: يرجى التحقق من اتصال الإنترنت")
                }
            }
        }
    }

    /**
     * Fetches the latest release from GitHub API, extracts tag_name, cleans 'v'/'V',
     * and performs numeric Semantic Versioning comparison against currentSemVer.
     * Returns RemoteUpdatePayload ONLY if remoteSemVer > currentSemVer.
     */
    private fun fetchLatestReleaseFromGitHub(currentSemVer: SemVer): RemoteUpdatePayload? {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .header("User-Agent", "GameTurbo-Android-App")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()

            // Handle non-success responses (404 Not Found, 403 Rate Limited, 5xx Server Error)
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API call failed: HTTP ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                Log.w(TAG, "GitHub release has empty tag_name")
                return null
            }

            // Parse Semantic Versioning
            val remoteSemVer = parseSemVer(tagName)
            if (remoteSemVer == null) {
                Log.d(TAG, "Tag '$tagName' is not a valid semantic version (e.g. v1.0.1). Skipping.")
                return null
            }

            // Strict numeric comparison: ONLY show update if remote > current!
            if (remoteSemVer <= currentSemVer) {
                Log.d(TAG, "Remote version ($remoteSemVer) is <= Current version ($currentSemVer). No update needed.")
                return null
            }

            // Find APK in release assets
            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var fileSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        fileSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            // Fallback download URL if assets array did not contain named APK
            if (downloadUrl.isBlank()) {
                downloadUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tagName/app-debug.apk"
            }

            val releaseNotesRaw = json.optString("body", "").trim()
            val releaseDate = json.optString("published_at", "").take(10)

            val releaseNotes = if (releaseNotesRaw.isNotBlank()) {
                releaseNotesRaw.lines()
                    .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .take(6)
            } else {
                listOf("تحديث رسمي جديد متوفر لتطبيق Game Turbo")
            }

            return RemoteUpdatePayload(
                versionCode = (remoteSemVer.major * 10000 + remoteSemVer.minor * 100 + remoteSemVer.patch).toLong(),
                versionName = remoteSemVer.toString(),
                apkUrl = downloadUrl,
                fileSize = fileSize,
                forceUpdate = false,
                releaseDate = releaseDate,
                releaseNotes = releaseNotes,
                source = UpdateSource.HTTPS_SERVER
            )
        } catch (e: Throwable) {
            Log.e(TAG, "fetchLatestReleaseFromGitHub error: ${e.message}")
            return null
        }
    }

    /**
     * Starts downloading the update APK directly from GitHub.
     */
    fun startUpdate(activity: Activity) {
        val currentState = _dialogState.value as? UpdateDialogState.Visible ?: return
        val payload = currentState.updatePayload
        startDownload(activity.applicationContext, payload)
    }

    private fun startDownload(context: Context, payload: RemoteUpdatePayload) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            updateProgress(DownloadProgress(status = DownloadStatus.CONNECTING))
            try {
                // 1. Prepare updates cache folder and delete old updates
                val updatesDir = File(context.cacheDir, "updates")
                if (updatesDir.exists()) {
                    updatesDir.listFiles()?.forEach { oldFile ->
                        try { oldFile.delete() } catch (_: Throwable) {}
                    }
                } else {
                    updatesDir.mkdirs()
                }

                val targetFile = File(updatesDir, "GameTurbo_v${payload.versionName}.apk")
                val tempFile = File(updatesDir, "GameTurbo_v${payload.versionName}.apk.tmp")
                if (tempFile.exists()) tempFile.delete()

                // 2. Initiate OkHttp Request
                val request = Request.Builder()
                    .url(payload.apkUrl)
                    .header("User-Agent", "GameTurbo-Android-Downloader")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateProgress(
                        DownloadProgress(
                            status = DownloadStatus.FAILED,
                            errorMessage = "فشل الاتصال بخادم التحديث (${response.code})"
                        )
                    )
                    return@launch
                }

                val responseBody = response.body ?: throw IllegalStateException("Empty download stream")
                val totalBytes = if (payload.fileSize > 0) payload.fileSize else responseBody.contentLength()

                var bytesCopied = 0L
                val buffer = ByteArray(32 * 1024)
                val inputStream: InputStream = responseBody.byteStream()
                val outputStream = FileOutputStream(tempFile)

                var lastPercentUpdate = 0
                inputStream.use { input ->
                    outputStream.use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (!isActive) {
                                tempFile.delete()
                                return@launch
                            }
                            output.write(buffer, 0, read)
                            bytesCopied += read

                            val percent = if (totalBytes > 0) ((bytesCopied * 100) / totalBytes).toInt() else 0
                            if (percent > lastPercentUpdate) {
                                lastPercentUpdate = percent
                                updateProgress(
                                    DownloadProgress(
                                        status = DownloadStatus.DOWNLOADING,
                                        bytesDownloaded = bytesCopied,
                                        totalBytes = totalBytes,
                                        percentage = percent
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Verify complete download if asset size was known
                if (payload.fileSize > 0 && tempFile.length() != payload.fileSize) {
                    tempFile.delete()
                    updateProgress(
                        DownloadProgress(
                            status = DownloadStatus.FAILED,
                            errorMessage = "الملف غير مكتمل (انقطع الاتصال أثناء التحميل)"
                        )
                    )
                    return@launch
                }

                // 4. Move temp file to target file
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                downloadedApkFile = targetFile

                updateProgress(
                    DownloadProgress(
                        status = DownloadStatus.DOWNLOAD_COMPLETED,
                        bytesDownloaded = targetFile.length(),
                        totalBytes = targetFile.length(),
                        percentage = 100
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                updateProgress(
                    DownloadProgress(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "حدث انقطاع أثناء تحميل التحديث"
                    )
                )
            }
        }
    }

    /**
     * Launches Android PackageInstaller safely via FileProvider.
     * Guides user to Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES if permission is not granted.
     */
    fun installDownloadedUpdate(context: Context) {
        val apkFile = downloadedApkFile ?: return
        if (!apkFile.exists()) {
            updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "ملف التحديث غير موجود"))
            return
        }

        // Check Unknown Sources permission on Android 8+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                showToastOnMain(context, "يرجى السماح بتثبيت التطبيقات من هذا المصدر للمتابعة")
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Throwable) {
                    Log.e(TAG, "Cannot open unknown app sources settings: ${e.message}")
                }
                return
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch installer: ${e.message}", e)
            updateProgress(
                DownloadProgress(
                    status = DownloadStatus.FAILED,
                    errorMessage = "تعذر فتح مثبت التطبيقات: ${e.message}"
                )
            )
        }
    }

    fun snoozeUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_DURATION_MS).apply()
        dismissDialog()
    }

    fun dismissDialog() {
        downloadJob?.cancel()
        _dialogState.value = UpdateDialogState.Hidden
    }

    private fun updateProgress(progress: DownloadProgress) {
        val current = _dialogState.value
        if (current is UpdateDialogState.Visible) {
            _dialogState.value = current.copy(progress = progress)
        }
    }

    private fun showToastOnMain(context: Context, message: String) {
        mainHandler.post {
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {}
        }
    }
}
