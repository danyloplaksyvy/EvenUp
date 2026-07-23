package com.dps.evenup.di

import android.content.Context
import android.content.ContentResolver
import com.dps.evenup.BuildConfig
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.DataStore
import com.dps.evenup.core.camera.api.ReceiptCaptureTargetFactory
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.camera.impl.DefaultReceiptCaptureTargetFactory
import com.dps.evenup.core.camera.impl.DefaultReceiptImageReader
import com.dps.evenup.core.datastore.api.StringDataStore
import com.dps.evenup.core.datastore.impl.PreferencesStringDataStore
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.expense.impl.DataStoreExpenseDraftRepository
import com.dps.evenup.data.expense.impl.WorkerExpenseRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.data.participant.impl.DataStoreSavedParticipantRepository
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.data.receipt.impl.WorkerReceiptRepository
import com.dps.evenup.data.sharing.api.ShareLinkResponseMapper
import com.dps.evenup.data.sharing.impl.DefaultShareLinkResponseMapper
import com.dps.evenup.core.network.api.WorkerApiClient
import com.dps.evenup.core.network.api.NetworkStatus
import com.dps.evenup.core.network.api.WorkerApiConfig
import com.dps.evenup.core.network.impl.DefaultWorkerApiClient
import com.dps.evenup.core.network.impl.DefaultNetworkStatus
import com.dps.evenup.core.speech.api.SpeechTranscriber
import com.dps.evenup.core.speech.impl.DefaultSpeechTranscriber
import com.dps.evenup.data.expenseinput.api.AiExpenseInterpreter
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.expenseinput.impl.DataStoreAiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.impl.DataStoreAiExpenseSessionRepository
import com.dps.evenup.data.expenseinput.impl.WorkerAiExpenseInterpreter
import com.dps.evenup.domain.expenseinput.api.ParticipantNameMatcher
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import com.dps.evenup.domain.expenseinput.impl.DefaultParticipantNameMatcher
import com.dps.evenup.domain.expenseinput.impl.DefaultPrepareAiExpenseUseCase
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
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import com.dps.evenup.domain.receipt.impl.DefaultNormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.impl.DefaultValidateReceiptUseCase
import com.dps.evenup.domain.sharing.api.GenerateGuestPasscodeUseCase
import com.dps.evenup.domain.sharing.impl.DefaultGenerateGuestPasscodeUseCase
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
    fun provideWorkerApiConfig(): WorkerApiConfig = WorkerApiConfig(BuildConfig.WORKER_BASE_URL)

    @Provides
    @Singleton
    fun provideWorkerApiClient(
        config: WorkerApiConfig,
    ): WorkerApiClient = DefaultWorkerApiClient(config)

    @Provides
    @Singleton
    fun provideNetworkStatus(
        @ApplicationContext context: Context,
    ): NetworkStatus = DefaultNetworkStatus(context)

    @Provides
    @Singleton
    fun provideSpeechTranscriber(
        @ApplicationContext context: Context,
    ): SpeechTranscriber = DefaultSpeechTranscriber(context)

    @Provides
    @Singleton
    fun provideAiExpenseSessionRepository(
        stringDataStore: StringDataStore,
    ): AiExpenseSessionRepository = DataStoreAiExpenseSessionRepository(stringDataStore)

    @Provides
    @Singleton
    fun provideAiExpensePreferencesRepository(
        stringDataStore: StringDataStore,
    ): AiExpensePreferencesRepository = DataStoreAiExpensePreferencesRepository(stringDataStore)

    @Provides
    @Singleton
    fun provideAiExpenseInterpreter(
        @ProtectedWorkerClient
        workerApiClient: WorkerApiClient,
    ): AiExpenseInterpreter = WorkerAiExpenseInterpreter(workerApiClient)

    @Provides
    fun provideParticipantNameMatcher(): ParticipantNameMatcher = DefaultParticipantNameMatcher()

    @Provides
    fun providePrepareAiExpenseUseCase(
        participantNameMatcher: ParticipantNameMatcher,
    ): PrepareAiExpenseUseCase = DefaultPrepareAiExpenseUseCase(participantNameMatcher)

    @Provides
    @Singleton
    fun provideContentResolver(
        @ApplicationContext context: Context,
    ): ContentResolver = context.contentResolver

    @Provides
    @Singleton
    fun provideReceiptImageReader(
        contentResolver: ContentResolver,
    ): ReceiptImageReader = DefaultReceiptImageReader(contentResolver)

    @Provides
    @Singleton
    fun provideReceiptCaptureTargetFactory(
        @ApplicationContext context: Context,
    ): ReceiptCaptureTargetFactory = DefaultReceiptCaptureTargetFactory(context)

    @Provides
    @Singleton
    fun provideReceiptRepository(
        @ProtectedWorkerClient
        workerApiClient: WorkerApiClient,
    ): ReceiptRepository = WorkerReceiptRepository(workerApiClient)

    @Provides
    fun provideShareLinkResponseMapper(): ShareLinkResponseMapper = DefaultShareLinkResponseMapper()

    @Provides
    @Singleton
    fun provideExpenseRepository(
        @ProtectedWorkerClient
        workerApiClient: WorkerApiClient,
        shareLinkResponseMapper: ShareLinkResponseMapper,
        draftRepository: ExpenseDraftRepository,
        aiSessionRepository: AiExpenseSessionRepository,
    ): ExpenseRepository = WorkerExpenseRepository(
        workerApiClient = workerApiClient,
        shareLinkResponseMapper = shareLinkResponseMapper,
        draftRepository = draftRepository,
        aiSessionRepository = aiSessionRepository,
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

    @Provides
    fun provideNormalizeReceiptUseCase(): NormalizeReceiptUseCase = DefaultNormalizeReceiptUseCase()

    @Provides
    fun provideGenerateGuestPasscodeUseCase(): GenerateGuestPasscodeUseCase = DefaultGenerateGuestPasscodeUseCase()
}
