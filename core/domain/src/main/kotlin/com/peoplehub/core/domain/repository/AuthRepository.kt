package com.peoplehub.core.domain.repository

import com.peoplehub.core.domain.model.Account
import kotlinx.coroutines.flow.Flow

/** The Firebase session every read and write goes through. */
interface AuthRepository {
    /** The signed-in account, `null` while signed out; re-emitted on every sign-in and sign-out. */
    val account: Flow<Account?>

    /** The account signed in right now, without waiting. */
    fun currentAccount(): Account?

    /** Signs in to Firebase with a Google ID token obtained on the phone. */
    suspend fun signInWithGoogle(idToken: String): Result<Unit>

    /** Signs out. Nothing stays on the phone apart from photos and device preferences. */
    suspend fun signOut()
}
