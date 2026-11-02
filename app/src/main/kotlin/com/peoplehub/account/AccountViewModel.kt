package com.peoplehub.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peoplehub.core.domain.model.Account
import com.peoplehub.core.domain.repository.AuthRepository
import com.peoplehub.core.domain.usecase.ObserveAccountUseCase
import com.peoplehub.core.domain.usecase.SignInWithGoogleUseCase
import com.peoplehub.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether someone is signed in: the app shows its data only to a signed-in account. */
sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(val account: Account) : AccountState
}

/** The sign-in gate and the Vault's account card. */
@HiltViewModel
class AccountViewModel
    @Inject
    constructor(
        observeAccount: ObserveAccountUseCase,
        auth: AuthRepository,
        private val signInWithGoogle: SignInWithGoogleUseCase,
        private val signOutUseCase: SignOutUseCase,
    ) : ViewModel() {
        val state: StateFlow<AccountState> =
            observeAccount()
                .map { it.toState() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    // The session is known synchronously, so there is no "loading" flash on launch.
                    initialValue = auth.currentAccount().toState(),
                )

        private val _signingIn = MutableStateFlow(false)

        /** Whether a sign-in is in progress (the button shows it and does not accept a second tap). */
        val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

        private val failures = Channel<String>(Channel.BUFFERED)

        /** Sign-in failures, each delivered once, with the underlying message. */
        val signInFailures: Flow<String> = failures.receiveAsFlow()

        /** Marks the start of the Google account picker. */
        fun onSignInStarted() {
            _signingIn.value = true
        }

        /** The picker failed or was dismissed. */
        fun onSignInFailed(message: String) {
            _signingIn.value = false
            failures.trySend(message)
        }

        /** Finishes the Google sign-in with the ID token the screen obtained. */
        fun onIdToken(idToken: String) {
            viewModelScope.launch {
                signInWithGoogle(idToken).onFailure { failures.send(it.message ?: it.javaClass.simpleName) }
                _signingIn.value = false
            }
        }

        fun signOut() {
            viewModelScope.launch { signOutUseCase() }
        }

        private fun Account?.toState(): AccountState = this?.let { AccountState.SignedIn(it) } ?: AccountState.SignedOut

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
