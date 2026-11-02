package com.peoplehub.core.sync.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.peoplehub.core.domain.model.SyncAccount
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** The Firebase Auth session behind sync: who is signed in, sign in with a Google token, sign out. */
@Singleton
internal class CloudAuth
    @Inject
    constructor(
        private val auth: FirebaseAuth,
    ) {
        /** The signed-in account, re-emitted on every sign-in and sign-out. */
        val account: Flow<SyncAccount?> =
            callbackFlow {
                val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toAccount()) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }.distinctUntilChanged()

        fun current(): SyncAccount? = auth.currentUser?.toAccount()

        suspend fun signInWithGoogle(idToken: String) {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        }

        fun signOut() = auth.signOut()

        private fun FirebaseUser.toAccount() = SyncAccount(uid = uid, email = email, displayName = displayName)
    }
