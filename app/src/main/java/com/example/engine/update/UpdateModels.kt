package com.example.engine.update

enum class UpdateSource {
    GOOGLE_PLAY,
    HTTPS_SERVER
}

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    VERIFYING_HASH,
    DOWNLOAD_COMPLETED,
    INSTALLING,
    FAILED
}

data class RemoteUpdatePayload(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String = "",
    val fileSize: Long = 0L,
    val forceUpdate: Boolean = false,
    val releaseDate: String = "",
    val releaseNotes: List<String> = emptyList(),
    val source: UpdateSource = UpdateSource.HTTPS_SERVER
)

data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val percentage: Int = 0,
    val errorMessage: String? = null
)

sealed class UpdateDialogState {
    object Hidden : UpdateDialogState()
    data class Visible(
        val updatePayload: RemoteUpdatePayload,
        val currentVersionCode: Long,
        val currentVersionName: String,
        val progress: DownloadProgress = DownloadProgress()
    ) : UpdateDialogState()
}
