package com.peoplehub.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.SyncStatus
import com.peoplehub.core.domain.usecase.DownloadFromCloudUseCase
import com.peoplehub.core.domain.usecase.ObserveSyncStatusUseCase
import com.peoplehub.core.domain.usecase.RefreshCloudCheckUseCase
import com.peoplehub.core.domain.usecase.SignInToCloudUseCase
import com.peoplehub.core.domain.usecase.SignOutOfCloudUseCase
import com.peoplehub.core.domain.usecase.SyncNowUseCase
import com.peoplehub.core.domain.usecase.UploadToCloudUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One-off outcome of a sync action, shown as a snackbar. */
sealed interface SyncEvent {
    data object Done : SyncEvent

    data class Failed(val message: String) : SyncEvent
}

/** State and actions of the Vault's cloud sync card. */
@HiltViewModel
class SyncViewModel
    @Inject
    constructor(
        observeSyncStatus: ObserveSyncStatusUseCase,
        private val signInToCloud: SignInToCloudUseCase,
        private val signOutOfCloud: SignOutOfCloudUseCase,
        private val uploadToCloud: UploadToCloudUseCase,
        private val downloadFromCloud: DownloadFromCloudUseCase,
        private val syncNow: SyncNowUseCase,
        private val refreshCloudCheck: RefreshCloudCheckUseCase,
    ) : ViewModel() {
        val status: StateFlow<SyncStatus> =
            observeSyncStatus().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SyncStatus(),
            )

        private val eventChannel = Channel<SyncEvent>(Channel.BUFFERED)

        /** Outcomes of the actions below, each delivered once. */
        val events: Flow<SyncEvent> = eventChannel.receiveAsFlow()

        /** Finishes the Google sign-in with the ID token the screen obtained. */
        fun onIdToken(idToken: String) = launchReporting(reportSuccess = false) { signInToCloud(idToken) }

        fun upload() = launchReporting { uploadToCloud() }

        fun download() = launchReporting { downloadFromCloud() }

        fun syncNow() = launchReporting { syncNow.invoke() }

        fun retryCloudCheck() {
            viewModelScope.launch { refreshCloudCheck() }
        }

        fun signOut() {
            viewModelScope.launch { signOutOfCloud() }
        }

        private fun launchReporting(reportSuccess: Boolean = true, action: suspend () -> Result<Unit>) {
            viewModelScope.launch {
                action().fold(
                    onSuccess = { if (reportSuccess) eventChannel.send(SyncEvent.Done) },
                    onFailure = { eventChannel.send(SyncEvent.Failed(it.message ?: it.javaClass.simpleName)) },
                )
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
