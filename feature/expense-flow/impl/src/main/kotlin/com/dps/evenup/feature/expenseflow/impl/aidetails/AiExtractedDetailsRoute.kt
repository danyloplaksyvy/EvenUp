package com.dps.evenup.feature.expenseflow.impl.aidetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.evenup.core.designsystem.api.EvenUpBottomSheet
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpMoneyField
import com.dps.evenup.core.designsystem.api.EvenUpPinnedTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.expenseinput.api.AiExpensePreferencesRepository
import com.dps.evenup.data.expenseinput.api.AiExpenseSessionRepository
import com.dps.evenup.data.participant.api.SavedParticipantRepository
import com.dps.evenup.domain.expenseinput.api.AiAssignmentMode
import com.dps.evenup.domain.expenseinput.api.AiFeeAllocationMode
import com.dps.evenup.domain.expenseinput.api.AiPricingMode
import com.dps.evenup.domain.expenseinput.api.PrepareAiExpenseUseCase
import com.dps.evenup.domain.receipt.api.FeeType
import com.dps.evenup.feature.expenseflow.impl.receiptentry.CurrencySelector
import com.dps.evenup.feature.expenseflow.impl.receiptentry.ReceiptDatePickerField
import com.dps.evenup.feature.expenseflow.impl.receiptentry.SmartStickyActionBar

@Composable
fun AiExtractedDetailsRoute(
    sessionRepository: AiExpenseSessionRepository,
    preferencesRepository: AiExpensePreferencesRepository,
    savedParticipantRepository: SavedParticipantRepository,
    draftRepository: ExpenseDraftRepository,
    prepareAiExpense: PrepareAiExpenseUseCase,
    fromReview: Boolean,
    onBack: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(
        sessionRepository,
        preferencesRepository,
        savedParticipantRepository,
        draftRepository,
        prepareAiExpense,
        fromReview,
    ) {
        AiExtractedDetailsViewModel.factory(
            sessionRepository = sessionRepository,
            preferencesRepository = preferencesRepository,
            savedParticipantRepository = savedParticipantRepository,
            draftRepository = draftRepository,
            prepareAiExpense = prepareAiExpense,
            fromReview = fromReview,
        )
    }
    val viewModel: AiExtractedDetailsViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AiExtractedDetailsEffect.Ready -> onReady()
            }
        }
    }
    AiExtractedDetailsScreen(
        state = state,
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun AiExtractedDetailsScreen(
    state: AiExtractedDetailsUiState,
    viewModel: AiExtractedDetailsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpPinnedTopBarScaffold(
        title = "Review details",
        onNavigationClick = onBack,
        navigationContentDescription = "Back",
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            SmartStickyActionBar(
                primaryText = state.primaryLabel,
                onPrimaryClick = viewModel::primaryAction,
                primaryEnabled = !state.isLoading && !state.isSaving,
                helperText = state.blockingMessage,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = EvenUpTheme.colors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = EvenUpTheme.spacing.space20, vertical = EvenUpTheme.spacing.space16),
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space20),
            ) {
                state.pageError?.let { EvenUpValidationMessage(it) }
                ExpenseSection(state, viewModel::openFacts)
                PeopleSection(state, viewModel::openPeople)
                ItemsSection(state, viewModel::openItem)
                FeesSection(state, viewModel::openFee)
                SplitSection(state, viewModel::openSplit, viewModel::openAssignment)
            }
        }
    }
    DetailsEditSheet(state = state, viewModel = viewModel)
}

@Composable
private fun ExpenseSection(state: AiExtractedDetailsUiState, onClick: () -> Unit) {
    val extraction = state.extraction
    ReviewSection(title = "Expense") {
        ReviewValueRow("Title", extraction.title ?: "Shared expense", onClick)
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        ReviewValueRow("Date", extraction.transactionDate.orEmpty().ifBlank { "Choose date" }, onClick)
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        ReviewValueRow("Currency", extraction.currency.orEmpty().ifBlank { "Choose currency" }, onClick)
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        ReviewValueRow("Total", extraction.totalMinor.money(extraction.currency), onClick)
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        ReviewValueRow(
            "Pricing",
            if (extraction.pricingMode == AiPricingMode.TotalOnly) "Overall total" else "Itemized",
            onClick,
        )
    }
}

