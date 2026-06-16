package com.dps.evenup.feature.expenseflow.impl.choosepeople

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ChoosePeopleScreen(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        EvenUpTopBar(
            title = "Who was involved?",
            onNavigationClick = { onEvent(ChoosePeopleUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading people...",
                modifier = Modifier.weight(1f),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Expense unavailable",
                message = uiState.submitError ?: "Start a receipt before adding people.",
                modifier = Modifier.weight(1f),
                retryText = "Go back",
                onRetryClick = { onEvent(ChoosePeopleUiEvent.BackClick) },
            )
            else -> ChoosePeopleContent(
                uiState = uiState,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChoosePeopleContent(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = EvenUpTheme.spacing.space20)
                .padding(top = EvenUpTheme.spacing.space16, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
        ) {
            SelectedParticipantsSection(uiState = uiState, onEvent = onEvent)
            AddParticipantSection(uiState = uiState, onEvent = onEvent)
            SavedSuggestionsSection(uiState = uiState, onEvent = onEvent)
            PayerSection(uiState = uiState, onEvent = onEvent)
            uiState.submitError?.let { error ->
                EvenUpValidationMessage(message = error)
            }
        }
        EvenUpBottomActionBar(
            primaryText = if (uiState.isSaving) "Saving..." else "Continue",
            onPrimaryClick = { onEvent(ChoosePeopleUiEvent.ContinueClick) },
            primaryEnabled = uiState.canContinue,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SelectedParticipantsSection(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        SectionHeader(title = "Selected")
        if (uiState.participants.isEmpty()) {
            Text(
                text = "Add at least two people for this expense.",
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textSecondary,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            ) {
                uiState.participants.forEach { participant ->
                    SelectedParticipantAvatar(
                        participant = participant,
                        onRemove = { onEvent(ChoosePeopleUiEvent.RemoveParticipantClick(participant.id)) },
                    )
                }
            }
        }
        uiState.fieldErrors["participants"]?.let { error ->
            EvenUpValidationMessage(message = error)
        }
    }
}

@Composable
private fun SelectedParticipantAvatar(
    participant: ChoosePeopleParticipantUiState,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
    ) {
        Box {
            EvenUpParticipantAvatar(
                name = participant.name,
                colorIndex = participant.colorIndex,
                modifier = Modifier.size(56.dp),
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clickable(onClick = onRemove),
                shape = EvenUpTheme.shapes.avatar,
                color = EvenUpTheme.colors.surfaceElevated,
                border = BorderStroke(1.dp, EvenUpTheme.colors.border),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove ${participant.name}",
                    modifier = Modifier.padding(EvenUpTheme.spacing.space4),
                    tint = EvenUpTheme.colors.textSecondary,
                )
            }
        }
        Text(
            text = participant.name,
            style = EvenUpTheme.typography.caption,
            color = EvenUpTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddParticipantSection(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8)) {
        EvenUpTextField(
            value = uiState.participantNameInput,
            onValueChange = { onEvent(ChoosePeopleUiEvent.ParticipantNameInputChanged(it)) },
            label = "Search or add a person",
            isError = uiState.fieldErrors.containsKey("participantName"),
            supportingText = uiState.fieldErrors["participantName"],
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textSecondary,
                )
            },
        )
        EvenUpTextButton(
            text = "Add person",
            onClick = { onEvent(ChoosePeopleUiEvent.AddParticipantClick) },
            enabled = uiState.participantNameInput.isNotBlank(),
        )
    }
}

@Composable
private fun SavedSuggestionsSection(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        SectionHeader(title = "Suggested")
        if (uiState.savedSuggestions.isEmpty()) {
            Text(
                text = "Saved names will appear here after you add people.",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        } else {
            uiState.savedSuggestions.forEach { suggestion ->
                SavedSuggestionRow(
                    suggestion = suggestion,
                    onAdd = { onEvent(ChoosePeopleUiEvent.AddSavedParticipantClick(suggestion.name)) },
                    onDelete = { onEvent(ChoosePeopleUiEvent.DeleteSavedParticipantClick(suggestion.name)) },
                )
            }
        }
    }
}

@Composable
private fun SavedSuggestionRow(
    suggestion: SavedParticipantSuggestionUiState,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = EvenUpTheme.spacing.space4),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EvenUpParticipantAvatar(
            name = suggestion.name,
            colorIndex = suggestion.colorIndex,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = suggestion.name,
            modifier = Modifier.weight(1f),
            style = EvenUpTheme.typography.bodyStrong,
            color = EvenUpTheme.colors.textPrimary,
        )
        EvenUpIconButton(
            contentDescription = "Delete saved ${suggestion.name}",
            onClick = onDelete,
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = EvenUpTheme.colors.textSecondary,
            )
        }
        EvenUpIconButton(
            contentDescription = "Add ${suggestion.name}",
            onClick = onAdd,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = EvenUpTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun PayerSection(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        SectionHeader(title = "Who paid?")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            maxItemsInEachRow = 2,
        ) {
            uiState.participants.forEach { participant ->
                PayerCard(
                    participant = participant,
                    selected = participant.id == uiState.payerId,
                    onClick = { onEvent(ChoosePeopleUiEvent.PayerSelected(participant.id)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        uiState.fieldErrors["payer"]?.let { error ->
            EvenUpValidationMessage(message = error)
        }
    }
}

@Composable
private fun PayerCard(
    participant: ChoosePeopleParticipantUiState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) EvenUpTheme.colors.primary else EvenUpTheme.colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(EvenUpTheme.spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = participant.name,
                colorIndex = participant.colorIndex,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = participant.name,
                style = if (selected) EvenUpTheme.typography.bodyStrong else EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = EvenUpTheme.typography.sectionTitle,
        color = EvenUpTheme.colors.textSecondary,
    )
}
