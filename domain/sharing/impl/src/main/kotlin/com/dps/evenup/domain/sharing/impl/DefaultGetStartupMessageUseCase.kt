package com.dps.evenup.domain.sharing.impl

import com.dps.evenup.domain.sharing.api.GetStartupMessageUseCase

class DefaultGetStartupMessageUseCase : GetStartupMessageUseCase {
    override fun invoke(): String = "EvenUp"
}