@Composable
private fun PeopleSection(state: AiExtractedDetailsUiState, onClick: () -> Unit) {
    val extraction = state.extraction
    val payer = extraction.participants.firstOrNull { it.ref == extraction.payerParticipantRef }?.name
    ReviewSection(title = "People", detail = "${extraction.participants.size}") {
        ReviewValueRow(
            label = "Participants",
            value = extraction.participants.joinToString { it.name }.ifBlank { "Add people" },
            onClick = onClick,
        )
        HorizontalDivider(color = EvenUpTheme.colors.divider)
        ReviewValueRow("Paid by", payer ?: "Choose payer", onClick)
    }
}

@Composable
private fun ItemsSection(state: AiExtractedDetailsUiState, onItemClick: (String?) -> Unit) {
    val extraction = state.extraction
    ReviewSection(title = "Items", detail = "${extraction.items.size}") {
        if (extraction.items.isEmpty()) {
            EmptySectionRow("No items yet", "Add an item", { onItemClick(null) })
        } else {
            extraction.items.forEachIndexed { index, item ->
                ReviewValueRow(
                    label = item.name.ifBlank { "Unnamed item" },
                    value = if (extraction.pricingMode == AiPricingMode.TotalOnly) {
                        "Quantity ${item.quantity ?: 1}"
                    } else {
                        item.totalPriceMinor.money(extraction.currency)
                    },
                    onClick = { onItemClick(item.ref) },
                )
                if (index != extraction.items.lastIndex) HorizontalDivider(color = EvenUpTheme.colors.divider)
            }
            AddRow("Add item") { onItemClick(null) }
        }
    }
}

@Composable
private fun FeesSection(state: AiExtractedDetailsUiState, onFeeClick: (String?) -> Unit) {
    val extraction = state.extraction
    ReviewSection(title = "Fees and discounts", detail = "${extraction.fees.size}") {
        if (extraction.fees.isEmpty()) {
            EmptySectionRow("No fees or discounts", "Add fee or discount", { onFeeClick(null) })
        } else {
            extraction.fees.forEachIndexed { index, fee ->
                ReviewValueRow(
                    label = fee.label.ifBlank { fee.type.displayName() },
                    value = fee.amountMinor.money(extraction.currency),
                    onClick = { onFeeClick(fee.ref) },
                )
                if (index != extraction.fees.lastIndex) HorizontalDivider(color = EvenUpTheme.colors.divider)
            }
            AddRow("Add fee or discount") { onFeeClick(null) }
        }
    }
}

@Composable
private fun SplitSection(
    state: AiExtractedDetailsUiState,
    onSplitClick: () -> Unit,
    onAssignmentClick: (String) -> Unit,
) {
    val extraction = state.extraction
    ReviewSection(title = "Split") {
        ReviewValueRow(
            label = "Overall",
            value = if (extraction.splitEverythingEqually) "Split everything equally" else "Item-level split",
            onClick = onSplitClick,
        )
        if (extraction.pricingMode == AiPricingMode.Itemized && !extraction.splitEverythingEqually) {
            extraction.items.forEach { item ->
                HorizontalDivider(color = EvenUpTheme.colors.divider)
                ReviewValueRow(
                    label = item.name,
                    value = item.assignment?.mode?.displayName() ?: "Choose split",
                    onClick = { onAssignmentClick(item.ref) },
                    isError = item.assignment == null,
                )
            }
        }
    }
}

@Composable
private fun ReviewSection(
    title: String,
    detail: String = "",
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = EvenUpTheme.typography.button, color = EvenUpTheme.colors.textPrimary)
            if (detail.isNotBlank()) {
                Text(detail, style = EvenUpTheme.typography.caption, color = EvenUpTheme.colors.textSecondary)
            }
        }
        EvenUpCard(contentPadding = PaddingValues(horizontal = EvenUpTheme.spacing.space20, vertical = EvenUpTheme.spacing.space12)) {
            content()
        }
    }
}

@Composable
private fun ReviewValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Edit $label, $value"
            }
            .padding(vertical = EvenUpTheme.spacing.space8),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.bodySmall,
            color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textSecondary,
        )
        Text(
            value,
            modifier = Modifier.weight(1.5f),
            style = EvenUpTheme.typography.body,
            color = if (isError) EvenUpTheme.colors.error else EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EvenUpTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptySectionRow(title: String, action: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = EvenUpTheme.spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        Text(title, style = EvenUpTheme.typography.bodySmall, color = EvenUpTheme.colors.textSecondary)
        EvenUpSecondaryButton(action, onClick)
    }
}

@Composable
private fun AddRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = EvenUpTheme.spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, style = EvenUpTheme.typography.button)
    }
}

