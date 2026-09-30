package com.example.engine.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
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
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * In-App Update Manager directly connected to GitHub Repository:
 * Eslam3537/Game-Turbo
 *
 * Checks for updates via:
 * 1. GitHub Releases API: https://api.github.com/repos/Eslam3537/Game-Turbo/releases/latest
 * 2. Raw Repository Manifest: https://raw.githubusercontent.com/Eslam3537/Game-Turbo/main/app-update.json
 *
 * Downloads APK directly from GitHub Releases and triggers safe PackageInstaller via FileProvider.
 */
object InAppUpdateManager {
    private const val TAG = "InAppUpdateManager"
    private const val PREFS_NAME = "in_app_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_timestamp"
    private const val MIN_CHECK_INTERVAL_MS = 3 * 60 * 60 * 1000L // 3 hours
    private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L   // 24 hours

    private const val GITHUB_REPO_OWNER = "Eslam3537"
    private const val GITHUB_REPO_NAME = "Game-Turbo"

    private const val GITHUB_API_LATEST_RELEASE =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    private const val GITHUB_RAW_UPDATE_JSON =
        "https://raw.githubusercontent.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/main/app-update.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val _dialogState = MutableStateFlow<UpdateDialogState>(UpdateDialogState.Hidden)
    val dialogState: StateFlow<UpdateDialogState> = _dialogState.asStateFlow()

    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    /**
     * Checks for updates from GitHub.
     * @param context Application or Activity context.
     * @param isUserInitiated True if clicked by user (in Settings). Bypasses cooldown and shows feedback toast.
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
                val currentPkgInfo = getCurrentPackageInfo(appContext)
                val currentCode = getVersionCode(currentPkgInfo)
                val currentName = currentPkgInfo?.versionName ?: "1.0.0"

                // 1. Try checking app-update.json from GitHub
                var payload = fetchFromRawManifest(currentName)

                // 2. If null, fallback to GitHub Releases API
                if (payload == null) {
                    payload = fetchFromGitHubReleasesApi(currentName)
                }

                val hasUpdate = payload != null && isUpdateAvailable(currentCode, currentName, payload)

                if (hasUpdate && payload != null) {
                    // Update available! Show Dialog
                    _dialogState.value = UpdateDialogState.Visible(
                        updatePayload = payload,
                        currentVersionCode = currentCode,
                        currentVersionName = currentName
                    )
                } else {
                    _dialogState.value = UpdateDialogState.Hidden
                    if (isUserInitiated) {
                        showToastOnMain(appContext, "التطبيق محدث إلى آخر إصدار (v$currentName)")
                    }
                    Log.d(TAG, "App is up to date: local=$currentCode, currentName=$currentName")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Update check error: ${e.message}", e)
                if (isUserInitiated) {
                    showToastOnMain(appContext, "تعذر الاتصال بمستودع GitHub، يرجى التحقق من الإنترنت")
                }
            }
        }
    }

    private fun isUpdateAvailable(currentCode: Long, currentName: String, payload: RemoteUpdatePayload): Boolean {
        val cleanCurrent = currentName.trim().removePrefix("v").removePrefix("V")
        val cleanRemote = payload.versionName.trim().removePrefix("v").removePrefix("V")

        // If the version names match exactly (e.g. 1.0.1 == 1.0.1), no update is needed
        if (cleanCurrent.isNotBlank() && cleanCurrent.equals(cleanRemote, ignoreCase = true)) {
            return false
        }

        // Only update if remote versionCode is strictly higher than current
        return payload.versionCode > currentCode
    }

    private fun fetchFromRawManifest(currentVersionName: String): RemoteUpdatePayload? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_RAW_UPDATE_JSON)
                .header("User-Agent", "GameTurbo-Android/$currentVersionName")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val remoteCode = json.optLong("versionCode", 0L)
            val remoteName = json.optString("versionName", "1.0.1")
            val apkUrl = json.optString("apkUrl", "")
            val apkSha256 = json.optString("apkSha256", "")
            val fileSize = json.optLong("fileSize", 0L)
            val forceUpdate = json.optBoolean("forceUpdate", false)
            val releaseDate = json.optString("releaseDate", "")

            val notesArray = json.optJSONArray("releaseNotes")
            val notes = mutableListOf<String>()
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    notes.add(notesArray.getString(i))
                }
            }

            if (remoteCode > 0 && apkUrl.startsWith("https://", ignoreCase = true)) {
                RemoteUpdatePayload(
                    versionCode = remoteCode,
                    versionName = remoteName,
                    apkUrl = apkUrl,
                    apkSha256 = apkSha256,
                    fileSize = fileSize,
                    forceUpdate = forceUpdate,
                    releaseDate = releaseDate,
                    releaseNotes = notes,
                    source = UpdateSource.HTTPS_SERVER
                )
            } else null
        } catch (e: Throwable) {
            Log.d(TAG, "fetchFromRawManifest skipped: ${e.message}")
            null
        }
    }

    private fun fetchFromGitHubReleasesApi(currentVersionName: String): RemoteUpdatePayload? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .header("User-Agent", "GameTurbo-Android/$currentVersionName")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseTitle = json.optString("name", "New Release")
            val releaseDate = json.optString("published_at", "").take(10)
            val releaseBody = json.optString("body", "")

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

            if (downloadUrl.isNotBlank()) {
                val notes = releaseBody.lines()
                    .map { it.trim().removePrefix("-").removePrefix("*").trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .take(5)

                // Derive version code if tag is e.g. "debug-apk-build-3-1" -> 3
                val derivedCode = extractVersionCodeFromTag(tagName)

                RemoteUpdatePayload(
                    versionCode = derivedCode,
                    versionName = releaseTitle,
                    apkUrl = downloadUrl,
                    fileSize = fileSize,
                    forceUpdate = false,
                    releaseDate = releaseDate,
                    releaseNotes = if (notes.isNotEmpty()) notes else listOf("تحديث جديد متوفر عبر GitHub Releases"),
                    source = UpdateSource.HTTPS_SERVER
                )
            } else null
        } catch (e: Throwable) {
            Log.d(TAG, "fetchFromGitHubReleasesApi failed: ${e.message}")
            null
        }
    }

    private fun extractVersionCodeFromTag(tag: String): Long {
        val buildMatch = Regex("build[_-](\\d+)").find(tag)
        if (buildMatch != null) {
            return buildMatch.groupValues[1].toLongOrNull() ?: 2L
        }
        val semverMatch = Regex("(\\d+)\\.(\\d+)\\.(\\d+)").find(tag)
        if (semverMatch != null) {
            val major = semverMatch.groupValues[1].toLongOrNull() ?: 1L
            val minor = semverMatch.groupValues[2].toLongOrNull() ?: 0L
            val patch = semverMatch.groupValues[3].toLongOrNull() ?: 0L
            return major * 10000 + minor * 100 + patch
        }
        return 2L
    }

    /**
     * Starts downloading the update APK directly from GitHub.
     */
    fun startUpdate(activity: Activity) {
        val currentState = _dialogState.value as? UpdateDialogState.Visible ?: return
        val payload = currentState.updatePayload
        startHttpsDownload(activity.applicationContext, payload)
    }

