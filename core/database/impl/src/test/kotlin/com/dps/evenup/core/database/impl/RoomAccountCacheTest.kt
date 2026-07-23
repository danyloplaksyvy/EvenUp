package com.dps.evenup.core.database.impl

import com.dps.evenup.core.database.api.CachedProfileRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomAccountCacheTest {
    @Test
    fun accountSwitchClearsPreviousOwnersServerCacheBeforeActivation() = runBlocking {
        val dao = FakeAccountCacheDao()
        val cache = RoomAccountCache(dao)
        cache.write(profile("account-a"))
        cache.replaceActiveAccount("account-a")
        cache.write(profile("account-b"))

        cache.replaceActiveAccount("account-b")

        assertNull(cache.read("account-a"))
        assertEquals("account-b", cache.activeAccountId())
        assertEquals("account-b", cache.read("account-b")?.ownerAccountId)
    }
}

private fun profile(owner: String) = CachedProfileRecord(
    owner, "${owner}_user", "User", null, "USD", "en-US", 1, "",
)

private class FakeAccountCacheDao : AccountCacheDao {
    private val profiles = mutableMapOf<String, ProfileEntity>()
    private var session: AccountCacheSessionEntity? = null
    override fun observeProfile(ownerAccountId: String): Flow<ProfileEntity?> = flowOf(profiles[ownerAccountId])
    override suspend fun readProfile(ownerAccountId: String): ProfileEntity? = profiles[ownerAccountId]
    override suspend fun writeProfile(profile: ProfileEntity) { profiles[profile.ownerAccountId] = profile }
    override suspend fun deleteProfile(ownerAccountId: String) { profiles.remove(ownerAccountId) }
    override suspend fun readSession(): AccountCacheSessionEntity? = session
    override suspend fun writeSession(session: AccountCacheSessionEntity) { this.session = session }
}
