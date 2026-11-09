package com.timestamp.app.update

import java.io.File

data class UpdateInfo(
    val versionName: String,
    /** -1 when the release has no update.json (then version names are compared). */
    val versionCode: Long,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val sha256: String?,
    val pageUrl: String
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class CheckFailed(val message: String) : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(
        val info: UpdateInfo,
        val progress: Float,
        val downloaded: Long,
        val total: Long
    ) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class DownloadFailed(val info: UpdateInfo, val message: String) : UpdateState
}

val UpdateState.infoOrNull: UpdateInfo?
    get() = when (this) {
        is UpdateState.Available -> info
        is UpdateState.Downloading -> info
        is UpdateState.Ready -> info
        is UpdateState.NeedsPermission -> info
        is UpdateState.DownloadFailed -> info
        else -> null
    }
