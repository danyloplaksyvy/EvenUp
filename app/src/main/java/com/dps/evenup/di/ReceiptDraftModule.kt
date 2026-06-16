package com.dps.evenup.di

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.core.datastore.impl.PreferencesStringDataStore
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.expense.impl.DataStoreExpenseDraftRepository
import com.dps.evenup.data.expense.impl.WorkerExpenseRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.data.participant.impl.DataStoreSavedParticipantRepository
import com.dps.evenup.data.sharing.api.ShareLinkResponseMapper
import com.dps.evenup.data.sharing.impl.DefaultShareLinkResponseMapper
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.WorkerApiConfig
import com.dps.evenup.core.network.impl.DefaultWorkerApiClient
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.expense.impl.DefaultAllocateFeesUseCase
import com.dps.evenup.domain.expense.impl.DefaultCalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.impl.DefaultValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.expense.impl.DefaultValidateItemAssignmentsUseCase
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import com.dps.evenup.domain.participant.impl.DefaultValidateParticipantsUseCase
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
    @Singleton
    fun provideWorkerApiConfig(): WorkerApiConfig = WorkerApiConfig("http://10.0.2.2:8787")

    @Provides
    @Singleton
    fun provideWorkerApiClient(
        config: WorkerApiConfig,
    ): WorkerApiClient = DefaultWorkerApiClient(config)

    @Provides
    fun provideShareLinkResponseMapper(): ShareLinkResponseMapper = DefaultShareLinkResponseMapper()

    @Provides
    @Singleton
    fun provideExpenseRepository(
        workerApiClient: WorkerApiClient,
        shareLinkResponseMapper: ShareLinkResponseMapper,
        draftRepository: ExpenseDraftRepository,
    ): ExpenseRepository = WorkerExpenseRepository(
        workerApiClient = workerApiClient,
        shareLinkResponseMapper = shareLinkResponseMapper,
        draftRepository = draftRepository,
    )

    @Provides
    @Singleton
    fun provideSavedParticipantRepository(
        stringDataStore: StringDataStore,
    ): SavedParticipantRepository = DataStoreSavedParticipantRepository(stringDataStore)

    @Provides
    fun provideValidateParticipantsUseCase(): ValidateParticipantsUseCase = DefaultValidateParticipantsUseCase()

    @Provides
    fun provideValidateItemAssignmentsUseCase(): ValidateItemAssignmentsUseCase = DefaultValidateItemAssignmentsUseCase()

    @Provides
    fun provideAllocateFeesUseCase(): AllocateFeesUseCase = DefaultAllocateFeesUseCase()

    @Provides
    fun provideCalculateExpenseSummaryUseCase(): CalculateExpenseSummaryUseCase = DefaultCalculateExpenseSummaryUseCase()

    @Provides
    fun provideValidateExpenseBeforeSaveUseCase(
        validateReceipt: ValidateReceiptUseCase,
        validateParticipants: ValidateParticipantsUseCase,
    ): ValidateExpenseBeforeSaveUseCase = DefaultValidateExpenseBeforeSaveUseCase(
        validateReceipt = validateReceipt,
        validateParticipants = validateParticipants,
        validateItemAssignments = DefaultValidateItemAssignmentsUseCase(),
        allocateFees = DefaultAllocateFeesUseCase(),
        calculateSummary = DefaultCalculateExpenseSummaryUseCase(),
    )

    @Provides
    fun provideValidateReceiptUseCase(): ValidateReceiptUseCase = DefaultValidateReceiptUseCase()
}
