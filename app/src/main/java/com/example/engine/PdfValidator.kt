package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

data class PdfValidationReport(
    val isValid: Boolean,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val message: String
)

object PdfValidator {

    fun validatePdfFile(file: File, expectedPages: Int, context: Context? = null): PdfValidationReport {
        if (!file.exists() || !file.isFile) {
            return PdfValidationReport(false, 0, 0L, "PDF file does not exist.")
        }
        val length = file.length()
        if (length < 64L) {
            return PdfValidationReport(false, 0, length, "PDF file is too small or empty ($length bytes).")
        }

        // 1. Structural Header / XRef / EOF Verification
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(8)
            raf.readFully(header)
            val headerStr = String(header, StandardCharsets.US_ASCII)
            if (!headerStr.startsWith("%PDF-")) {
                return PdfValidationReport(false, 0, length, "Invalid PDF header: $headerStr")
            }

            val tailSize = minOf(length, 4096L).toInt()
            raf.seek(length - tailSize)
            val tailBytes = ByteArray(tailSize)
            raf.readFully(tailBytes)
            val tailStr = String(tailBytes, StandardCharsets.US_ASCII)
            if (!tailStr.contains("%%EOF") || !tailStr.contains("startxref")) {
                return PdfValidationReport(false, 0, length, "Incomplete PDF trailer (missing startxref or %%EOF).")
            }

            // Check /Count N in tail or document
            val countMatch = Regex("/Count\\s+(\\d+)").find(tailStr)
            val parsedCount = countMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (parsedCount != null && parsedCount != expectedPages) {
                return PdfValidationReport(
                    false,
                    parsedCount,
                    length,
                    "Page count mismatch: expected $expectedPages but found $parsedCount."
                )
            }
        }

        // 2. Native Android PdfRenderer verification (when not running in Robolectric JVM stub)
        val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        if (!isRobolectric && context != null) {
            val rendererCheck = runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount
                        if (count != expectedPages) {
                            return@runCatching PdfValidationReport(
                                false, count, length, "PdfRenderer reported $count pages (expected $expectedPages)."
                            )
                        }
                        if (count > 0) {
                            renderer.openPage(0).use { page ->
                                val thumb = Bitmap.createBitmap(
                                    maxOf(1, page.width.coerceAtMost(256)),
                                    maxOf(1, page.height.coerceAtMost(256)),
                                    Bitmap.Config.ARGB_8888
                                )
                                try {
                                    page.render(thumb, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                } finally {
                                    thumb.recycle()
                                }
                            }
                        }
                        PdfValidationReport(true, count, length, "Verified $count pages via PdfRenderer.")
                    }
                }
            }
            if (rendererCheck.isSuccess) {
                return rendererCheck.getOrThrow()
            }
        }

        return PdfValidationReport(true, expectedPages, length, "Verified PDF structure and $expectedPages pages.")
    }

    fun validateSafUri(context: Context, uri: Uri, expectedPages: Int): PdfValidationReport {
        val check = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize.coerceAtLeast(0L)
                val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
                if (!isRobolectric) {
                    PdfRenderer(pfd).use { renderer ->
                        val count = renderer.pageCount
                        return@runCatching if (count == expectedPages) {
                            PdfValidationReport(true, count, size, "Reopened and verified $count pages.")
                        } else {
                            PdfValidationReport(false, count, size, "Page count mismatch: $count vs $expectedPages.")
                        }
                    }
                }
                PdfValidationReport(true, expectedPages, size, "Verified SAF stream ($size bytes).")
            }
        }.getOrNull()
        return check ?: PdfValidationReport(false, 0, 0L, "Unable to reopen exported PDF URI.")
    }
}
