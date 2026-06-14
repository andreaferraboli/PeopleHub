package com.peoplehub.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the self-update flow. */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(val update: AvailableUpdate) : UpdateUiState

    data object Downloading : UpdateUiState

    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        private val checker: UpdateChecker,
        private val installer: ApkInstaller,
    ) : ViewModel() {
        private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
        val state: StateFlow<UpdateUiState> = _state.asStateFlow()

        /** The version currently installed, for display. */
        val currentVersion: String = BuildConfig.VERSION_NAME

        /**
         * Checks GitHub for a newer release. When [silent] (the automatic launch check), failures and
         * "up to date" leave the state Idle so nothing is shown; a manual check surfaces both.
         */
        fun check(silent: Boolean) {
            viewModelScope.launch {
                if (!silent) _state.value = UpdateUiState.Checking
                runCatching { checker.fetchLatest(BuildConfig.UPDATE_OWNER, BuildConfig.UPDATE_REPO) }.fold(
                    onSuccess = { latest ->
                        _state.value =
                            when {
                                latest != null && latest.versionCode > BuildConfig.VERSION_CODE -> UpdateUiState.Available(latest)
                                silent -> UpdateUiState.Idle
                                else -> UpdateUiState.UpToDate
                            }
                    },
                    onFailure = {
                        _state.value = if (silent) UpdateUiState.Idle else UpdateUiState.Error(it.message ?: "Couldn't check for updates")
                    },
                )
            }
        }

        /** Downloads and installs [update], first sending the user to grant install permission if needed. */
        fun downloadAndInstall(update: AvailableUpdate) {
            if (!installer.canInstall()) {
                installer.openInstallPermissionSettings()
                return
            }
            viewModelScope.launch {
                _state.value = UpdateUiState.Downloading
                runCatching { installer.download(update.apkUrl) }.fold(
                    onSuccess = { file ->
                        installer.launchInstall(file)
                        _state.value = UpdateUiState.Idle
                    },
                    onFailure = { _state.value = UpdateUiState.Error(it.message ?: "Download failed") },
                )
            }
        }

        fun dismiss() {
            _state.value = UpdateUiState.Idle
        }
    }
