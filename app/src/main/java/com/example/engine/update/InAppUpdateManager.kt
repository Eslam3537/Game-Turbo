package com.example.engine.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.engine.overlay.FloatingMonitorService
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
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
 * In-App Update Manager.
 *
 * Supports dual update mechanisms:
 * 1. Google Play In-App Updates (Flexible & Immediate) via Play Core API.
 * 2. Standalone HTTPS Update Server (for GitHub Releases or custom APK distribution).
 *
 * Features:
 * - VersionCode numeric comparison.
 * - Throttled battery-friendly background checks.
 * - Suppressed during active gameplay or active floating HUD.
 * - SHA-256 checksum verification before installation.
 * - Resumable/temp file caching.
 * - Secure FileProvider installation dispatch.
 */
object InAppUpdateManager {
    private const val TAG = "InAppUpdateManager"
    private const val PREFS_NAME = "in_app_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_timestamp"
    private const val KEY_SNOOZE_UNTIL = "snooze_until_timestamp"
    private const val MIN_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours
    private const val SNOOZE_DURATION_MS = 24 * 60 * 60 * 1000L   // 24 hours

    // Remote HTTPS update metadata endpoint
    private const val HTTPS_UPDATE_CONFIG_URL =
        "https://raw.githubusercontent.com/Eslam3537/Game-Turbo/main/app-update.json"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _dialogState = MutableStateFlow<UpdateDialogState>(UpdateDialogState.Hidden)
    val dialogState: StateFlow<UpdateDialogState> = _dialogState.asStateFlow()

    private var playAppUpdateManager: AppUpdateManager? = null
    private var playInstallListener: InstallStateUpdatedListener? = null
    private var downloadJob: Job? = null
    private var downloadedApkFile: File? = null

