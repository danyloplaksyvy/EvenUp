package com.dps.evenup.di

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.core.datastore.impl.PreferencesStringDataStore
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.impl.DataStoreExpenseDraftRepository
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import com.dps.evenup.domain.receipt.impl.DefaultValidateReceiptUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.evenUpPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "evenup_preferences",
)

@Module
@InstallIn(SingletonComponent::class)
object ReceiptDraftModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.evenUpPreferencesDataStore

    @Provides
    @Singleton
    fun provideStringDataStore(
        preferencesDataStore: DataStore<Preferences>,
    ): StringDataStore = PreferencesStringDataStore(preferencesDataStore)

    @Provides
    @Singleton
    fun provideExpenseDraftRepository(
        stringDataStore: StringDataStore,
    ): ExpenseDraftRepository = DataStoreExpenseDraftRepository(stringDataStore)

    @Provides
    fun provideValidateReceiptUseCase(): ValidateReceiptUseCase = DefaultValidateReceiptUseCase()
}