@Composable
private fun DetailsEditSheet(state: AiExtractedDetailsUiState, viewModel: AiExtractedDetailsViewModel) {
    val draft = state.editDraft
    EvenUpBottomSheet(
        visible = draft != null,
        onDismissRequest = viewModel::dismissSheet,
        title = draft.sheetTitle(),
        headerAction = {
            if ((draft is AiDetailsEditDraft.Item && !draft.isNew) || (draft is AiDetailsEditDraft.Fee && !draft.isNew)) {
                EvenUpIconButton("Delete", viewModel::deleteActiveRow) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = EvenUpTheme.colors.error)
                }
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
        ) {
            when (draft) {
                is AiDetailsEditDraft.Facts -> FactsSheet(draft, viewModel)
                is AiDetailsEditDraft.People -> PeopleSheet(draft, state, viewModel)
                is AiDetailsEditDraft.Item -> ItemSheet(draft, state, viewModel)
                is AiDetailsEditDraft.Fee -> FeeSheet(draft, state, viewModel)
                is AiDetailsEditDraft.Split -> SplitSheet(draft, viewModel)
                is AiDetailsEditDraft.Assignment -> AssignmentSheet(draft, viewModel)
                null -> Unit
            }
            state.sheetError?.let { EvenUpValidationMessage(it) }
            EvenUpPrimaryButton("Apply", viewModel::applySheet)
        }
    }
}

@Composable
private fun FactsSheet(draft: AiDetailsEditDraft.Facts, viewModel: AiExtractedDetailsViewModel) {
    EvenUpTextField(
        value = draft.title,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Facts).copy(title = value) } },
        label = "Expense title",
    )
    ReceiptDatePickerField(
        value = draft.date,
        onDateSelected = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Facts).copy(date = value) } },
    )
    Text("Currency", style = EvenUpTheme.typography.bodyStrong)
    CurrencySelector(
        selectedCurrencyCode = draft.currency,
        onCurrencySelected = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Facts).copy(currency = value) } },
        currencyCodes = listOf(draft.currency, "USD", "EUR", "GBP").filter(String::isNotBlank),
    )
    EvenUpMoneyField(
        value = draft.total,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Facts).copy(total = value) } },
        label = "Expense total",
        currencySymbol = draft.currency,
    )
    Text("Pricing", style = EvenUpTheme.typography.bodyStrong)
    ChoiceRow(
        options = listOf(AiPricingMode.Itemized, AiPricingMode.TotalOnly),
        selected = draft.pricingMode,
        label = { if (it == AiPricingMode.Itemized) "Itemized" else "Overall total" },
        onSelect = { mode -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Facts).copy(pricingMode = mode) } },
    )
}

