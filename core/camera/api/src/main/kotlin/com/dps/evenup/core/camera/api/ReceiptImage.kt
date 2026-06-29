package com.dps.evenup.core.camera.api

import android.net.Uri
import java.io.File

data class ReceiptImage(
    val bytes: ByteArray,
    val mimeType: String,
    val source: ReceiptImageSource,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReceiptImage) return false
        return bytes.contentEquals(other.bytes) &&
            mimeType == other.mimeType &&
            source == other.source
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }
}

enum class ReceiptImageSource {
    Camera,
    Gallery,
}

data class ReceiptCaptureTarget(
    val file: File,
    val uri: Uri,
)

interface ReceiptCaptureTargetFactory {
    fun createTarget(): ReceiptCaptureTarget
}

interface ReceiptImageReader {
    fun readImage(
        uri: Uri,
        source: ReceiptImageSource,
    ): ReceiptImage
}

class ReceiptImageReadException(
    message: String,
    val reason: ReceiptImageReadFailureReason,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

enum class ReceiptImageReadFailureReason {
    CannotOpenImage,
    UnsupportedImage,
    ImageTooLarge,
}
