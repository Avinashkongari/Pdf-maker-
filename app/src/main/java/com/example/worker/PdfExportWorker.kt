package com.example.worker

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.AppDatabase
import com.example.data.ExportHistoryEntity
import com.example.data.ExportHistoryRepository
import com.example.data.SettingsDataStore
import com.example.engine.AndroidImageInputProvider
import com.example.engine.InsufficientStorageException
import com.example.engine.OnDeviceOcrEngine
import com.example.engine.PdfValidator
import com.example.engine.StreamingPdfGenerator
import com.example.model.ExportStatus
import com.example.model.ImportedImage
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.model.PdfExportConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream

class PdfExportWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val payloadFilePath = inputData.getString(KEY_PAYLOAD_FILE)
            ?: return@withContext failureData("Missing export payload.")
        val targetUriString = inputData.getString(KEY_TARGET_URI)
            ?: return@withContext failureData("Missing destination PDF URI.")

        val payloadFile = File(payloadFilePath)
        if (!payloadFile.exists()) {
            return@withContext failureData("Export job payload expired.")
        }

        val payload = runCatching {
            json.decodeFromString<ExportJobPayload>(payloadFile.readText())
        }.getOrNull() ?: return@withContext failureData("Corrupted export job payload.")

        val settingsStore = SettingsDataStore(applicationContext)
        val stagingDir = File(applicationContext.cacheDir, "export_staging").apply { mkdirs() }
        val stagingPdf = File(stagingDir, "staging_${id}.pdf")
        val targetUri = Uri.parse(targetUriString)

        settingsStore.markExportStarted(payload.config.outputFileName)

        try {
            val images = payload.images
            val totalPages = images.size
            val ocrEnabled = payload.ocrSettings.enabled

            // 1. Optional On-Device OCR Phase (only runs when ENABLE OCR == ON; never alters images/PDF)
            var ocrResults: List<OcrPageResult> = emptyList()
            if (ocrEnabled) {
                val ocrEngine = OnDeviceOcrEngine(applicationContext)
                ocrResults = ocrEngine.recognizePages(
                    images = images,
                    ocrSettings = payload.ocrSettings,
                    pageNumberConfig = payload.config.pageNumberConfig,
                    isCancelled = { isStopped },
                    onProgress = { page, total ->
                        val pct = ((page.toFloat() / total) * 28f).toInt().coerceIn(0, 28)
                        setProgress(
                            progressData(
                                status = ExportStatus.RUNNING,
                                currentPage = page,
                                totalPages = total,
                                progressPct = pct,
                                phase = "Running Offline OCR ($page/$total)...",
                                ocrPagesDone = page
                            )
                        )
                    }
                )
                settingsStore.saveOcrResults(ocrResults)
            }

            if (isStopped) throw CancellationException("Export cancelled.")

            // 2. Streaming Lossless PDF Generation Phase
            val pdfBasePct = if (ocrEnabled) 30f else 0f
            val pdfSpanPct = if (ocrEnabled) 58f else 88f

            val generator = StreamingPdfGenerator(AndroidImageInputProvider(applicationContext))
            val result = generator.generatePdf(
                images = images,
                config = payload.config,
                outputFile = stagingPdf,
                isCancelled = { isStopped },
                onProgress = { page, total, phase ->
                    val pct = (pdfBasePct + (page.toFloat() / total) * pdfSpanPct).toInt().coerceIn(1, 88)
                    setProgress(
                        progressData(
                            status = ExportStatus.RUNNING,
                            currentPage = page,
                            totalPages = total,
                            progressPct = pct,
                            phase = phase,
                            ocrPagesDone = ocrResults.size
                        )
                    )
                }
            )

            if (isStopped) throw CancellationException("Export cancelled.")

            // 3. Integrity Validation Phase (Header, XRef, %%EOF, Page Count, PdfRenderer check)
            setProgress(
                progressData(
                    status = ExportStatus.VALIDATING,
                    currentPage = totalPages,
                    totalPages = totalPages,
                    progressPct = 92,
                    phase = "Validating PDF integrity ($totalPages pages)...",
                    ocrPagesDone = ocrResults.size
                )
            )
            val validation = PdfValidator.validatePdfFile(
                file = result.outputFile,
                expectedPages = totalPages,
                context = applicationContext
            )
            if (!validation.isValid) {
                throw IllegalStateException(validation.message)
            }

            if (isStopped) throw CancellationException("Export cancelled.")

            // 4. Write validated PDF bytes to SAF destination URI
            setProgress(
                progressData(
                    status = ExportStatus.VALIDATING,
                    currentPage = totalPages,
                    totalPages = totalPages,
                    progressPct = 96,
                    phase = "Saving validated PDF to chosen folder...",
                    ocrPagesDone = ocrResults.size
                )
            )

            applicationContext.contentResolver.openOutputStream(targetUri, "w")?.use { out ->
                FileInputStream(stagingPdf).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
                out.flush()
            } ?: throw IllegalStateException("Could not open destination file for writing.")

            // Verify SAF file can be reopened cleanly
            val safCheck = PdfValidator.validateSafUri(applicationContext, targetUri, totalPages)
            if (!safCheck.isValid) {
                runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, targetUri) }
                throw IllegalStateException("Exported PDF verification failed: ${safCheck.message}")
            }

            // 5. Record in local Room Export History
            val repo = ExportHistoryRepository(AppDatabase.getInstance(applicationContext).exportHistoryDao())
            repo.recordExport(
                ExportHistoryEntity(
                    fileName = payload.config.outputFileName,
                    outputUriString = targetUriString,
                    pageCount = totalPages,
                    fileSizeBytes = result.fileSizeBytes,
                    imageModeName = payload.config.imageHandlingMode.title,
                    pageSizeName = payload.config.pageSizePreset.label,
                    ocrEnabled = ocrEnabled,
                    ocrExtractedCharCount = ocrResults.sumOf { it.extractedText.length }
                )
            )

            val output = workDataOf(
                KEY_STATUS to ExportStatus.COMPLETED.name,
                KEY_PROGRESS_PCT to 100,
                KEY_CURRENT_PAGE to totalPages,
                KEY_TOTAL_PAGES to totalPages,
                KEY_PHASE to "Export complete & verified",
                KEY_TARGET_URI to targetUriString,
                KEY_FILE_NAME to payload.config.outputFileName,
                KEY_FILE_SIZE to result.fileSizeBytes,
                KEY_OCR_PAGES_DONE to ocrResults.size,
                KEY_VALIDATION_MSG to safCheck.message
            )
            Result.success(output)
        } catch (ce: CancellationException) {
            // Ensure no corrupted partial SAF file remains if cancelled
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, targetUri) }
            Result.failure(
                workDataOf(
                    KEY_STATUS to ExportStatus.CANCELLED.name,
                    KEY_ERROR_MSG to "Export was cancelled. No incomplete PDF was saved."
                )
            )
        } catch (ise: InsufficientStorageException) {
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, targetUri) }
            Result.failure(
                workDataOf(
                    KEY_STATUS to ExportStatus.FAILED.name,
                    KEY_ERROR_MSG to (ise.message ?: "Insufficient device storage to finish export.")
                )
            )
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, targetUri) }
            Result.failure(
                workDataOf(
                    KEY_STATUS to ExportStatus.FAILED.name,
                    KEY_ERROR_MSG to (t.message ?: "PDF export failed unexpectedly.")
                )
            )
        } finally {
            runCatching { if (stagingPdf.exists()) stagingPdf.delete() }
            runCatching { if (payloadFile.exists()) payloadFile.delete() }
            settingsStore.markExportFinished()
        }
    }

    private fun progressData(
        status: ExportStatus,
        currentPage: Int,
        totalPages: Int,
        progressPct: Int,
        phase: String,
        ocrPagesDone: Int
    ): Data = workDataOf(
        KEY_STATUS to status.name,
        KEY_CURRENT_PAGE to currentPage,
        KEY_TOTAL_PAGES to totalPages,
        KEY_PROGRESS_PCT to progressPct,
        KEY_PHASE to phase,
        KEY_OCR_PAGES_DONE to ocrPagesDone
    )

    private fun failureData(message: String): Result = Result.failure(
        workDataOf(
            KEY_STATUS to ExportStatus.FAILED.name,
            KEY_ERROR_MSG to message
        )
    )

    companion object {
        const val KEY_PAYLOAD_FILE = "payload_file"
        const val KEY_TARGET_URI = "target_uri"
        const val KEY_STATUS = "status"
        const val KEY_CURRENT_PAGE = "current_page"
        const val KEY_TOTAL_PAGES = "total_pages"
        const val KEY_PROGRESS_PCT = "progress_pct"
        const val KEY_PHASE = "phase"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_OCR_PAGES_DONE = "ocr_pages_done"
        const val KEY_VALIDATION_MSG = "validation_msg"
        const val KEY_ERROR_MSG = "error_msg"
    }
}

@kotlinx.serialization.Serializable
data class ExportJobPayload(
    val images: List<ImportedImage>,
    val config: PdfExportConfig,
    val ocrSettings: OcrSettings
)
