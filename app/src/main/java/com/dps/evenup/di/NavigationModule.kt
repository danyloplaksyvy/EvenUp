package com.dps.evenup.di

import androidx.navigation3.runtime.NavKey
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.core.navigation.api.EvenUpStartDestination
import com.dps.evenup.core.navigation.impl.DefaultEvenUpNavigator
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped

@Module
@InstallIn(ActivityRetainedComponent::class)
object NavigationModule {
    @Provides
    @EvenUpStartDestination
    fun provideStartDestination(): NavKey = NewExpenseDestination

    @Provides
    @ActivityRetainedScoped
    fun provideEvenUpNavigator(
        @EvenUpStartDestination startDestination: NavKey,
    ): EvenUpNavigator = DefaultEvenUpNavigator(startDestination)
}
