package com.dps.evenup.domain.account.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameTest {
    @Test fun normalizesCaseAndWhitespace() {
        assertEquals("alex_42", Username.parse("  Alex_42  ")?.value)
    }

    @Test fun rejectsInvalidUsernames() {
        assertNull(Username.parse("ab"))
        assertNull(Username.parse("2alex"))
        assertNull(Username.parse("alex__42"))
        assertNull(Username.parse("alex-42"))
    }
}