@Composable
private fun PeopleSheet(
    draft: AiDetailsEditDraft.People,
    state: AiExtractedDetailsUiState,
    viewModel: AiExtractedDetailsViewModel,
) {
    Text("Select the payer and edit the people included.", style = EvenUpTheme.typography.bodySmall, color = EvenUpTheme.colors.textSecondary)
    draft.participants.forEach { participant ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = draft.payerRef == participant.ref,
                onClick = { viewModel.updateEditDraft { (it as AiDetailsEditDraft.People).copy(payerRef = participant.ref) } },
            )
            EvenUpTextField(
                value = participant.name,
                onValueChange = { name ->
                    viewModel.updateEditDraft {
                        val people = it as AiDetailsEditDraft.People
                        people.copy(participants = people.participants.map { row ->
                            if (row.ref == participant.ref) row.copy(name = name) else row
                        })
                    }
                },
                label = if (participant.isSelf) "Your name" else "Name",
                modifier = Modifier.weight(1f),
            )
            EvenUpIconButton("Remove ${participant.name}", { viewModel.removeDraftParticipant(participant.ref) }) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = EvenUpTheme.colors.textSecondary)
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvenUpTextField(
            value = draft.newName,
            onValueChange = { name -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.People).copy(newName = name) } },
            label = "Add person",
            modifier = Modifier.weight(1f),
        )
        EvenUpIconButton("Add person", { viewModel.addDraftParticipant(draft.newName) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
    val suggestions = state.savedParticipantNames.filter { saved -> draft.participants.none { it.name.equals(saved, true) } }
    if (suggestions.isNotEmpty()) {
        Text("Saved people", style = EvenUpTheme.typography.bodyStrong)
        suggestions.forEach { name -> EvenUpTextButton("Add $name", { viewModel.addDraftParticipant(name) }) }
    }
}

@Composable
private fun ItemSheet(
    draft: AiDetailsEditDraft.Item,
    state: AiExtractedDetailsUiState,
    viewModel: AiExtractedDetailsViewModel,
) {
    if (draft.deleteRequested) {
        Text(
            "This item will be removed when you tap Apply.",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.error,
        )
    }
    EvenUpTextField(
        value = draft.name,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Item).copy(name = value) } },
        label = "Item name",
    )
    EvenUpTextField(
        value = draft.quantity,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Item).copy(quantity = value) } },
        label = "Quantity",
    )
    if (draft.pricingMode == AiPricingMode.Itemized) {
        EvenUpMoneyField(
            value = draft.total,
            onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Item).copy(total = value) } },
            label = "Item total",
            currencySymbol = state.extraction.currency ?: "$",
        )
    } else {
        Text(
            "Overall-total items are descriptive and do not have prices.",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun FeeSheet(
    draft: AiDetailsEditDraft.Fee,
    state: AiExtractedDetailsUiState,
    viewModel: AiExtractedDetailsViewModel,
) {
    if (draft.deleteRequested) {
        Text(
            "This fee or discount will be removed when you tap Apply.",
            style = EvenUpTheme.typography.bodySmall,
            color = EvenUpTheme.colors.error,
        )
    }
    EvenUpTextField(
        value = draft.label,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Fee).copy(label = value) } },
        label = "Name",
    )
    Text("Type", style = EvenUpTheme.typography.bodyStrong)
    ChoiceColumn(
        options = FeeType.entries,
        selected = draft.type,
        label = FeeType::displayName,
        onSelect = { type -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Fee).copy(type = type) } },
    )
    EvenUpMoneyField(
        value = draft.amount,
        onValueChange = { value -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Fee).copy(amount = value) } },
        label = if (draft.type == FeeType.Discount) "Discount amount" else "Amount",
        currencySymbol = state.extraction.currency ?: "$",
    )
    Text("Allocation", style = EvenUpTheme.typography.bodyStrong)
    ChoiceColumn(
        options = AiFeeAllocationMode.entries,
        selected = draft.allocationMode,
        label = { it.displayName() },
        onSelect = { mode -> viewModel.updateEditDraft { (it as AiDetailsEditDraft.Fee).copy(allocationMode = mode) } },
    )
    Text("Included people", style = EvenUpTheme.typography.bodyStrong)
    state.extraction.participants.forEach { participant ->
        val checked = participant.ref in draft.participantRefs
        Row(
            modifier = Modifier.fillMaxWidth().clickable {
                viewModel.updateEditDraft {
                    val fee = it as AiDetailsEditDraft.Fee
                    fee.copy(participantRefs = if (checked) fee.participantRefs - participant.ref else fee.participantRefs + participant.ref)
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(participant.name, modifier = Modifier.weight(1f), style = EvenUpTheme.typography.body)
            if (checked && draft.allocationMode == AiFeeAllocationMode.Custom) {
                EvenUpMoneyField(
                    value = draft.customAmounts[participant.ref].orEmpty(),
                    onValueChange = { value -> viewModel.updateEditDraft {
                        val fee = it as AiDetailsEditDraft.Fee
                        fee.copy(customAmounts = fee.customAmounts + (participant.ref to value))
                    } },
                    label = "Share",
                    currencySymbol = state.extraction.currency ?: "$",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SplitSheet(draft: AiDetailsEditDraft.Split, viewModel: AiExtractedDetailsViewModel) {
    ChoiceColumn(
        options = listOf(true, false),
        selected = draft.splitEverythingEqually,
        label = { equal -> if (equal) "Split everything equally" else "Set item-level splits" },
        onSelect = { equal ->
            viewModel.updateEditDraft { (it as AiDetailsEditDraft.Split).copy(splitEverythingEqually = equal) }
        },
    )
    Text(
        if (draft.splitEverythingEqually) {
            "The base amount and every fee or discount will be shared equally."
        } else {
            "Apply this choice, then open each item to choose its allocation."
        },
        style = EvenUpTheme.typography.bodySmall,
        color = EvenUpTheme.colors.textSecondary,
    )
}

@Composable
private fun AssignmentSheet(draft: AiDetailsEditDraft.Assignment, viewModel: AiExtractedDetailsViewModel) {
    Text(draft.itemName, style = EvenUpTheme.typography.bodyStrong)
    ChoiceColumn(
        options = AiAssignmentMode.entries,
        selected = draft.mode,
        label = { it.displayName() },
        onSelect = { mode ->
            viewModel.updateEditDraft {
                val assignment = it as AiDetailsEditDraft.Assignment
                assignment.copy(
                    mode = mode,
                    shares = assignment.shares.map { share -> share.copy(value = "") },
                )
            }
        },
    )
    draft.shares.forEach { share ->
        val singleChoice = draft.mode == AiAssignmentMode.Full
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (singleChoice) {
                RadioButton(
                    selected = share.selected,
                    onClick = {
                        viewModel.updateEditDraft {
                            val assignment = it as AiDetailsEditDraft.Assignment
                            assignment.copy(shares = assignment.shares.map { row -> row.copy(selected = row.participantRef == share.participantRef) })
                        }
                    },
                )
            } else {
                Checkbox(
                    checked = share.selected,
                    onCheckedChange = { checked ->
                        viewModel.updateEditDraft {
                            val assignment = it as AiDetailsEditDraft.Assignment
                            assignment.copy(shares = assignment.shares.map { row ->
                                if (row.participantRef == share.participantRef) row.copy(selected = checked) else row
                            })
                        }
                    },
                )
            }
            Text(share.participantName, modifier = Modifier.weight(1f), style = EvenUpTheme.typography.body)
            if (share.selected && draft.mode.needsValue()) {
                EvenUpTextField(
                    value = share.value,
                    onValueChange = { value ->
                        viewModel.updateEditDraft {
                            val assignment = it as AiDetailsEditDraft.Assignment
                            assignment.copy(shares = assignment.shares.map { row ->
                                if (row.participantRef == share.participantRef) row.copy(value = value) else row
                            })
                        }
                    },
                    label = draft.mode.valueLabel(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        options.forEach { option ->
            ChoiceSurface(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun <T> ChoiceColumn(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        options.forEach { option ->
            ChoiceSurface(label(option), option == selected, { onSelect(option) })
        }
    }
}

@Composable
private fun ChoiceSurface(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        shape = EvenUpTheme.shapes.chip,
        color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.surfaceElevated,
        contentColor = if (selected) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = EvenUpTheme.spacing.space12, vertical = EvenUpTheme.spacing.space12),
            style = EvenUpTheme.typography.button,
            textAlign = TextAlign.Center,
        )
    }
}

private fun AiDetailsEditDraft?.sheetTitle(): String = when (this) {
    is AiDetailsEditDraft.Facts -> "Edit expense"
    is AiDetailsEditDraft.People -> "Edit people"
    is AiDetailsEditDraft.Item -> if (isNew) "Add item" else "Edit item"
    is AiDetailsEditDraft.Fee -> if (isNew) "Add fee or discount" else "Edit fee or discount"
    is AiDetailsEditDraft.Split -> "Edit split"
    is AiDetailsEditDraft.Assignment -> "Split item"
    null -> ""
}

private fun Long?.money(currency: String?): String {
    if (this == null) return "Add amount"
    val absolute = kotlin.math.abs(this)
    val sign = if (this < 0) "−" else ""
    return "$sign${currency.orEmpty()} ${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}".trim()
}

private fun FeeType.displayName(): String = when (this) {
    FeeType.Tax -> "Tax"
    FeeType.Tip -> "Tip"
    FeeType.ServiceFee -> "Service fee"
    FeeType.Discount -> "Discount"
    FeeType.Other -> "Other"
}

private fun AiFeeAllocationMode.displayName(): String = when (this) {
    AiFeeAllocationMode.Equal -> "Equal"
    AiFeeAllocationMode.Proportional -> "Proportional"
    AiFeeAllocationMode.Custom -> "Custom amounts"
}

private fun AiAssignmentMode.displayName(): String = when (this) {
    AiAssignmentMode.Full -> "One person"
    AiAssignmentMode.ByUnits -> "By quantity"
    AiAssignmentMode.SharedEqual -> "Shared equally"
    AiAssignmentMode.CustomAmount -> "Custom amounts"
    AiAssignmentMode.Percentage -> "Percentages"
    AiAssignmentMode.Ratio -> "Ratios"
}

private fun AiAssignmentMode.needsValue(): Boolean = this !in setOf(AiAssignmentMode.Full, AiAssignmentMode.SharedEqual)

private fun AiAssignmentMode.valueLabel(): String = when (this) {
    AiAssignmentMode.ByUnits -> "Quantity"
    AiAssignmentMode.CustomAmount -> "Amount"
    AiAssignmentMode.Percentage -> "Percent"
    AiAssignmentMode.Ratio -> "Ratio"
    else -> "Value"
}
