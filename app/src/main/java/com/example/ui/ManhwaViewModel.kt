package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.AppDatabase
import com.example.data.ExportHistoryEntity
import com.example.data.ExportHistoryRepository
import com.example.data.ImageImportHelper
import com.example.data.SettingsDataStore
import com.example.engine.OnDeviceOcrEngine
import com.example.model.ExportProgressState
import com.example.model.ExportStatus
import com.example.model.ImportedImage
import com.example.model.OcrLanguageStatus
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.model.PdfExportConfig
import com.example.worker.ExportJobPayload
import com.example.worker.PdfExportWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ManhwaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val settingsStore = SettingsDataStore(context)
    private val importHelper = ImageImportHelper(context)
    private val ocrEngine = OnDeviceOcrEngine(context)
    private val historyRepo = ExportHistoryRepository(AppDatabase.getInstance(context).exportHistoryDao())
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun getWorkManager(): WorkManager {
        if (!WorkManager.isInitialized()) {
            runCatching {
                WorkManager.initialize(context, Configuration.Builder().build())
            }
        }
        return WorkManager.getInstance(context)
    }

    private val _images = MutableStateFlow<List<ImportedImage>>(emptyList())
    val images: StateFlow<List<ImportedImage>> = _images.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _pdfConfig = MutableStateFlow(PdfExportConfig())
    val pdfConfig: StateFlow<PdfExportConfig> = _pdfConfig.asStateFlow()

    private val _ocrSettings = MutableStateFlow(OcrSettings())
    val ocrSettings: StateFlow<OcrSettings> = _ocrSettings.asStateFlow()

    private val _ocrResults = MutableStateFlow<List<OcrPageResult>>(emptyList())
    val ocrResults: StateFlow<List<OcrPageResult>> = _ocrResults.asStateFlow()

    private val _ocrSearchQuery = MutableStateFlow("")
    val ocrSearchQuery: StateFlow<String> = _ocrSearchQuery.asStateFlow()

    val filteredOcrResults: StateFlow<List<OcrPageResult>> = combine(_ocrResults, _ocrSearchQuery) { results, q ->
        val trimmed = q.trim()
        if (trimmed.isEmpty()) results
        else results.filter {
            it.extractedText.contains(trimmed, ignoreCase = true) ||
                it.pageLabel.contains(trimmed, ignoreCase = true) ||
                it.displayName.contains(trimmed, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isStandaloneOcrRunning = MutableStateFlow(false)
    val isStandaloneOcrRunning: StateFlow<Boolean> = _isStandaloneOcrRunning.asStateFlow()

    private val _standaloneOcrProgress = MutableStateFlow(0 to 0)
    val standaloneOcrProgress: StateFlow<Pair<Int, Int>> = _standaloneOcrProgress.asStateFlow()

    private val _languageStatuses = MutableStateFlow<List<OcrLanguageStatus>>(
        ocrEngine.getLanguageStatuses(OcrSettings().selectedLanguages)
    )
    val languageStatuses: StateFlow<List<OcrLanguageStatus>> = _languageStatuses.asStateFlow()

    private val _exportState = MutableStateFlow(ExportProgressState())
    val exportState: StateFlow<ExportProgressState> = _exportState.asStateFlow()

    private val _interruptedExportBanner = MutableStateFlow<String?>(null)
    val interruptedExportBanner: StateFlow<String?> = _interruptedExportBanner.asStateFlow()

    val exportHistory: StateFlow<List<ExportHistoryEntity>> = historyRepo.allExports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activeWorkId: UUID? = null
    private var workObserverJob: Job? = null
    private var standaloneOcrJob: Job? = null

    init {
        viewModelScope.launch {
            val savedConfig = settingsStore.pdfConfigFlow.first()
            val savedOcr = settingsStore.ocrSettingsFlow.first()
            val savedDraft = settingsStore.draftImagesFlow.first()
            val savedOcrResults = settingsStore.ocrResultsFlow.first()
            val (wasActive, interruptedName) = settingsStore.interruptedExportFlow.first()

            _pdfConfig.value = savedConfig
            _ocrSettings.value = savedOcr
            _languageStatuses.value = ocrEngine.getLanguageStatuses(savedOcr.selectedLanguages)
            _images.value = savedDraft
            _ocrResults.value = savedOcrResults

            // Clean up orphaned staging files safely while preserving active draft cached pages
            val activePaths = savedDraft.mapNotNull { it.cachedPath }.toSet()
            importHelper.cleanupStaleStagingAndOrphanedCache(activePaths)

            // Reattach to running WorkManager job if app rotated/reopened, or handle interrupted crash
            val activeInfos = withContext(Dispatchers.IO) {
                runCatching { getWorkManager().getWorkInfosByTag(TAG_PDF_EXPORT).get() }.getOrDefault(emptyList())
            }
            val runningWork = activeInfos.firstOrNull { !it.state.isFinished }
            if (runningWork != null) {
                activeWorkId = runningWork.id
                observeExportWork(runningWork.id)
            } else if (wasActive) {
                settingsStore.markExportFinished()
                _interruptedExportBanner.value =
                    "Previous export '${interruptedName.ifBlank { "PDF" }}' was interrupted. Staging cache was cleaned safely—ready to retry."
            }
        }
    }

    fun dismissInterruptedBanner() {
        _interruptedExportBanner.value = null
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val current = _images.value
                val imported = importHelper.importUris(uris, startingOrderIndex = current.size)
                val updated = (current + imported).mapIndexed { idx, img -> img.copy(orderIndex = idx) }
                _images.value = updated
                settingsStore.saveDraftImages(updated)
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun removeImage(id: String) {
        viewModelScope.launch {
            val removed = _images.value.firstOrNull { it.id == id }
            if (removed != null) importHelper.deleteCachedCopyIfPresent(removed)
            val updated = _images.value
                .filterNot { it.id == id }
                .mapIndexed { idx, img -> img.copy(orderIndex = idx) }
            _images.value = updated
            settingsStore.saveDraftImages(updated)
        }
    }

    fun clearAllImages() {
        viewModelScope.launch {
            _images.value.forEach { importHelper.deleteCachedCopyIfPresent(it) }
            _images.value = emptyList()
            _ocrResults.value = emptyList()
            settingsStore.saveDraftImages(emptyList())
            settingsStore.saveOcrResults(emptyList())
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val current = _images.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        val reindexed = current.mapIndexed { idx, img -> img.copy(orderIndex = idx) }
        _images.value = reindexed
        viewModelScope.launch { settingsStore.saveDraftImages(reindexed) }
    }

    fun reverseImageOrder() {
        val reindexed = _images.value.reversed().mapIndexed { idx, img -> img.copy(orderIndex = idx) }
        _images.value = reindexed
        viewModelScope.launch { settingsStore.saveDraftImages(reindexed) }
    }

    fun sortImagesByFilename() {
        val sorted = _images.value
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
            .mapIndexed { idx, img -> img.copy(orderIndex = idx) }
        _images.value = sorted
        viewModelScope.launch { settingsStore.saveDraftImages(sorted) }
    }

    fun rotateImage(id: String) {
        val updated = _images.value.map { img ->
            if (img.id == id) {
                img.copy(manualRotationDegrees = (img.manualRotationDegrees + 90) % 360)
            } else img
        }
        _images.value = updated
        viewModelScope.launch { settingsStore.saveDraftImages(updated) }
    }

    fun updatePdfConfig(transform: (PdfExportConfig) -> PdfExportConfig) {
        val updated = transform(_pdfConfig.value)
        _pdfConfig.value = updated
        viewModelScope.launch { settingsStore.savePdfConfig(updated) }
    }

    fun updateOcrSettings(transform: (OcrSettings) -> OcrSettings) {
        val updated = transform(_ocrSettings.value)
        _ocrSettings.value = updated
        _languageStatuses.value = ocrEngine.getLanguageStatuses(updated.selectedLanguages)
        viewModelScope.launch { settingsStore.saveOcrSettings(updated) }
    }

    fun updateOcrSearchQuery(query: String) {
        _ocrSearchQuery.value = query
    }

    fun runStandaloneOcr() {
        val currentImages = _images.value
        val settings = _ocrSettings.value
        if (!settings.enabled || currentImages.isEmpty() || _isStandaloneOcrRunning.value) return

        standaloneOcrJob?.cancel()
        standaloneOcrJob = viewModelScope.launch {
            _isStandaloneOcrRunning.value = true
            _standaloneOcrProgress.value = 0 to currentImages.size
            try {
                val results = ocrEngine.recognizePages(
                    images = currentImages,
                    ocrSettings = settings,
                    pageNumberConfig = _pdfConfig.value.pageNumberConfig,
                    isCancelled = { false },
                    onProgress = { cur, total ->
                        _standaloneOcrProgress.value = cur to total
                    }
                )
                _ocrResults.value = results
                settingsStore.saveOcrResults(results)
            } catch (_: CancellationException) {
                // Safe cancellation
            } finally {
                _isStandaloneOcrRunning.value = false
            }
        }
    }

    fun cancelStandaloneOcr() {
        standaloneOcrJob?.cancel()
        _isStandaloneOcrRunning.value = false
    }

    fun exportOcrTextToUri(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = importHelper.exportOcrTextToUri(uri, _ocrResults.value)
            onComplete(ok)
        }
    }

    fun startPdfExport(targetUri: Uri) {
        val currentImages = _images.value
        if (currentImages.isEmpty()) return

        val config = _pdfConfig.value
        val ocr = _ocrSettings.value
        val stagingDir = File(context.cacheDir, "export_staging").apply { mkdirs() }
        val payloadFile = File(stagingDir, "payload_${UUID.randomUUID()}.json")
        payloadFile.writeText(json.encodeToString(ExportJobPayload(currentImages, config, ocr)))

        _exportState.value = ExportProgressState(
            status = ExportStatus.RUNNING,
            currentPage = 0,
            totalPages = currentImages.size,
            phaseLabel = if (ocr.enabled) "Preparing Offline OCR & Lossless PDF..." else "Preparing Lossless Streaming PDF...",
            progressFraction = 0.02f,
            outputUriString = targetUri.toString(),
            outputFileName = config.outputFileName
        )

        val request = OneTimeWorkRequestBuilder<PdfExportWorker>()
            .addTag(TAG_PDF_EXPORT)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                workDataOf(
                    PdfExportWorker.KEY_PAYLOAD_FILE to payloadFile.absolutePath,
                    PdfExportWorker.KEY_TARGET_URI to targetUri.toString()
                )
            )
            .build()

        activeWorkId = request.id
        getWorkManager().enqueue(request)
        observeExportWork(request.id)
    }

    private fun observeExportWork(workId: UUID) {
        workObserverJob?.cancel()
        workObserverJob = viewModelScope.launch {
            getWorkManager().getWorkInfoByIdFlow(workId).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                        val p = info.progress
                        val statusStr = p.getString(PdfExportWorker.KEY_STATUS)
                        val status = statusStr?.let { runCatching { ExportStatus.valueOf(it) }.getOrNull() }
                            ?: ExportStatus.RUNNING
                        val pct = p.getInt(PdfExportWorker.KEY_PROGRESS_PCT, 0)
                        val cur = p.getInt(PdfExportWorker.KEY_CURRENT_PAGE, _exportState.value.currentPage)
                        val tot = p.getInt(PdfExportWorker.KEY_TOTAL_PAGES, _images.value.size)
                        val phase = p.getString(PdfExportWorker.KEY_PHASE) ?: _exportState.value.phaseLabel
                        val ocrDone = p.getInt(PdfExportWorker.KEY_OCR_PAGES_DONE, 0)
                        _exportState.update {
                            it.copy(
                                status = status,
                                currentPage = cur,
                                totalPages = tot,
                                phaseLabel = phase,
                                progressFraction = (pct / 100f).coerceIn(0f, 1f),
                                ocrPagesCompleted = ocrDone
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val out = info.outputData
                        val latestOcr = settingsStore.ocrResultsFlow.first()
                        _ocrResults.value = latestOcr
                        _exportState.value = ExportProgressState(
                            status = ExportStatus.COMPLETED,
                            currentPage = out.getInt(PdfExportWorker.KEY_TOTAL_PAGES, _images.value.size),
                            totalPages = out.getInt(PdfExportWorker.KEY_TOTAL_PAGES, _images.value.size),
                            phaseLabel = out.getString(PdfExportWorker.KEY_PHASE) ?: "Export complete",
                            progressFraction = 1f,
                            outputUriString = out.getString(PdfExportWorker.KEY_TARGET_URI),
                            outputFileName = out.getString(PdfExportWorker.KEY_FILE_NAME) ?: _pdfConfig.value.outputFileName,
                            outputSizeBytes = out.getLong(PdfExportWorker.KEY_FILE_SIZE, 0L),
                            ocrPagesCompleted = out.getInt(PdfExportWorker.KEY_OCR_PAGES_DONE, 0),
                            validationSummary = out.getString(PdfExportWorker.KEY_VALIDATION_MSG)
                        )
                    }
                    WorkInfo.State.CANCELLED -> {
                        _exportState.update {
                            it.copy(
                                status = ExportStatus.CANCELLED,
                                phaseLabel = "Cancelled",
                                errorMessage = "Export was cancelled. No incomplete PDF was saved."
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val out = info.outputData
                        val statusStr = out.getString(PdfExportWorker.KEY_STATUS)
                        val status = statusStr?.let { runCatching { ExportStatus.valueOf(it) }.getOrNull() }
                            ?: ExportStatus.FAILED
                        _exportState.update {
                            it.copy(
                                status = status,
                                phaseLabel = if (status == ExportStatus.CANCELLED) "Cancelled" else "Export Failed",
                                errorMessage = out.getString(PdfExportWorker.KEY_ERROR_MSG)
                                    ?: "PDF export failed unexpectedly."
                            )
                        }
                    }
                }
            }
        }
    }

    fun cancelPdfExport() {
        activeWorkId?.let { getWorkManager().cancelWorkById(it) }
    }

    fun resetExportState() {
        _exportState.value = ExportProgressState()
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyRepo.deleteById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepo.clearAll() }
    }

    companion object {
        private const val TAG_PDF_EXPORT = "manhwa_pdf_export"
    }
}
