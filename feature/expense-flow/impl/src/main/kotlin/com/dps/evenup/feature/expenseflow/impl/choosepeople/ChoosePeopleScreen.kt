package com.dps.evenup.feature.expenseflow.impl.choosepeople

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpBottomActionBar
import com.dps.evenup.core.designsystem.api.EvenUpPinnedTopBarScaffold
import com.dps.evenup.core.designsystem.api.EvenUpErrorState
import com.dps.evenup.core.designsystem.api.EvenUpIconButton
import com.dps.evenup.core.designsystem.api.EvenUpLoadingState
import com.dps.evenup.core.designsystem.api.EvenUpParticipantAvatar
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTextField
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ChoosePeopleScreen(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpPinnedTopBarScaffold(
        title = "Who was involved?",
        onNavigationClick = { onEvent(ChoosePeopleUiEvent.BackClick) },
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (!uiState.isLoading && !uiState.missingDraft) {
                EvenUpBottomActionBar(
                    primaryText = if (uiState.isSaving) "Saving..." else "Continue",
                    onPrimaryClick = { onEvent(ChoosePeopleUiEvent.ContinueClick) },
                    primaryEnabled = uiState.canContinue,
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> EvenUpLoadingState(
                message = "Loading people...",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            uiState.missingDraft -> EvenUpErrorState(
                title = "Expense unavailable",
                message = uiState.submitError ?: "Start a receipt before adding people.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                retryText = "Go back",
                onRetryClick = { onEvent(ChoosePeopleUiEvent.BackClick) },
            )
            else -> ChoosePeopleContent(
                uiState = uiState,
                onEvent = onEvent,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun ChoosePeopleContent(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = EvenUpTheme.spacing.space20)
            .padding(top = EvenUpTheme.spacing.space16, bottom = EvenUpTheme.spacing.space24),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space24),
    ) {
        PeopleInvolvedSection(uiState = uiState, onEvent = onEvent)
        AddParticipantSection(uiState = uiState, onEvent = onEvent)
        SavedSuggestionsSection(uiState = uiState, onEvent = onEvent)
        uiState.submitError?.let { error ->
            EvenUpValidationMessage(message = error)
        }
    }
}

@Composable
private fun PeopleInvolvedSection(
    uiState: ChoosePeopleUiState,
    onEvent: (ChoosePeopleUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12)) {
        SectionHeader(title = "People involved", detail = uiState.selectedCountLabel)
        AnimatedVisibility(visible = uiState.selectedParticipants.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
                contentPadding = PaddingValues(end = EvenUpTheme.spacing.space20),
            ) {
                items(
                    items = uiState.selectedParticipants,
                    key = { participant -> participant.id },
                ) { participant ->
                    AnimatedSelectedParticipantItem(
                        participant = participant,
                        onSetPayer = { onEvent(ChoosePeopleUiEvent.PayerSelected(participant.id)) },
                        onRemove = { onEvent(ChoosePeopleUiEvent.RemoveParticipantClick(participant.id)) },
                    )
                }
            }
        }
        Text(
            text = uiState.helperText,
            style = EvenUpTheme.typography.bodySmall,
            color = if (uiState.canContinue) {
                EvenUpTheme.colors.textSecondary
            } else {
                EvenUpTheme.colors.warning
            },
        )
        uiState.fieldErrors["participants"]?.let { error ->
            EvenUpValidationMessage(message = error)
        }
        uiState.fieldErrors["payer"]?.let { error ->
            EvenUpValidationMessage(message = error)
        }
    }
}

@Composable
private fun AnimatedSelectedParticipantItem(
    participant: SelectedParticipantUiState,
    onSetPayer: () -> Unit,
    onRemove: () -> Unit,
) {
    AnimatedRemoveContainer(
        enter = fadeIn(participantEnterSpec()) +
            slideInHorizontally(
                animationSpec = participantEnterSpec(),
                initialOffsetX = { it / 2 },
            ) +
            expandHorizontally(
                animationSpec = participantEnterSpec(),
                expandFrom = Alignment.Start,
            ),
        exit = fadeOut(participantExitSpec()) +
            slideOutHorizontally(
                animationSpec = participantExitSpec(),
                targetOffsetX = { -it / 2 },
            ) +
            shrinkHorizontally(
                animationSpec = participantExitSpec(),
                shrinkTowards = Alignment.Start,
            ),
        onRemoved = onRemove,
    ) { removeWithAnimation ->
        SelectedParticipantItem(
            participant = participant,
            onSetPayer = onSetPayer,
            onRemove = removeWithAnimation,
        )
    }
}

@Composable
private fun SelectedParticipantItem(
    participant: SelectedParticipantUiState,
    onSetPayer: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clickable(enabled = !participant.isPayer, onClick = onSetPayer)
            .semantics {
                if (!participant.isPayer) role = Role.Button
                contentDescription = participant.setPayerContentDescription
            },
        shape = EvenUpTheme.shapes.input,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(
            width = if (participant.isPayer) 2.dp else 1.dp,
            color = if (participant.isPayer) EvenUpTheme.colors.primary else EvenUpTheme.colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space12,
                vertical = EvenUpTheme.spacing.space8,
            ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvenUpParticipantAvatar(
                name = participant.name,
                colorIndex = participant.colorIndex,
                selected = participant.isPayer,
                modifier = Modifier.size(32.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
            ) {
                Text(
                    text = participant.name,
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = participant.payerActionLabel,
                    style = EvenUpTheme.typography.caption,
                    color = if (participant.isPayer) {
                        EvenUpTheme.colors.primary
                    } else {
                        EvenUpTheme.colors.textSecondary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EvenUpIconButton(
                contentDescription = participant.removeContentDescription,
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
            keyboardActions = KeyboardActions(
                onDone = {
                    if (uiState.typedAddLabel != null) {
                        onEvent(ChoosePeopleUiEvent.AddParticipantClick)
                    }
                },
            ),
            leadingContent = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = EvenUpTheme.colors.textSecondary,
                )
            },
        )
        uiState.typedAddLabel?.let { label ->
            EvenUpTextButton(
                text = label,
                onClick = { onEvent(ChoosePeopleUiEvent.AddParticipantClick) },
            )
        }
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
                text = if (uiState.participantNameInput.isBlank()) {
                    "Saved names will appear here after you add people."
                } else {
                    "No saved people match this search."
                },
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        } else {
            uiState.savedSuggestions.forEach { suggestion ->
                key(suggestion.name.lowercase()) {
                    AnimatedSavedSuggestionRow(
                        suggestion = suggestion,
                        onAdd = { onEvent(ChoosePeopleUiEvent.AddSavedParticipantClick(suggestion.name)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedSavedSuggestionRow(
    suggestion: SavedParticipantSuggestionUiState,
    onAdd: () -> Unit,
) {
    AnimatedRemoveContainer(
        enter = fadeIn(participantEnterSpec()) +
            slideInHorizontally(
                animationSpec = participantEnterSpec(),
                initialOffsetX = { it / 3 },
            ) +
            expandVertically(animationSpec = participantEnterSpec()),
        exit = fadeOut(participantExitSpec()) +
            slideOutHorizontally(
                animationSpec = participantExitSpec(),
                targetOffsetX = { -it / 3 },
            ) +
            shrinkVertically(animationSpec = participantExitSpec()),
        onRemoved = onAdd,
    ) { addWithAnimation ->
        SavedSuggestionRow(
            suggestion = suggestion,
            onAdd = addWithAnimation,
        )
    }
}

@Composable
private fun SavedSuggestionRow(
    suggestion: SavedParticipantSuggestionUiState,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd)
            .semantics {
                role = Role.Button
                contentDescription = "Add ${suggestion.name}"
            }
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
private fun SectionHeader(
    title: String,
    detail: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textSecondary,
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun AnimatedRemoveContainer(
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
    onRemoved: () -> Unit,
    content: @Composable (removeWithAnimation: () -> Unit) -> Unit,
) {
    val latestOnRemoved = rememberUpdatedState(onRemoved)
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }

    LaunchedEffect(visibleState.isIdle, visibleState.currentState, visibleState.targetState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) {
            latestOnRemoved.value()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        enter = enter,
        exit = exit,
    ) {
        content {
            visibleState.targetState = false
        }
    }
}

private fun <T> participantEnterSpec() = tween<T>(
    durationMillis = 180,
    easing = FastOutSlowInEasing,
)

private fun <T> participantExitSpec() = tween<T>(
    durationMillis = 140,
    easing = FastOutSlowInEasing,
)
