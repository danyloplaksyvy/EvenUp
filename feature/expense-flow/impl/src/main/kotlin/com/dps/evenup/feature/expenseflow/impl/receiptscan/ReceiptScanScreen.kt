package com.dps.evenup.feature.expenseflow.impl.receiptscan

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dps.evenup.core.designsystem.api.EvenUpCard
import com.dps.evenup.core.designsystem.api.EvenUpPrimaryButton
import com.dps.evenup.core.designsystem.api.EvenUpTextButton
import com.dps.evenup.core.designsystem.api.EvenUpTheme
import com.dps.evenup.core.designsystem.api.EvenUpTopBar
import com.dps.evenup.core.designsystem.api.EvenUpValidationMessage

@Composable
fun ReceiptScanScreen(
    uiState: ReceiptScanUiState,
    cameraController: LifecycleCameraController,
    onEvent: (ReceiptScanUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EvenUpTheme.colors.background),
    ) {
        EvenUpTopBar(
            title = "Scan receipt",
            onNavigationClick = { onEvent(ReceiptScanUiEvent.BackClick) },
            navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            ReceiptCameraPreview(
                cameraController = cameraController,
                modifier = Modifier.fillMaxSize(),
            )
            ReceiptFrameOverlay(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = EvenUpTheme.spacing.space24),
            )
            uiState.errorMessage?.let { message ->
                ReceiptScanErrorOverlay(
                    message = message,
                    onTryAgain = { onEvent(ReceiptScanUiEvent.TryAgainClick) },
                    onManualFallback = { onEvent(ReceiptScanUiEvent.ManualFallbackClick) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(EvenUpTheme.spacing.space16),
                )
            }
            if (uiState.isParsing) {
                ReceiptScanLoadingOverlay(modifier = Modifier.fillMaxSize())
            }
        }
        ReceiptScanActions(
            captureEnabled = uiState.captureEnabled,
            galleryEnabled = uiState.galleryEnabled,
            onCapture = { onEvent(ReceiptScanUiEvent.CaptureClick) },
            onGallery = { onEvent(ReceiptScanUiEvent.GalleryClick) },
        )
    }
}

@Composable
private fun ReceiptCameraPreview(
    cameraController: LifecycleCameraController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.background(EvenUpTheme.colors.primary),
        factory = { context ->
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        },
    )
}

@Composable
private fun ReceiptFrameOverlay(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .border(
                    border = BorderStroke(2.dp, EvenUpTheme.colors.onPrimary),
                    shape = EvenUpTheme.shapes.screenCard,
                ),
        )
        Surface(
            color = EvenUpTheme.colors.primary.copy(alpha = 0.72f),
            contentColor = EvenUpTheme.colors.onPrimary,
            shape = EvenUpTheme.shapes.input,
        ) {
            Text(
                text = "Align receipt within the frame",
                modifier = Modifier.padding(
                    horizontal = EvenUpTheme.spacing.space16,
                    vertical = EvenUpTheme.spacing.space8,
                ),
                style = EvenUpTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReceiptScanActions(
    captureEnabled: Boolean,
    galleryEnabled: Boolean,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = EvenUpTheme.colors.background,
        contentColor = EvenUpTheme.colors.textPrimary,
        border = BorderStroke(1.dp, EvenUpTheme.colors.divider),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(EvenUpTheme.spacing.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space12),
        ) {
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(enabled = captureEnabled, onClick = onCapture),
                shape = CircleShape,
                color = if (captureEnabled) EvenUpTheme.colors.primary else EvenUpTheme.colors.surface,
                contentColor = if (captureEnabled) EvenUpTheme.colors.onPrimary else EvenUpTheme.colors.textTertiary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture receipt",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = onGallery,
                enabled = galleryEnabled,
                modifier = Modifier.fillMaxWidth(),
                shape = EvenUpTheme.shapes.button,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = EvenUpTheme.colors.surfaceElevated,
                    contentColor = EvenUpTheme.colors.textPrimary,
                    disabledContainerColor = EvenUpTheme.colors.surface,
                    disabledContentColor = EvenUpTheme.colors.textTertiary,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = "Choose from gallery", style = EvenUpTheme.typography.button)
                }
            }
        }
    }
}

@Composable
private fun ReceiptScanLoadingOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(EvenUpTheme.colors.primary.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = EvenUpTheme.colors.background,
            contentColor = EvenUpTheme.colors.textPrimary,
            shape = EvenUpTheme.shapes.screenCard,
        ) {
            Column(
                modifier = Modifier.padding(EvenUpTheme.spacing.space24),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(EvenUpTheme.spacing.space16),
            ) {
                CircularProgressIndicator(color = EvenUpTheme.colors.primary)
                Text(
                    text = "Reading receipt...",
                    style = EvenUpTheme.typography.body,
                    color = EvenUpTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ReceiptScanErrorOverlay(
    message: String,
    onTryAgain: () -> Unit,
    onManualFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EvenUpCard(modifier = modifier) {
        Text(
            text = "We couldn't read this receipt",
            style = EvenUpTheme.typography.sectionTitle,
            color = EvenUpTheme.colors.textPrimary,
        )
        EvenUpValidationMessage(message = message)
        Spacer(modifier = Modifier.height(EvenUpTheme.spacing.space4))
        EvenUpPrimaryButton(text = "Try again", onClick = onTryAgain)
        EvenUpTextButton(
            text = "Enter manually",
            onClick = onManualFallback,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
