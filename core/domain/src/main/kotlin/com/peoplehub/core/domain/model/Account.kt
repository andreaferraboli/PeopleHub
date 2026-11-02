package com.peoplehub.core.domain.model

/**
 * The Google account signed in to Firebase. Every piece of user data lives in the cloud under this
 * account, so without one the app shows only the sign-in screen.
 */
data class Account(
    val uid: String,
    val email: String?,
    val displayName: String?,
)
