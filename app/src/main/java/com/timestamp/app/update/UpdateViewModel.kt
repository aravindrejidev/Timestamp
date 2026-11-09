package com.timestamp.app.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UpdateRepository(app)
    private val prefs = app.getSharedPreferences("timestamp_updates", Context.MODE_PRIVATE)

    val installedVersionName: String get() = repo.installedVersionName
    val installedVersionCode: Long get() = repo.installedVersionCode

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _dialog = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialog.asStateFlow()

    private var job: Job? = null
    private var checkedOnStart = false

    // ------------------------------------------------------------ check

    /** Called once when the app opens. Shows the popup by itself if a new release exists. */
    fun checkOnStart() {
        if (checkedOnStart) return
        checkedOnStart = true
        check(manual = false)
    }

    /** "Check for updates" button in Settings. */
    fun checkManual() = check(manual = true)

    private fun check(manual: Boolean) {
        val s = _state.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        if (!repo.isConfigured) {
            _state.value = if (manual) {
                UpdateState.CheckFailed("Updates only work in builds made by GitHub Actions.")
            } else {
                UpdateState.Idle
            }
            return
        }
        if (s is UpdateState.Ready || s is UpdateState.NeedsPermission) {
            if (manual) _dialog.value = true
            return
        }
        _state.value = UpdateState.Checking
        job = viewModelScope.launch {
            try {
                val info = repo.fetchLatest()
                if (info == null) {
                    _state.value = UpdateState.UpToDate
                    repo.cleanup()
                } else {
                    _state.value = UpdateState.Available(info)
                    val skipped = prefs.getString("skipped_version", null)
                    if (manual || skipped != info.versionName) _dialog.value = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoReleaseException) {
                _state.value = UpdateState.UpToDate
            } catch (e: Exception) {
                _state.value = if (manual) {
                    UpdateState.CheckFailed("Could not check for updates. Check your internet connection.")
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    // ------------------------------------------------------------ popup actions

    fun showDialog() {
        _dialog.value = true
    }

    /** "Later": hide the popup, ask again next time the app opens. */
    fun dismiss() {
        _dialog.value = false
    }

    /** "Skip this version": no automatic popup for this version any more. */
    fun skipVersion() {
        _state.value.infoOrNull?.let { prefs.edit().putString("skipped_version", it.versionName).apply() }
        _dialog.value = false
    }

    fun startDownload() {
        val info = _state.value.infoOrNull ?: return
        job?.cancel()
        _state.value = UpdateState.Downloading(info, 0f, 0L, info.sizeBytes)
        job = viewModelScope.launch {
            try {
                val file = repo.download(info) { done, total ->
                    val p = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _state.value = UpdateState.Downloading(info, p, done, total)
                }
                installFile(info, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.DownloadFailed(info, e.message ?: "Download failed.")
            }
        }
    }

    fun cancelDownload() {
        val info = _state.value.infoOrNull ?: return
        job?.cancel()
        _state.value = UpdateState.Available(info)
    }

    /** "Install" button (after the system installer was closed or permission was granted). */
    fun install() {
        when (val s = _state.value) {
            is UpdateState.Ready -> installFile(s.info, s.file)
            is UpdateState.NeedsPermission -> installFile(s.info, s.file)
            else -> Unit
        }
    }

    fun openPermissionSettings() {
        repo.openInstallSettings()
    }

    /** Called every time the app comes back to the foreground (e.g. after the permission screen). */
    fun onResume() {
        val s = _state.value
        if (s is UpdateState.NeedsPermission && repo.canInstall()) installFile(s.info, s.file)
    }

    private fun installFile(info: UpdateInfo, file: File) {
        val problem = repo.validate(file)
        if (problem != null) {
            _state.value = UpdateState.DownloadFailed(info, problem)
            return
        }
        if (!repo.canInstall()) {
            _state.value = UpdateState.NeedsPermission(info, file)
            return
        }
        _state.value = UpdateState.Ready(info, file)
        try {
            repo.launchInstaller(file)
        } catch (e: Exception) {
            _state.value = UpdateState.DownloadFailed(info, "Could not open the installer.")
        }
    }
}
