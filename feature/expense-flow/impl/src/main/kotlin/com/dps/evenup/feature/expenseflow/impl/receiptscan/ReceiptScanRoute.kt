package com.dps.evenup.feature.expenseflow.impl.receiptscan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dps.evenup.core.camera.api.ReceiptCaptureTargetFactory
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.camera.api.ReceiptImageSource
import com.dps.evenup.data.expense.api.ExpenseDraftRepository
import com.dps.evenup.data.receipt.api.ReceiptDataException
import com.dps.evenup.data.receipt.api.ReceiptDataFailureReason
import com.dps.evenup.data.receipt.api.ReceiptRepository
import com.dps.evenup.domain.receipt.api.NormalizeReceiptUseCase
import com.dps.evenup.domain.receipt.api.ValidateReceiptUseCase
import kotlinx.coroutines.launch

@Composable
fun ReceiptScanRoute(
    draftRepository: ExpenseDraftRepository,
    receiptRepository: ReceiptRepository,
    receiptImageReader: ReceiptImageReader,
    receiptCaptureTargetFactory: ReceiptCaptureTargetFactory,
    normalizeReceipt: NormalizeReceiptUseCase,
    validateReceipt: ValidateReceiptUseCase,
    onBack: () -> Boolean,
    onManualEntry: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val presenter = remember(draftRepository, receiptRepository, receiptImageReader, normalizeReceipt, validateReceipt) {
        ReceiptScanPresenter(
            draftRepository = draftRepository,
            receiptRepository = receiptRepository,
            imageReader = receiptImageReader,
            normalizeReceipt = normalizeReceipt,
            validateReceipt = validateReceipt,
        )
    }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    var uiState by remember { mutableStateOf(ReceiptScanUiState()) }

    DisposableEffect(cameraController, lifecycleOwner, uiState.cameraPermissionGranted) {
        if (uiState.cameraPermissionGranted) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }
        onDispose { cameraController.unbind() }
    }

    fun parseImage(uri: Uri, source: ReceiptImageSource) {
        coroutineScope.launch {
            uiState = uiState.copy(isParsing = true, errorMessage = null)
            uiState = try {
                when (val result = presenter.parseImage(uri = uri, source = source)) {
                    ReceiptScanParseResult.Saved -> {
                        onContinue()
                        uiState.copy(isParsing = false)
                    }
                    is ReceiptScanParseResult.Invalid -> uiState.copy(
                        isParsing = false,
                        errorMessage = result.message,
                    )
                }
            } catch (error: ReceiptDataException) {
                uiState.copy(
                    isParsing = false,
                    errorMessage = error.toUserMessage(),
                )
            } catch (_: RuntimeException) {
                uiState.copy(
                    isParsing = false,
                    errorMessage = "We couldn't read this receipt. Try again or enter it manually.",
                )
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            parseImage(uri = uri, source = ReceiptImageSource.Gallery)
        }
    }

    fun captureReceipt() {
        val target = receiptCaptureTargetFactory.createTarget()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(target.file).build()
        cameraController.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    parseImage(uri = target.uri, source = ReceiptImageSource.Camera)
                }

                override fun onError(exception: ImageCaptureException) {
                    uiState = uiState.copy(
                        isParsing = false,
                        errorMessage = "Could not capture the receipt. Try again or choose from gallery.",
                    )
                }
            },
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            uiState = uiState.copy(
                cameraPermissionGranted = true,
                errorMessage = null,
            )
        } else {
            uiState = uiState.copy(
                cameraPermissionGranted = false,
                errorMessage = "Camera permission is needed to scan a receipt.",
            )
        }
    }

    LaunchedEffect(cameraPermissionLauncher) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            uiState = uiState.copy(cameraPermissionGranted = true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ReceiptScanScreen(
        uiState = uiState,
        cameraController = cameraController,
        onEvent = { event ->
            when (event) {
                ReceiptScanUiEvent.BackClick -> onBack()
                ReceiptScanUiEvent.CaptureClick -> {
                    if (uiState.cameraPermissionGranted) {
                        captureReceipt()
                    }
                }
                ReceiptScanUiEvent.GalleryClick -> {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
                ReceiptScanUiEvent.TryAgainClick -> uiState = uiState.copy(errorMessage = null)
                ReceiptScanUiEvent.ManualFallbackClick -> onManualEntry()
            }
        },
        modifier = modifier,
    )
}

private fun ReceiptDataException.toUserMessage(): String = when (reason) {
    ReceiptDataFailureReason.Connection -> "No internet connection. Check your connection and try again."
    ReceiptDataFailureReason.Timeout -> "Receipt parsing took too long. Try again with a clearer image."
    ReceiptDataFailureReason.ParseRejected -> "The image was too blurry or the text was hard to read. Try another photo."
    ReceiptDataFailureReason.Unknown -> "We couldn't read this receipt. Try again or enter it manually."
}
