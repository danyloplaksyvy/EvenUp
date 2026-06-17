package com.dps.evenup.core.camera.impl

import android.content.Context
import android.net.Uri
import com.dps.evenup.core.camera.api.ReceiptCaptureTarget
import com.dps.evenup.core.camera.api.ReceiptCaptureTargetFactory
import java.io.File
import java.util.UUID

class DefaultReceiptCaptureTargetFactory(
    private val context: Context,
) : ReceiptCaptureTargetFactory {
    override fun createTarget(): ReceiptCaptureTarget {
        val directory = File(context.cacheDir, RECEIPT_DIRECTORY).apply {
            mkdirs()
        }
        val file = File(directory, "receipt-${UUID.randomUUID()}.jpg")
        return ReceiptCaptureTarget(
            file = file,
            uri = Uri.fromFile(file),
        )
    }

    private companion object {
        const val RECEIPT_DIRECTORY = "receipt-captures"
    }
}
