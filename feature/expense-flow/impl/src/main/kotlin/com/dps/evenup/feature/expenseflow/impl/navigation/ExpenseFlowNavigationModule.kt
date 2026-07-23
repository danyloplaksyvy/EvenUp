package com.dps.evenup.feature.expenseflow.impl.navigation

import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.core.camera.api.ReceiptCaptureTargetFactory
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.network.api.NetworkStatus
import com.dps.evenup.core.speech.api.SpeechTranscriber
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseInterpreter
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.data.account.api.PendingAuthActionRepository
import com.dps.evenup.domain.account.api.PendingActionState
import com.dps.evenup.domain.account.api.PendingAuthAction
import com.dps.evenup.domain.account.api.PendingAuthActionType
import com.dps.evenup.domain.account.api.PendingAuthOrigin
import com.dps.evenup.domain.account.api.ProtectedActionDecision
import com.dps.evenup.domain.account.api.RequireAuthenticatedAccountUseCase
import com.dps.evenup.domain.account.api.ResumePendingAuthActionUseCase
import com.dps.evenup.feature.account.api.AuthenticationDestination
import com.dps.evenup.feature.account.api.ProfileDestination
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import com.dps.evenup.domain.sharing.api.GenerateGuestPasscodeUseCase
import com.dps.evenup.feature.expenseflow.api.AssignItemsDestination
import com.dps.evenup.feature.expenseflow.api.AiExtractedDetailsDestination
import com.dps.evenup.feature.expenseflow.api.ChoosePeopleDestination
import com.dps.evenup.feature.expenseflow.api.ExpenseSavedDestination
import com.dps.evenup.feature.expenseflow.api.EditAiDescriptionDestination
import com.dps.evenup.feature.expenseflow.api.EditAssignmentsDestination
import com.dps.evenup.feature.expenseflow.api.EditFeesDestination
import com.dps.evenup.feature.expenseflow.api.EditPeopleDestination
import com.dps.evenup.feature.expenseflow.api.FeesAllocationDestination
import com.dps.evenup.feature.expenseflow.api.ManualEntryDestination
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptReviewDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptScanDestination
import com.dps.evenup.feature.expenseflow.api.ReviewExpenseDestination
import com.dps.evenup.feature.expenseflow.impl.assignitems.AssignItemsRoute
import com.dps.evenup.feature.expenseflow.impl.aidetails.AiExtractedDetailsRoute
import com.dps.evenup.feature.expenseflow.impl.choosepeople.ChoosePeopleRoute
import com.dps.evenup.feature.expenseflow.impl.expensesaved.ExpenseSavedRoute
import com.dps.evenup.feature.expenseflow.impl.feesallocation.FeesAllocationRoute
import com.dps.evenup.feature.expenseflow.impl.feesallocation.shouldShowFeesAllocation
import com.dps.evenup.feature.expenseflow.impl.manualentry.ManualReceiptEntryRoute
import com.dps.evenup.feature.expenseflow.impl.newexpense.NewExpenseRoute
import com.dps.evenup.feature.expenseflow.impl.receiptscan.ReceiptScanRoute
import com.dps.evenup.feature.expenseflow.impl.receiptreview.ReceiptReviewRoute
import com.dps.evenup.feature.expenseflow.impl.reviewexpense.ReviewExpenseRoute
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.multibindings.IntoSet
import java.util.UUID

