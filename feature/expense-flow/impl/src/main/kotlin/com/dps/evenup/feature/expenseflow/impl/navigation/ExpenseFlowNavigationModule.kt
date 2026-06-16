package com.dps.evenup.feature.expenseflow.impl.navigation

import com.dps.evenup.core.navigation.api.EvenUpEntryProviderInstaller
import com.dps.evenup.core.navigation.api.EvenUpNavigator
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.feature.expenseflow.api.ChoosePeopleDestination
import com.dps.evenup.feature.expenseflow.api.ManualEntryDestination
import com.dps.evenup.feature.expenseflow.api.NewExpenseDestination
import com.dps.evenup.feature.expenseflow.api.ReceiptScanDestination
import com.dps.evenup.feature.expenseflow.impl.manualentry.ManualReceiptEntryRoute
import com.dps.evenup.feature.expenseflow.impl.newexpense.NewExpenseRoute
import com.dps.evenup.feature.expenseflow.impl.placeholder.ExpenseFlowPlaceholderRoute
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
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
                ExpenseFlowPlaceholderRoute(
                    title = "Scan receipt",
                    message = "Receipt capture will be added in its milestone task.",
                    onBack = navigator::navigateBack,
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
            entry<ChoosePeopleDestination> {
                ExpenseFlowPlaceholderRoute(
                    title = "Choose people",
                    message = "Participants and payer selection will be added in its milestone task.",
                    onBack = navigator::navigateBack,
                )
            }
        }
    }
}
