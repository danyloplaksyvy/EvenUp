package com.dps.evenup.domain.account.api

interface IdentitySession {
    fun hasIdentity(): Boolean

    suspend fun signOut()
}

interface ResolveAccountSessionUseCase {
    suspend fun resolve(): AccountSessionState
}

interface BootstrapAccountUseCase {
    suspend fun bootstrap(): AccountSessionState
}

interface RequireAuthenticatedAccountUseCase {
    suspend fun require(action: PendingAuthAction): ProtectedActionDecision
}

interface ResumePendingAuthActionUseCase {
    suspend fun claim(actionId: String): PendingAuthAction?

    suspend fun complete(actionId: String)

    suspend fun markRetryRequired(actionId: String)
}

interface SignOutUseCase {
    suspend fun signOut()
}

interface UpdateProfileUseCase {
    suspend fun update(command: UpdateProfileCommand): Profile
}

data class UpdateProfileCommand(
    val displayName: String,
    val username: String,
    val defaultCurrency: String,
    val locale: String,
    val expectedVersion: ProfileVersion,
)

interface RequestAccountDeletionUseCase {
    suspend fun request(confirmation: String): AccountSessionState
}

interface CancelAccountDeletionUseCase {
    suspend fun cancel(): AccountSessionState
}
