package com.dps.evenup.domain.sharing.impl

import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGenerateGuestPasscodeUseCaseTest {
    @Test
    fun `generate returns four uppercase letters`() {
        val useCase = DefaultGenerateGuestPasscodeUseCase()

        repeat(100) {
            assertTrue(useCase.generate().matches(Regex("[A-Z]{4}")))
        }
    }
}