@Module
@InstallIn(ActivityRetainedComponent::class)
object ExpenseFlowNavigationModule {
    @Provides
    @IntoSet
    fun provideExpenseFlowEntryProviderInstaller(
        navigator: EvenUpNavigator,
        draftRepository: ExpenseDraftRepository,
        expenseRepository: ExpenseRepository,
        receiptRepository: ReceiptRepository,
        receiptImageReader: ReceiptImageReader,
        receiptCaptureTargetFactory: ReceiptCaptureTargetFactory,
        savedParticipantRepository: SavedParticipantRepository,
        aiExpenseInterpreter: AiExpenseInterpreter,
        aiExpenseSessionRepository: AiExpenseSessionRepository,
        aiExpensePreferencesRepository: AiExpensePreferencesRepository,
        prepareAiExpense: PrepareAiExpenseUseCase,
        speechTranscriber: SpeechTranscriber,
        networkStatus: NetworkStatus,
        allocateFees: AllocateFeesUseCase,
        calculateSummary: CalculateExpenseSummaryUseCase,
        validateExpenseBeforeSave: ValidateExpenseBeforeSaveUseCase,
        validateItemAssignments: ValidateItemAssignmentsUseCase,
        validateParticipants: ValidateParticipantsUseCase,
        normalizeReceipt: NormalizeReceiptUseCase,
        validateReceipt: ValidateReceiptUseCase,
        generateGuestPasscode: GenerateGuestPasscodeUseCase,
        requireAuthenticatedAccount: RequireAuthenticatedAccountUseCase,
        pendingActions: PendingAuthActionRepository,
        resumePendingAction: ResumePendingAuthActionUseCase,
    ): EvenUpEntryProviderInstaller = EvenUpEntryProviderInstaller { scope ->
        with(scope) {
            entry<NewExpenseDestination> {
                NewExpenseRoute(
                    interpreter = aiExpenseInterpreter,
                    sessionRepository = aiExpenseSessionRepository,
                    preferencesRepository = aiExpensePreferencesRepository,
                    savedParticipantRepository = savedParticipantRepository,
                    draftRepository = draftRepository,
                    prepareAiExpense = prepareAiExpense,
                    speechTranscriber = speechTranscriber,
                    networkStatus = networkStatus,
                    requireAuthenticatedAccount = requireAuthenticatedAccount,
                    pendingActions = pendingActions,
                    resumePendingAction = resumePendingAction,
                    onAuthenticationRequired = { navigator.navigate(AuthenticationDestination(it)) },
                    onProfile = { navigator.navigate(ProfileDestination) },
                    onScanReceipt = { navigator.navigate(ReceiptScanDestination) },
                    onEnterManually = { navigator.navigate(ManualEntryDestination) },
                    onReviewExpense = { navigator.navigate(ReviewExpenseDestination) },
                    onReviewAllDetails = { navigator.navigate(AiExtractedDetailsDestination()) },
                )
            }
            entry<EditAiDescriptionDestination> {
                NewExpenseRoute(
                    interpreter = aiExpenseInterpreter,
                    sessionRepository = aiExpenseSessionRepository,
                    preferencesRepository = aiExpensePreferencesRepository,
                    savedParticipantRepository = savedParticipantRepository,
                    draftRepository = draftRepository,
                    prepareAiExpense = prepareAiExpense,
                    speechTranscriber = speechTranscriber,
                    networkStatus = networkStatus,
                    requireAuthenticatedAccount = requireAuthenticatedAccount,
                    pendingActions = pendingActions,
                    resumePendingAction = resumePendingAction,
                    onAuthenticationRequired = { navigator.navigate(AuthenticationDestination(it)) },
                    onProfile = { navigator.navigate(ProfileDestination) },
                    onScanReceipt = { navigator.navigate(ReceiptScanDestination) },
                    onEnterManually = { navigator.navigate(ManualEntryDestination) },
                    onReviewExpense = { navigator.navigateBack() },
                    onReviewAllDetails = { navigator.navigate(AiExtractedDetailsDestination(fromReview = true)) },
                    onClose = { navigator.navigateBack() },
                )
            }
            entry<AiExtractedDetailsDestination> { destination ->
                AiExtractedDetailsRoute(
                    sessionRepository = aiExpenseSessionRepository,
                    preferencesRepository = aiExpensePreferencesRepository,
                    savedParticipantRepository = savedParticipantRepository,
                    draftRepository = draftRepository,
                    prepareAiExpense = prepareAiExpense,
                    fromReview = destination.fromReview,
                    onBack = { navigator.navigateBack() },
                    onReady = {
                        navigator.navigateBack()
                        if (!destination.fromReview) navigator.navigate(ReviewExpenseDestination)
                    },
                )
            }
            entry<ReceiptScanDestination> {
                ReceiptScanRoute(
                    draftRepository = draftRepository,
                    receiptRepository = receiptRepository,
                    receiptImageReader = receiptImageReader,
                    receiptCaptureTargetFactory = receiptCaptureTargetFactory,
                    normalizeReceipt = normalizeReceipt,
                    validateReceipt = validateReceipt,
                    onBack = navigator::navigateBack,
                    onManualEntry = { navigator.navigate(ManualEntryDestination) },
                    onContinue = { navigator.navigate(ReceiptReviewDestination) },
                )
            }
            entry<ManualEntryDestination> {
                ManualReceiptEntryRoute(
                    draftRepository = draftRepository,
                    validateReceipt = validateReceipt,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigate(ChoosePeopleDestination) },
                )
            }
            entry<ReceiptReviewDestination> {
                ReceiptReviewRoute(
                    draftRepository = draftRepository,
                    normalizeReceipt = normalizeReceipt,
                    validateReceipt = validateReceipt,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigate(ChoosePeopleDestination) },
                )
            }
            entry<ChoosePeopleDestination> {
                ChoosePeopleRoute(
                    draftRepository = draftRepository,
                    savedParticipantRepository = savedParticipantRepository,
                    validateParticipants = validateParticipants,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigate(AssignItemsDestination) },
                )
            }
            entry<EditPeopleDestination> {
                ChoosePeopleRoute(
                    draftRepository = draftRepository,
                    savedParticipantRepository = savedParticipantRepository,
                    validateParticipants = validateParticipants,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigateBack() },
                )
            }
            entry<AssignItemsDestination> {
                AssignItemsRoute(
                    draftRepository = draftRepository,
                    validateItemAssignments = validateItemAssignments,
                    onBack = navigator::navigateBack,
                    onContinue = {
                        val draft = draftRepository.getDraft()
                        if (shouldShowFeesAllocation(draft)) {
                            navigator.navigate(FeesAllocationDestination)
                        } else {
                            navigator.navigate(ReviewExpenseDestination)
                        }
                    },
                )
            }
            entry<EditAssignmentsDestination> {
                AssignItemsRoute(
                    draftRepository = draftRepository,
                    validateItemAssignments = validateItemAssignments,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigateBack() },
                )
            }
            entry<FeesAllocationDestination> {
                FeesAllocationRoute(
                    draftRepository = draftRepository,
                    allocateFees = allocateFees,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigate(ReviewExpenseDestination) },
                )
            }
            entry<EditFeesDestination> {
                FeesAllocationRoute(
                    draftRepository = draftRepository,
                    allocateFees = allocateFees,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigateBack() },
                )
            }
            entry<ReviewExpenseDestination> {
                ReviewExpenseRoute(
                    draftRepository = draftRepository,
                    expenseRepository = expenseRepository,
                    calculateSummary = calculateSummary,
                    validateExpenseBeforeSave = validateExpenseBeforeSave,
                    generateGuestPasscode = generateGuestPasscode,
                    aiSessionRepository = aiExpenseSessionRepository,
                    authorizeSave = {
                        val action = PendingAuthAction(
                            id = UUID.randomUUID().toString(),
                            type = PendingAuthActionType.ConfirmExpenseSave,
                            origin = PendingAuthOrigin.ReviewExpense,
                            reference = null,
                            createdAtEpochMillis = System.currentTimeMillis(),
                            state = PendingActionState.Pending,
                        )
                        when (requireAuthenticatedAccount.require(action)) {
                            ProtectedActionDecision.Allowed -> true
                            is ProtectedActionDecision.AuthenticationRequired,
                            ProtectedActionDecision.BootstrapRequired,
                            -> {
                                navigator.navigate(
                                    AuthenticationDestination(
                                        "Sign in to confirm, save, and share this expense.",
                                    ),
                                )
                                false
                            }
                        }
                    },
                    onBack = navigator::navigateBack,
                    onEditDescription = { navigator.navigate(EditAiDescriptionDestination) },
                    onEditDetails = { navigator.navigate(AiExtractedDetailsDestination(fromReview = true)) },
                    onEditPeople = { navigator.navigate(EditPeopleDestination) },
                    onEditAssignments = { navigator.navigate(EditAssignmentsDestination) },
                    onEditFees = { navigator.navigate(EditFeesDestination) },
                    onSaved = { shareUrl, guestPasscode ->
                        navigator.replaceAll(
                            ExpenseSavedDestination(
                                shareUrl = shareUrl,
                                guestPasscode = guestPasscode,
                            ),
                        )
                    },
                )
            }
            entry<ExpenseSavedDestination> { destination ->
                ExpenseSavedRoute(
                    shareUrl = destination.shareUrl,
                    guestPasscode = destination.guestPasscode,
                    draftRepository = draftRepository,
                    aiSessionRepository = aiExpenseSessionRepository,
                    onAddAnother = { navigator.replaceAll(NewExpenseDestination.fresh()) },
                )
            }
        }
    }
}
