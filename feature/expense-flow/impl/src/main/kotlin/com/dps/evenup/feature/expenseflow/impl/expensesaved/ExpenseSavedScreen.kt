package com.dps.evenup.feature.expenseflow.impl.expensesaved

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme

@Composable
fun ExpenseSavedScreen(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessageId) {
        val message = uiState.snackbarMessage
        if (uiState.snackbarMessageId != 0L && message != null) {
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = EvenUpTheme.spacing.space20,
                    vertical = EvenUpTheme.spacing.space20,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            SuccessHeader()
            QrAccessCard(uiState = uiState, onEvent = onEvent)
            AccessDetailsCard(uiState = uiState, onEvent = onEvent)
            ShareActions(uiState = uiState, onEvent = onEvent)
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(horizontal = EvenUpTheme.spacing.space16)
                .padding(bottom = EvenUpTheme.spacing.space16),
        )
    }
}

@Composable
private fun SuccessHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
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
            .size(64.dp)
            .semantics { contentDescription = "Expense saved confirmation" },
        shape = EvenUpTheme.shapes.avatar,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = EvenUpTheme.colors.textPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
private fun QrAccessCard(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
) {
    val qrAccessUrl = uiState.qrAccessUrl
    val qrCodeMatrix = remember(qrAccessUrl) {
        qrAccessUrl?.let(QrCodeGenerator::encode)
    }

    EvenUpCard(
        modifier = Modifier
            .clickable(enabled = uiState.canOpenQr) { onEvent(ExpenseSavedUiEvent.QrOpenClick) }
            .semantics {
                role = Role.Button
                contentDescription = if (uiState.canOpenQr) {
                    "Scan or tap to open the expense breakdown without entering the guest code"
                } else {
                    "Expense breakdown QR code is not ready"
                }
            },
        contentPadding = PaddingValues(EvenUpTheme.spacing.space16),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            Text(
                text = "Scan or tap to open",
                style = EvenUpTheme.typography.sectionTitle,
                color = EvenUpTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Opens without code",
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
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
            .size(192.dp)
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
    EvenUpCard(
        contentPadding = PaddingValues(
            horizontal = EvenUpTheme.spacing.space16,
            vertical = EvenUpTheme.spacing.space12,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
        ) {
            LinkDetailRow(uiState = uiState, onEvent = onEvent)
            HorizontalDivider(color = EvenUpTheme.colors.divider)
            GuestCodeRow(uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun LinkDetailRow(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
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
                text = "Link",
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = uiState.bareShareLink.ifBlank { "Not available" },
                modifier = Modifier.semantics {
                    contentDescription = "Share link. Use Copy link to copy only the full URL."
                },
                style = EvenUpTheme.typography.body,
                color = EvenUpTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        EvenUpTextButton(
            text = "Copy link",
            onClick = { onEvent(ExpenseSavedUiEvent.CopyLinkClick) },
            enabled = uiState.canCopyLink,
            modifier = Modifier.semantics { contentDescription = uiState.copyLinkContentDescription },
        )
    }
}

@Composable
private fun GuestCodeRow(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
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
                text = "Guest code",
                style = EvenUpTheme.typography.caption,
                color = EvenUpTheme.colors.textSecondary,
            )
            Text(
                text = uiState.guestCodeHelperText,
                style = EvenUpTheme.typography.bodySmall,
                color = EvenUpTheme.colors.textSecondary,
            )
        }
        GuestCodeChip(uiState = uiState, onEvent = onEvent)
    }
}

@Composable
private fun GuestCodeChip(
    uiState: ExpenseSavedUiState,
    onEvent: (ExpenseSavedUiEvent) -> Unit,
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .clickable(enabled = uiState.canCopyCode) { onEvent(ExpenseSavedUiEvent.CopyCodeClick) }
            .semantics {
                role = Role.Button
                contentDescription = uiState.guestCodeCopyContentDescription
            },
        shape = EvenUpTheme.shapes.chip,
        color = EvenUpTheme.colors.surfaceElevated,
        contentColor = if (uiState.canCopyCode) EvenUpTheme.colors.textPrimary else EvenUpTheme.colors.textTertiary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = EvenUpTheme.spacing.space12,
                vertical = EvenUpTheme.spacing.space8,
            ),
            horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = uiState.normalizedGuestCode.ifBlank { "Not available" },
                style = EvenUpTheme.typography.button,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.canCopyCode) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = EvenUpTheme.colors.textSecondary,
                )
            }
        }
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
