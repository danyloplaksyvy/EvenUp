package com.dps.evenup.domain.account.impl

import com.dps.evenup.data.account.api.AccountBootstrapCommand
import com.dps.evenup.data.account.api.AccountRepository
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.domain.account.api.Account
import com.dps.evenup.domain.account.api.AccountId
import com.dps.evenup.domain.account.api.AccountSessionState
import com.dps.evenup.domain.account.api.AccountState
import com.dps.evenup.domain.account.api.BootstrapStatus
import com.dps.evenup.domain.account.api.BootstrapAccountUseCase
import com.dps.evenup.domain.account.api.BootstrappedAccount
import com.dps.evenup.domain.account.api.IdentitySession
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.PendingAuthOrigin
import com.dps.evenup.domain.account.api.ProtectedActionDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUseCasesTest {
    @Test
    fun signedOutSessionDoesNotBootstrap() = runBlocking {
        var bootstrapCalls = 0
        val useCase = DefaultResolveAccountSessionUseCase(
            identitySession = FakeIdentitySession(false),
            accountRepository = FakeAccountRepository(),
            bootstrap = object : BootstrapAccountUseCase {
                override suspend fun bootstrap(): AccountSessionState {
                    bootstrapCalls += 1
                    return AccountSessionState.AuthenticatedBootstrapFailed(true)
                }
            },
        )

        assertEquals(AccountSessionState.SignedOut, useCase.resolve())
        assertEquals(0, bootstrapCalls)
    }

    @Test
    fun protectedActionIsPersistedBeforeAuthentication() = runBlocking {
        val pending = FakePendingRepository()
        val useCase = DefaultRequireAuthenticatedAccountUseCase(
            identitySession = FakeIdentitySession(false),
            accountRepository = FakeAccountRepository(),
            pendingActions = pending,
        )
        val action = PendingAuthAction(
            id = "action-1",
            type = PendingAuthActionType.ConfirmExpenseSave,
            origin = PendingAuthOrigin.ReviewExpense,
            reference = "draft-1",
            createdAtEpochMillis = 1,
            state = PendingActionState.Pending,
        )

        assertTrue(useCase.require(action) is ProtectedActionDecision.AuthenticationRequired)
        assertEquals(action.id, pending.current?.id)
    }

    @Test
    fun signOutLocksServerCacheAndPreservesUnrelatedLocalData() = runBlocking {
        val identity = FakeIdentitySession(true)
        val account = FakeAccountRepository()
        val pending = FakePendingRepository().apply {
            current = PendingAuthAction(
                "action", PendingAuthActionType.OpenReceiptScan, PendingAuthOrigin.NewExpense,
                "draft-stays-in-separate-store", 1, PendingActionState.Pending,
            )
        }
        DefaultSignOutUseCase(identity, account, pending).signOut()

        assertTrue(identity.signedOut)
        assertTrue(account.locked)
        assertEquals(null, pending.current)
    }
}

private class FakeIdentitySession(private val authenticated: Boolean) : IdentitySession {
    var signedOut = false
    override fun hasIdentity(): Boolean = authenticated && !signedOut
    override suspend fun signOut() { signedOut = true }
}

private class FakeAccountRepository : AccountRepository {
    var locked = false
    override suspend fun bootstrap(command: AccountBootstrapCommand): BootstrappedAccount =
        BootstrappedAccount(Account(AccountId("account"), AccountState.Active), null, emptySet(), BootstrapStatus.Ready)
    override suspend fun cachedAccount(): BootstrappedAccount? = null
    override suspend fun requestDeletion(confirmation: String): String? = null
    override suspend fun cancelDeletion(): BootstrappedAccount = bootstrap(
        AccountBootstrapCommand("en", "USD", "terms", "privacy"),
    )
    override suspend fun signOut() { locked = true }
}

private class FakePendingRepository : PendingAuthActionRepository {
    var current: PendingAuthAction? = null
    override fun observe(): Flow<PendingAuthAction?> = flowOf(current)
    override suspend fun get(): PendingAuthAction? = current
    override suspend fun replace(action: PendingAuthAction) { current = action }
    override suspend fun claim(actionId: String): PendingAuthAction? =
        current?.takeIf { it.id == actionId }?.copy(state = PendingActionState.Executing)?.also { current = it }
    override suspend fun update(action: PendingAuthAction) { current = action }
    override suspend fun clear() { current = null }
}
