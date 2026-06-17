package com.dps.evenup.core.camera.impl

import android.content.ContentResolver
import android.net.Uri
import com.dps.evenup.core.camera.api.ReceiptImage
import com.dps.evenup.core.camera.api.ReceiptImageReader
import com.dps.evenup.core.camera.api.ReceiptImageSource
import java.io.File

class DefaultReceiptImageReader(
    private val contentResolver: ContentResolver,
) : ReceiptImageReader {
    override fun readImage(
        uri: Uri,
        source: ReceiptImageSource,
    ): ReceiptImage {
        val bytes = when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> File(requireNotNull(uri.path)).readBytes()
            else -> contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                ?: throw IllegalArgumentException("Could not open receipt image.")
        }
        require(bytes.isNotEmpty()) { "Receipt image is empty." }

        return ReceiptImage(
            bytes = bytes,
            mimeType = contentResolver.getType(uri) ?: DEFAULT_MIME_TYPE,
            source = source,
        )
    }

    private companion object {
        const val DEFAULT_MIME_TYPE = "image/jpeg"
    }
}
