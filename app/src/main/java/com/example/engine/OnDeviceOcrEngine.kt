package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.example.model.ImportedImage
import com.example.model.OcrLanguage
import com.example.model.OcrLanguageStatus
import com.example.model.OcrModelStatus
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.model.PageNumberConfig
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface OcrEngine {
    fun getLanguageStatuses(selected: Set<OcrLanguage>): List<OcrLanguageStatus>
    suspend fun recognizePages(
        images: List<ImportedImage>,
        ocrSettings: OcrSettings,
        pageNumberConfig: PageNumberConfig,
        isCancelled: () -> Boolean = { false },
        onProgress: suspend (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): List<OcrPageResult>
}

class OnDeviceOcrEngine(
    private val context: Context,
    private val inputProvider: ImageInputProvider = AndroidImageInputProvider(context)
) : OcrEngine {

    override fun getLanguageStatuses(selected: Set<OcrLanguage>): List<OcrLanguageStatus> {
        // Bundled ML Kit text-recognition artifacts include their offline models directly in the APK.
        return OcrLanguage.entries.map { lang ->
            OcrLanguageStatus(
                language = lang,
                status = OcrModelStatus.BUNDLED_READY,
                isSelected = lang in selected
            )
        }
    }

    override suspend fun recognizePages(
        images: List<ImportedImage>,
        ocrSettings: OcrSettings,
        pageNumberConfig: PageNumberConfig,
        isCancelled: () -> Boolean,
        onProgress: suspend (currentPage: Int, totalPages: Int) -> Unit
    ): List<OcrPageResult> = withContext(Dispatchers.Default) {
        if (!ocrSettings.enabled || images.isEmpty()) return@withContext emptyList()

        val languages = ocrSettings.selectedLanguages.ifEmpty { setOf(OcrLanguage.ENGLISH) }
        val recognizers = languages.associateWith { createRecognizer(it) }

        try {
            val results = ArrayList<OcrPageResult>(images.size)
            val total = images.size

            for ((index, image) in images.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (isCancelled()) throw CancellationException("OCR scan cancelled.")

                onProgress(index + 1, total)

                val pageText = recognizeSingleImage(image, recognizers)
                val pageNum = pageNumberConfig.startNumber + index
                val pageLabel = pageNumberConfig.formatLabel(index)

                results.add(
                    OcrPageResult(
                        pageNumber = pageNum,
                        pageLabel = pageLabel,
                        imageId = image.id,
                        displayName = image.displayName,
                        extractedText = pageText,
                        languagesUsed = languages.map { it.displayName }
                    )
                )
            }
            results
        } finally {
            recognizers.values.forEach { recognizer ->
                runCatching { recognizer.close() }
            }
        }
    }

    private suspend fun recognizeSingleImage(
        image: ImportedImage,
        recognizers: Map<OcrLanguage, TextRecognizer>
    ): String {
        val rawBitmap = decodeMemorySafeBitmapForOcr(image) ?: return ""
        val orientedBitmap = applyOrientationForOcr(rawBitmap, image.exifOrientation, image.manualRotationDegrees)
        if (orientedBitmap !== rawBitmap && !rawBitmap.isRecycled) {
            rawBitmap.recycle()
        }

        try {
            val width = orientedBitmap.width
            val height = orientedBitmap.height
            // Slice ultra-tall manhwa webtoon strips into overlapping 2200px tiles so ML Kit
            // retains sharp speech-bubble recognition without exceeding memory limits.
            val maxTileHeight = 2200
            val overlap = 120
            val accumulatedLines = LinkedHashSet<String>()

            if (height <= maxTileHeight) {
                runRecognizersOnBitmap(orientedBitmap, recognizers, accumulatedLines)
            } else {
                var y = 0
                while (y < height) {
                    val sliceH = minOf(maxTileHeight, height - y)
                    val tile = Bitmap.createBitmap(orientedBitmap, 0, y, width, sliceH)
                    try {
                        runRecognizersOnBitmap(tile, recognizers, accumulatedLines)
                    } finally {
                        if (!tile.isRecycled) tile.recycle()
                    }
                    if (y + sliceH >= height) break
                    y += (maxTileHeight - overlap)
                }
            }
            return accumulatedLines.joinToString("\n")
        } finally {
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
        }
    }

    private suspend fun runRecognizersOnBitmap(
        bitmap: Bitmap,
        recognizers: Map<OcrLanguage, TextRecognizer>,
        outLines: MutableSet<String>
    ) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        for ((_, recognizer) in recognizers) {
            val visionText = recognizer.process(inputImage).awaitTask()
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val cleaned = line.text.trim()
                    if (cleaned.isNotEmpty()) {
                        outLines.add(cleaned)
                    }
                }
            }
        }
    }

    private fun decodeMemorySafeBitmapForOcr(image: ImportedImage): Bitmap? {
        // Only downscale temporary OCR input if a single image exceeds 2560px width to keep 4GB devices safe;
        // the original image file used by PDF generation is NEVER touched.
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        inputProvider.openInputStream(image)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return null

        var sampleSize = 1
        while (rawW / sampleSize > 2560) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return inputProvider.openInputStream(image)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    private fun applyOrientationForOcr(
        bitmap: Bitmap,
        exifOrientation: Int,
        manualRotationDegrees: Int
    ): Bitmap {
        val matrix = Matrix()
        when (exifOrientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
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
        }
        val normalizedManual = ((manualRotationDegrees % 360) + 360) % 360
        if (normalizedManual != 0) {
            matrix.postRotate(normalizedManual.toFloat())
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun createRecognizer(language: OcrLanguage): TextRecognizer {
        return when (language) {
            OcrLanguage.ENGLISH -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrLanguage.HINDI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrLanguage.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrLanguage.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
    }

    private suspend fun Task<Text>.awaitTask(): Text = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
