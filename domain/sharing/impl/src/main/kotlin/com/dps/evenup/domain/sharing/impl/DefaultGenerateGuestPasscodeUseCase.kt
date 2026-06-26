package com.dps.evenup.domain.sharing.impl

import com.dps.evenup.domain.sharing.api.GenerateGuestPasscodeUseCase
import java.security.SecureRandom

class DefaultGenerateGuestPasscodeUseCase(
    private val random: SecureRandom = SecureRandom(),
) : GenerateGuestPasscodeUseCase {
    override fun generate(): String {
        return buildString(capacity = PasscodeLength) {
            repeat(PasscodeLength) {
                append(('A'.code + random.nextInt(AlphabetSize)).toChar())
            }
        }
    }

    private companion object {
        const val PasscodeLength = 4
        const val AlphabetSize = 26
    }
}