    private fun startHttpsDownload(context: Context, payload: RemoteUpdatePayload) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            updateProgress(DownloadProgress(status = DownloadStatus.CONNECTING))
            try {
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

                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val targetFile = File(updatesDir, "app-update-v${payload.versionCode}.apk")
                val tempFile = File(updatesDir, "app-update-v${payload.versionCode}.apk.tmp")

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

                // Verify SHA-256 if hash was specified
                if (payload.apkSha256.isNotBlank()) {
                    updateProgress(DownloadProgress(status = DownloadStatus.VERIFYING_HASH, percentage = 100))
                    val computedHash = computeSha256(tempFile)
                    if (!computedHash.equals(payload.apkSha256.trim(), ignoreCase = true)) {
                        tempFile.delete()
                        updateProgress(
                            DownloadProgress(
                                status = DownloadStatus.FAILED,
                                errorMessage = "فشل التحقق من أمان الملف (SHA-256 Mismatch)"
                            )
                        )
                        return@launch
                    }
                }

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
     */
    fun installDownloadedUpdate(context: Context) {
        val apkFile = downloadedApkFile ?: return
        if (!apkFile.exists()) {
            updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "ملف التحديث غير موجود"))
            return
        }

        // Check Unknown Sources permission on Android 8+
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
            updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "تعذر فتح مثبت التطبيقات: ${e.message}"))
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

    private fun computeSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentPackageInfo(context: Context): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getVersionCode(info: PackageInfo?): Long {
        if (info == null) return 1L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
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
