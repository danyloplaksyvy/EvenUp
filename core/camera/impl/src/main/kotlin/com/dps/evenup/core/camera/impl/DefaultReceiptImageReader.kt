package com.dps.evenup.core.camera.impl

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.dps.evenup.core.camera.api.ReceiptImage
import com.dps.evenup.core.camera.api.ReceiptImageReadException
import com.dps.evenup.core.camera.api.ReceiptImageReadFailureReason
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.camera.api.ReceiptImageSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class DefaultReceiptImageReader(
    private val contentResolver: ContentResolver,
    private val maxImageEdgePx: Int = DEFAULT_MAX_IMAGE_EDGE_PX,
    private val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    private val maxImageBytes: Int = MAX_RECEIPT_IMAGE_BYTES,
) : ReceiptImageReader {
    override fun readImage(
        uri: Uri,
        source: ReceiptImageSource,
    ): ReceiptImage {
        val originalBytes = readImageBytes(uri)
        val decodedBitmap = decodeBitmap(originalBytes)
        val orientedBitmap = decodedBitmap.withExifOrientation(originalBytes)
        val resizedBitmap = orientedBitmap.resizeToMaxEdge(maxImageEdgePx)
        val normalizedBytes = resizedBitmap.compressJpegUnderLimit()

        return ReceiptImage(
            bytes = normalizedBytes,
            mimeType = JPEG_MIME_TYPE,
            source = source,
        )
    }

    private fun readImageBytes(uri: Uri): ByteArray {
        val bytes = try {
            when (uri.scheme) {
                ContentResolver.SCHEME_FILE -> File(requireNotNull(uri.path)).readBytes()
                else -> contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: throw IllegalArgumentException("Could not open receipt image.")
            }
        } catch (error: Exception) {
            throw ReceiptImageReadException(
                message = "Could not open receipt image.",
                reason = ReceiptImageReadFailureReason.CannotOpenImage,
                cause = error,
            )
        }

        if (bytes.isEmpty()) {
            throw ReceiptImageReadException(
                message = "Receipt image is empty.",
                reason = ReceiptImageReadFailureReason.CannotOpenImage,
            )
        }

        return bytes
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ReceiptImageReadException(
                message = "Receipt image format is not supported.",
                reason = ReceiptImageReadFailureReason.UnsupportedImage,
            )
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxEdge = maxImageEdgePx,
            )
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw ReceiptImageReadException(
                message = "Receipt image could not be decoded.",
                reason = ReceiptImageReadFailureReason.UnsupportedImage,
            )
    }

    private fun Bitmap.withExifOrientation(bytes: ByteArray): Bitmap {
        val orientation = readExifOrientation(bytes)
        val matrix = Matrix()
        var transformed = true

        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> transformed = false
        }

        if (!transformed) return this

        return try {
            Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        } catch (error: RuntimeException) {
            throw ReceiptImageReadException(
                message = "Receipt image orientation could not be applied.",
                reason = ReceiptImageReadFailureReason.UnsupportedImage,
                cause = error,
            )
        }
    }

    private fun Bitmap.resizeToMaxEdge(maxEdge: Int): Bitmap {
        val currentMaxEdge = max(width, height)
        if (currentMaxEdge <= maxEdge) return this

        val scale = maxEdge.toFloat() / currentMaxEdge.toFloat()
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return try {
            Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        } catch (error: RuntimeException) {
            throw ReceiptImageReadException(
                message = "Receipt image could not be resized.",
                reason = ReceiptImageReadFailureReason.UnsupportedImage,
                cause = error,
            )
        }
    }

    private fun Bitmap.compressJpegUnderLimit(): ByteArray {
        var edgeLimit = maxImageEdgePx
        while (edgeLimit >= MIN_IMAGE_EDGE_PX) {
            val candidate = resizeToMaxEdge(edgeLimit)
            for (quality in jpegQualitySteps()) {
                val bytes = candidate.compressJpeg(quality)
                if (bytes.size <= maxImageBytes) return bytes
            }
            edgeLimit = (edgeLimit * IMAGE_EDGE_RETRY_SCALE).roundToInt()
        }

        throw ReceiptImageReadException(
            message = "Receipt image is too large after compression.",
            reason = ReceiptImageReadFailureReason.ImageTooLarge,
        )
    }

    private fun Bitmap.compressJpeg(quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val compressed = compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (!compressed) {
            throw ReceiptImageReadException(
                message = "Receipt image could not be compressed.",
                reason = ReceiptImageReadFailureReason.UnsupportedImage,
            )
        }
        return output.toByteArray()
    }

    private fun jpegQualitySteps(): List<Int> = listOf(
        jpegQuality,
        85,
        80,
        75,
    ).map { quality -> quality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY) }
        .distinct()

    private fun readExifOrientation(bytes: ByteArray): Int {
        return try {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxEdge: Int,
    ): Int {
        var sampleSize = 1
        while (max(width, height) / sampleSize > maxEdge) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
        const val DEFAULT_MAX_IMAGE_EDGE_PX = 2048
        const val MIN_IMAGE_EDGE_PX = 1024
        const val DEFAULT_JPEG_QUALITY = 90
        const val MIN_JPEG_QUALITY = 60
        const val MAX_JPEG_QUALITY = 100
        const val MAX_RECEIPT_IMAGE_BYTES = 7 * 1024 * 1024
        const val IMAGE_EDGE_RETRY_SCALE = 0.75f
    }
}
