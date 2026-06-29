package com.dps.evenup.feature.expenseflow.impl.expensesaved

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpSecondaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage
import com.dps.evenup.core.designsystem.api.EvenUpValidationSeverity

@Composable
fun ExpenseSavedScreen(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = EvenUpTheme.spacing.space20,
                vertical = EvenUpTheme.spacing.space24,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
    ) {
        SuccessHeader()
        uiState.message?.let { message ->
            EvenUpValidationMessage(
                message = message,
                severity = if (message.contains("copied", ignoreCase = true)) {
                    EvenUpValidationSeverity.Success
                } else {
                    EvenUpValidationSeverity.Warning
                },
            )
        }
        QrAccessCard(uiState = uiState)
        AccessDetailsCard(uiState = uiState, onEvent = onEvent)
        ShareActions(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun SuccessHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
    ) {
        SuccessMark()
        Text(
            text = "Expense saved",
            modifier = Modifier.semantics { contentDescription = "Expense saved successfully" },
            style = EvenUpTheme.typography.displayLargeTotal,
            color = EvenUpTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Share this breakdown with your group.",
            style = EvenUpTheme.typography.body,
            color = EvenUpTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SuccessMark() {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .semantics { contentDescription = "Expense saved confirmation" },
        shape = EvenUpTheme.shapes.avatar,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun QrAccessCard(uiState: ExpenseSavedUiState) {
    val qrAccessUrl = uiState.qrAccessUrl
    val qrCodeMatrix = remember(qrAccessUrl) {
        qrAccessUrl?.let(QrCodeGenerator::encode)
    }

    EvenUpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            Text(
                text = "Scan to open",
                style = EvenUpTheme.typography.sectionTitle,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (qrCodeMatrix != null) {
                QrCodeCanvas(matrix = qrCodeMatrix)
            } else {
                Text(
                    text = "QR code will appear when the link and guest code are ready.",
                    style = EvenUpTheme.typography.bodySmall,
                    color = EvenUpTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun QrCodeCanvas(
    matrix: QrCodeMatrix,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = EvenUpTheme.colors.surfaceElevated
    val moduleColor = EvenUpTheme.colors.textPrimary
    val quietZoneModules = 4

    Canvas(
        modifier = modifier
            .size(208.dp)
            .semantics {
                contentDescription = "QR code that opens the expense breakdown without entering a guest code"
            },
    ) {
        val moduleCount = matrix.size + quietZoneModules * 2
        val moduleSize = minOf(size.width, size.height) / moduleCount
        val qrSize = moduleSize * moduleCount
        val left = (size.width - qrSize) / 2f
        val top = (size.height - qrSize) / 2f

        drawRect(
            color = backgroundColor,
            topLeft = Offset(left, top),
            size = Size(qrSize, qrSize),
        )

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    drawRect(
                        color = moduleColor,
                        topLeft = Offset(
                            x = left + (x + quietZoneModules) * moduleSize,
                            y = top + (y + quietZoneModules) * moduleSize,
                        ),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessDetailsCard(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
) {
    EvenUpCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            AccessDetailRow(
                label = "Link",
                value = uiState.bareShareLink.ifBlank { "Not available" },
                valueContentDescription = "Share link. Use Copy link to copy the full URL.",
                helper = "Opening this link manually asks for the guest code.",
                actionText = "Copy link",
                actionContentDescription = "Copy bare expense share link",
                enabled = uiState.canCopyLink,
                onClick = { onEvent(ExpenseSavedUiEvent.CopyLinkClick) },
            )
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            AccessDetailRow(
                label = "Guest code",
                value = uiState.normalizedGuestCode.ifBlank { "Not available" },
                valueContentDescription = "Guest code ${uiState.normalizedGuestCode}. Needed only when opening the link manually.",
                helper = "Needed only when someone opens the link manually.",
                actionText = "Copy code",
                actionContentDescription = "Copy guest code",
                enabled = uiState.canCopyCode,
                onClick = { onEvent(ExpenseSavedUiEvent.CopyCodeClick) },
            )
        }
    }
}

@Composable
private fun AccessDetailRow(
    label: String,
    value: String,
    valueContentDescription: String,
    helper: String,
    actionText: String,
    actionContentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space4),
        ) {
            Text(
                text = label,
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = value,
                modifier = Modifier.semantics { contentDescription = valueContentDescription },
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = helper,
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        EvenUpTextButton(
            text = actionText,
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = actionContentDescription },
        )
    }
}

@Composable
private fun ShareActions(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
    ) {
        EvenUpPrimaryButton(
            text = "Share invite",
            onClick = { onEvent(ExpenseSavedUiEvent.ShareInviteClick) },
            enabled = uiState.canShareInvite,
            modifier = Modifier.semantics {
                contentDescription = "Share invite with link and guest code"
            },
        )
        EvenUpSecondaryButton(
            text = "Copy invite",
            onClick = { onEvent(ExpenseSavedUiEvent.CopyInviteClick) },
            enabled = uiState.canShareInvite,
            modifier = Modifier.semantics {
                contentDescription = "Copy invite message with link and guest code"
            },
        )
        EvenUpTextButton(
            text = if (uiState.isWorking) "Starting..." else "Add another expense",
            onClick = { onEvent(ExpenseSavedUiEvent.AddAnotherClick) },
            enabled = !uiState.isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Add another expense" },
        )
    }
}