    /**
     * Initializes and executes update check.
     * @param isUserInitiated True if manually pressed in Settings (bypasses cooldown and snooze).
     */
    fun checkForUpdates(context: Context, isUserInitiated: Boolean = false) {
        // Do not check if floating monitor or active gameplay is ongoing (unless user explicitly initiated)
        if (!isUserInitiated && FloatingMonitorService.isOverlayRunning.value) {
            Log.d(TAG, "Update check suppressed: Floating monitor is actively running")
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        if (!isUserInitiated) {
            val snoozeUntil = prefs.getLong(KEY_SNOOZE_UNTIL, 0L)
            if (now < snoozeUntil) {
                Log.d(TAG, "Update check snoozed by user until: $snoozeUntil")
                return
            }

            val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
            if (now - lastCheck < MIN_CHECK_INTERVAL_MS) {
                Log.d(TAG, "Update check throttled. Last checked ${ (now - lastCheck) / 60000 } mins ago")
                return
            }
        }

        prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()

        scope.launch {
            val currentPkgInfo = getCurrentPackageInfo(context)
            val currentCode = getVersionCode(currentPkgInfo)
            val currentName = currentPkgInfo?.versionName ?: "1.0.0"

            // 1. Try Google Play In-App Updates first
            val playHandled = checkGooglePlayUpdate(context, currentCode, currentName)
            if (playHandled) return@launch

            // 2. Fallback to HTTPS update server
            checkHttpsServerUpdate(context, currentCode, currentName)
        }
    }

    private suspend fun checkGooglePlayUpdate(
        context: Context,
        currentCode: Long,
        currentName: String
    ): Boolean = withContext(Dispatchers.Main) {
        try {
            val updateManager = AppUpdateManagerFactory.create(context)
            playAppUpdateManager = updateManager
            val appUpdateInfoTask = updateManager.appUpdateInfo

            val info: AppUpdateInfo = suspendCancellableCoroutine { continuation ->
                appUpdateInfoTask.addOnSuccessListener { continuation.resumeWith(Result.success(it)) }
                appUpdateInfoTask.addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
            }

            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                val remoteCode = info.availableVersionCode().toLong()
                if (remoteCode > currentCode) {
                    val isForce = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) &&
                            !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)

                    val payload = RemoteUpdatePayload(
                        versionCode = remoteCode,
                        versionName = "$remoteCode.0",
                        apkUrl = "",
                        fileSize = info.totalBytesToDownload(),
                        forceUpdate = isForce,
                        releaseNotes = listOf("تحديث رسمي متاح عبر متجر Google Play"),
                        source = UpdateSource.GOOGLE_PLAY
                    )

                    _dialogState.value = UpdateDialogState.Visible(
                        updatePayload = payload,
                        currentVersionCode = currentCode,
                        currentVersionName = currentName
                    )
                    return@withContext true
                }
            }
            false
        } catch (e: Throwable) {
            Log.d(TAG, "Google Play Update check skipped or failed: ${e.message}")
            false
        }
    }

    private suspend fun checkHttpsServerUpdate(
        context: Context,
        currentCode: Long,
        currentName: String
    ) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(HTTPS_UPDATE_CONFIG_URL)
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTPS config check failed with code ${response.code}")
                return@withContext
            }

            val body = response.body?.string() ?: return@withContext
            val json = JSONObject(body)

            val remoteCode = json.optLong("versionCode", 0L)
            val remoteName = json.optString("versionName", "")
            val apkUrl = json.optString("apkUrl", "")
            val apkSha256 = json.optString("apkSha256", "")
            val fileSize = json.optLong("fileSize", 0L)
            val forceUpdate = json.optBoolean("forceUpdate", false)
            val releaseDate = json.optString("releaseDate", "")

            val notesArray = json.optJSONArray("releaseNotes")
            val releaseNotes = mutableListOf<String>()
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    releaseNotes.add(notesArray.getString(i))
                }
            }

            if (remoteCode > currentCode && apkUrl.startsWith("https://", ignoreCase = true)) {
                val payload = RemoteUpdatePayload(
                    versionCode = remoteCode,
                    versionName = remoteName,
                    apkUrl = apkUrl,
                    apkSha256 = apkSha256,
                    fileSize = fileSize,
                    forceUpdate = forceUpdate,
                    releaseDate = releaseDate,
                    releaseNotes = releaseNotes,
                    source = UpdateSource.HTTPS_SERVER
                )

                _dialogState.value = UpdateDialogState.Visible(
                    updatePayload = payload,
                    currentVersionCode = currentCode,
                    currentVersionName = currentName
                )
            } else {
                Log.d(TAG, "App is up to date: local=$currentCode, remote=$remoteCode")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "checkHttpsServerUpdate failed: ${e.message}")
        }
    }

    /**
     * Initiates the update flow (Google Play or HTTPS APK download).
     */
    fun startUpdate(activity: Activity) {
        val currentState = _dialogState.value as? UpdateDialogState.Visible ?: return
        val payload = currentState.updatePayload

        if (payload.source == UpdateSource.GOOGLE_PLAY && playAppUpdateManager != null) {
            startPlayUpdateFlow(activity, payload)
        } else {
            startHttpsDownload(activity.applicationContext, payload)
        }
    }

    private fun startPlayUpdateFlow(activity: Activity, payload: RemoteUpdatePayload) {
        try {
            val updateType = if (payload.forceUpdate) AppUpdateType.IMMEDIATE else AppUpdateType.FLEXIBLE
            playInstallListener = InstallStateUpdatedListener { state ->
                when (state.installStatus()) {
                    InstallStatus.DOWNLOADING -> {
                        val progress = DownloadProgress(
                            status = DownloadStatus.DOWNLOADING,
                            bytesDownloaded = state.bytesDownloaded(),
                            totalBytes = state.totalBytesToDownload(),
                            percentage = if (state.totalBytesToDownload() > 0) {
                                ((state.bytesDownloaded() * 100) / state.totalBytesToDownload()).toInt()
                            } else 0
                        )
                        updateProgress(progress)
                    }
                    InstallStatus.DOWNLOADED -> {
                        updateProgress(DownloadProgress(status = DownloadStatus.DOWNLOAD_COMPLETED, percentage = 100))
                    }
                    InstallStatus.FAILED -> {
                        updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "فشل تحميل التحديث من Google Play"))
                    }
                    else -> {}
                }
            }
            playAppUpdateManager?.registerListener(playInstallListener!!)
            playAppUpdateManager?.appUpdateInfo?.addOnSuccessListener { info ->
                playAppUpdateManager?.startUpdateFlowForResult(info, updateType, activity, 9901)
            }
        } catch (e: Throwable) {
            updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = e.message))
        }
    }

    private fun startHttpsDownload(context: Context, payload: RemoteUpdatePayload) {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            updateProgress(DownloadProgress(status = DownloadStatus.CONNECTING))
            try {
                val request = Request.Builder().url(payload.apkUrl).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "فشل الاتصال بخادم التحديث (${response.code})"))
                    return@launch
                }

                val responseBody = response.body ?: throw IllegalStateException("Empty download stream")
                val totalBytes = if (payload.fileSize > 0) payload.fileSize else responseBody.contentLength()

                val updatesDir = File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val targetFile = File(updatesDir, "update_v${payload.versionCode}.apk")
                val tempFile = File(updatesDir, "update_v${payload.versionCode}.apk.tmp")

                var bytesCopied = 0L
                val buffer = ByteArray(16 * 1024)
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

                // Verify SHA-256 if present
                if (payload.apkSha256.isNotBlank()) {
                    updateProgress(DownloadProgress(status = DownloadStatus.VERIFYING_HASH, percentage = 100))
                    val computedHash = computeSha256(tempFile)
                    if (!computedHash.equals(payload.apkSha256.trim(), ignoreCase = true)) {
                        tempFile.delete()
                        updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "فشل التحقق من أمان ملف APK (SHA-256 Mismatch)"))
                        return@launch
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
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
                Log.e(TAG, "Download failed: ${e.message}")
                updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = e.localizedMessage ?: "حدث خطأ أثناء تحميل التحديث"))
            }
        }
    }

    /**
     * Completes installation.
     */
    fun installDownloadedUpdate(context: Context) {
        val currentState = _dialogState.value as? UpdateDialogState.Visible ?: return

        // Google Play Complete
        if (currentState.updatePayload.source == UpdateSource.GOOGLE_PLAY) {
            playAppUpdateManager?.completeUpdate()
            return
        }

        // HTTPS Standalone APK Installation via FileProvider
        val apkFile = downloadedApkFile ?: return
        if (!apkFile.exists()) {
            updateProgress(DownloadProgress(status = DownloadStatus.FAILED, errorMessage = "ملف التحديث غير موجود"))
            return
        }

        // Check Unknown Sources permission on Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
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
            Log.e(TAG, "Failed to launch installer: ${e.message}")
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
}
