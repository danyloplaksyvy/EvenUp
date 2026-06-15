package com.dps.evenup.di

import com.dps.evenup.domain.sharing.api.GetStartupMessageUseCase
import com.dps.evenup.domain.sharing.impl.DefaultGetStartupMessageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object StartupModule {
    @Provides
    fun provideGetStartupMessageUseCase(): GetStartupMessageUseCase {
        return DefaultGetStartupMessageUseCase()
    }
}
