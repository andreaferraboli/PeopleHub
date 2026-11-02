package com.peoplehub.core.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.peoplehub.core.domain.model.Account
import com.peoplehub.core.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The Firebase Auth session: who is signed in, sign in with a Google token, sign out. */
@Singleton
internal class CloudAuth
    @Inject
    constructor(
        private val auth: FirebaseAuth,
    ) : AuthRepository {
        override val account: Flow<Account?> =
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.distinctUntilChanged()

        /** The signed-in uid, `null` while signed out. */
        val uid: Flow<String?> = account.map { it?.uid }.distinctUntilChanged()

        fun currentUid(): String? = auth.currentUser?.uid

        override fun currentAccount(): Account? = auth.currentUser?.toAccount()

        override suspend fun signInWithGoogle(idToken: String): Result<Unit> =
            runCatching { auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }
                .map { }
                .onFailure { if (it is CancellationException) throw it }

        override suspend fun signOut() = auth.signOut()

        private fun FirebaseUser.toAccount() = Account(uid = uid, email = email, displayName = displayName)
    }
