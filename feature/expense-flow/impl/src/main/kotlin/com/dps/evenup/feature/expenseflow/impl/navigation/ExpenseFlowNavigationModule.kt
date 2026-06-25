package com.dps.evenup.feature.expenseflow.impl.navigation

import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.core.camera.api.ReceiptCaptureTargetFactory
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expense.api.ExpenseRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.domain.expense.api.AllocateFeesUseCase
import com.dps.evenup.domain.expense.api.CalculateExpenseSummaryUseCase
import com.dps.evenup.domain.expense.api.ValidateExpenseBeforeSaveUseCase
import com.dps.evenup.domain.expense.api.ValidateItemAssignmentsUseCase
import com.dps.evenup.domain.participant.api.ValidateParticipantsUseCase
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import com.dps.evenup.feature.expenseflow.api.AssignItemsDestination
import com.dps.evenup.feature.expenseflow.api.ChoosePeopleDestination
import com.dps.evenup.feature.expenseflow.api.ExpenseSavedDestination
import com.dps.evenup.feature.expenseflow.api.FeesAllocationDestination
import com.dps.evenup.feature.expenseflow.api.ManualEntryDestination
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptReviewDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptScanDestination
import com.dps.evenup.feature.expenseflow.api.ReviewExpenseDestination
import com.dps.evenup.feature.expenseflow.impl.assignitems.AssignItemsRoute
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
        allocateFees: AllocateFeesUseCase,
        calculateSummary: CalculateExpenseSummaryUseCase,
        validateExpenseBeforeSave: ValidateExpenseBeforeSaveUseCase,
        validateItemAssignments: ValidateItemAssignmentsUseCase,
        validateParticipants: ValidateParticipantsUseCase,
        normalizeReceipt: NormalizeReceiptUseCase,
        validateReceipt: ValidateReceiptUseCase,
    ): EvenUpEntryProviderInstaller = EvenUpEntryProviderInstaller { scope ->
        with(scope) {
            entry<NewExpenseDestination> {
                NewExpenseRoute(
                    onScanReceipt = { navigator.navigate(ReceiptScanDestination) },
                    onEnterManually = { navigator.navigate(ManualEntryDestination) },
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
            entry<FeesAllocationDestination> {
                FeesAllocationRoute(
                    draftRepository = draftRepository,
                    allocateFees = allocateFees,
                    onBack = navigator::navigateBack,
                    onContinue = { navigator.navigate(ReviewExpenseDestination) },
                )
            }
            entry<ReviewExpenseDestination> {
                ReviewExpenseRoute(
                    draftRepository = draftRepository,
                    expenseRepository = expenseRepository,
                    calculateSummary = calculateSummary,
                    validateExpenseBeforeSave = validateExpenseBeforeSave,
                    onBack = navigator::navigateBack,
                    onSaved = { shareUrl -> navigator.navigate(ExpenseSavedDestination(shareUrl = shareUrl)) },
                )
            }
            entry<ExpenseSavedDestination> { destination ->
                ExpenseSavedRoute(
                    shareUrl = destination.shareUrl,
                    draftRepository = draftRepository,
                    onAddAnother = { navigator.replaceAll(NewExpenseDestination) },
                )
            }
        }
    }
}
