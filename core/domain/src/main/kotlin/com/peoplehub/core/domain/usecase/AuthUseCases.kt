package com.peoplehub.core.domain.usecase

import com.peoplehub.core.domain.model.Account
import com.peoplehub.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the signed-in account (`null` while signed out). */
class ObserveAccountUseCase
    @Inject
    constructor(
        private val repository: AuthRepository,
    ) {
        operator fun invoke(): Flow<Account?> = repository.account
    }

/** Signs in to the cloud with a Google ID token. */
class SignInWithGoogleUseCase
    @Inject
    constructor(
        private val repository: AuthRepository,
    ) {
        suspend operator fun invoke(idToken: String): Result<Unit> = repository.signInWithGoogle(idToken)
    }

/** Signs out of the cloud. */
class SignOutUseCase
    @Inject
    constructor(
        private val repository: AuthRepository,
    ) {
        suspend operator fun invoke() = repository.signOut()
    }
