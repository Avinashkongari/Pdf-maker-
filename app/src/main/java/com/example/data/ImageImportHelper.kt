package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.model.ImportedImage
import com.example.model.OcrPageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageImportHelper(private val context: Context) {

    private val importedCacheDir: File
        get() = File(context.cacheDir, "imported_pages").apply { mkdirs() }

    private val stagingDir: File
        get() = File(context.cacheDir, "export_staging").apply { mkdirs() }

    suspend fun importUris(
        uris: List<Uri>,
        startingOrderIndex: Int
    ): List<ImportedImage> = withContext(Dispatchers.IO) {
        val result = ArrayList<ImportedImage>(uris.size)
        for ((offset, uri) in uris.withIndex()) {
            val imported = inspectSingleUri(uri, startingOrderIndex + offset)
            if (imported != null) {
                result.add(imported)
            }
        }
        result
    }

    private fun inspectSingleUri(uri: Uri, orderIndex: Int): ImportedImage? {
        val resolver = context.contentResolver
        // Try to persist read access if provider supports it (SAF documents & Android 14+ Photo Picker).
        val hasPersistableGrant = runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)

        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        var displayName = "Page_${orderIndex + 1}"
        var sizeBytes = 0L

        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        sizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
        }

        // If URI permission cannot be persisted across app restarts/WorkManager,
        // stream-copy raw original bytes untouched into app cache (no decoding/recompression).
        var cachedPath: String? = null
        if (!hasPersistableGrant) {
            val ext = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() } ?: "img"
            val target = File(importedCacheDir, "${UUID.randomUUID()}.$ext")
            val copied = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output ->
                        val total = input.copyTo(output, bufferSize = 32 * 1024)
                        if (sizeBytes <= 0L) sizeBytes = total
                    }
                    true
                } ?: false
            }.getOrDefault(false)
            if (copied && target.exists()) {
                cachedPath = target.absolutePath
            }
        }

        // Read dimensions using inJustDecodeBounds = true (ZERO pixel memory allocated!)
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            if (cachedPath != null) {
                BitmapFactory.decodeFile(cachedPath, boundsOpts)
            } else {
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
            }
        }
        val widthPx = boundsOpts.outWidth.coerceAtLeast(1)
        val heightPx = boundsOpts.outHeight.coerceAtLeast(1)

        // Read EXIF orientation cleanly
        val exifOrientation = runCatching {
            if (cachedPath != null) {
                ExifInterface(cachedPath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } else {
                resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        if (sizeBytes <= 0L) {
            sizeBytes = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
            }.getOrDefault(0L).coerceAtLeast(0L)
        }

        return ImportedImage(
            id = UUID.randomUUID().toString(),
            uriString = uri.toString(),
            cachedPath = cachedPath,
            displayName = displayName,
            mimeType = mimeType,
            widthPx = widthPx,
            heightPx = heightPx,
            fileSizeBytes = sizeBytes,
            exifOrientation = exifOrientation,
            manualRotationDegrees = 0,
            orderIndex = orderIndex
        )
    }

    fun deleteCachedCopyIfPresent(image: ImportedImage) {
        image.cachedPath?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    fun cleanupStaleStagingAndOrphanedCache(activeCachedPaths: Set<String>) {
        runCatching {
            stagingDir.listFiles()?.forEach { it.delete() }
            importedCacheDir.listFiles()?.forEach { file ->
                if (file.absolutePath !in activeCachedPaths) {
                    file.delete()
                }
            }
        }
    }

    suspend fun exportOcrTextToUri(uri: Uri, results: List<OcrPageResult>): Boolean = withContext(Dispatchers.IO) {
        val textContent = buildString {
            appendLine("=== MANHWA PDF MAKER — EXTRACTED OCR TEXT ===")
            appendLine("Pages scanned: ${results.size}")
            appendLine()
            for (page in results) {
                appendLine("----------------------------------------")
                appendLine("${page.pageLabel} | ${page.displayName} | [${page.languagesUsed.joinToString(", ")}]")
                appendLine("----------------------------------------")
                appendLine(page.extractedText.ifBlank { "(No text detected on this page)" })
                appendLine()
            }
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(textContent.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        }.getOrDefault(false)
    }
}
