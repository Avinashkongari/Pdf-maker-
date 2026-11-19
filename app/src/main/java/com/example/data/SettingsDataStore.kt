package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ImportedImage
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.model.PdfExportConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.manhwaDataStore: DataStore<Preferences> by preferencesDataStore(name = "manhwa_pdf_prefs")

class SettingsDataStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val PDF_CONFIG_JSON = stringPreferencesKey("pdf_config_json")
        val OCR_SETTINGS_JSON = stringPreferencesKey("ocr_settings_json")
        val DRAFT_IMAGES_JSON = stringPreferencesKey("draft_images_json")
        val OCR_RESULTS_JSON = stringPreferencesKey("ocr_results_json")
        val ACTIVE_EXPORT_IN_PROGRESS = booleanPreferencesKey("active_export_in_progress")
        val ACTIVE_EXPORT_FILENAME = stringPreferencesKey("active_export_filename")
    }

    val pdfConfigFlow: Flow<PdfExportConfig> = context.manhwaDataStore.data.map { prefs ->
        prefs[Keys.PDF_CONFIG_JSON]?.let { raw ->
            runCatching { json.decodeFromString<PdfExportConfig>(raw) }.getOrNull()
        } ?: PdfExportConfig()
    }

    val ocrSettingsFlow: Flow<OcrSettings> = context.manhwaDataStore.data.map { prefs ->
        prefs[Keys.OCR_SETTINGS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<OcrSettings>(raw) }.getOrNull()
        } ?: OcrSettings()
    }

    val draftImagesFlow: Flow<List<ImportedImage>> = context.manhwaDataStore.data.map { prefs ->
        prefs[Keys.DRAFT_IMAGES_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<ImportedImage>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val ocrResultsFlow: Flow<List<OcrPageResult>> = context.manhwaDataStore.data.map { prefs ->
        prefs[Keys.OCR_RESULTS_JSON]?.let { raw ->
            runCatching { json.decodeFromString<List<OcrPageResult>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val interruptedExportFlow: Flow<Pair<Boolean, String>> = context.manhwaDataStore.data.map { prefs ->
        val inProgress = prefs[Keys.ACTIVE_EXPORT_IN_PROGRESS] ?: false
        val fileName = prefs[Keys.ACTIVE_EXPORT_FILENAME] ?: ""
        inProgress to fileName
    }

    suspend fun savePdfConfig(config: PdfExportConfig) {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.PDF_CONFIG_JSON] = json.encodeToString(config)
        }
    }

    suspend fun saveOcrSettings(settings: OcrSettings) {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.OCR_SETTINGS_JSON] = json.encodeToString(settings)
        }
    }

    suspend fun saveDraftImages(images: List<ImportedImage>) {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.DRAFT_IMAGES_JSON] = json.encodeToString(images)
        }
    }

    suspend fun saveOcrResults(results: List<OcrPageResult>) {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.OCR_RESULTS_JSON] = json.encodeToString(results)
        }
    }

    suspend fun markExportStarted(fileName: String) {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_EXPORT_IN_PROGRESS] = true
            prefs[Keys.ACTIVE_EXPORT_FILENAME] = fileName
        }
    }

    suspend fun markExportFinished() {
        context.manhwaDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_EXPORT_IN_PROGRESS] = false
            prefs[Keys.ACTIVE_EXPORT_FILENAME] = ""
        }
    }
}
