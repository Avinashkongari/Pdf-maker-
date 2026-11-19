package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ImportedImage(
    val id: String,
    val uriString: String,
    val cachedPath: String? = null,
    val displayName: String,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
    val exifOrientation: Int = 1,
    val manualRotationDegrees: Int = 0,
    val orderIndex: Int = 0
) {
    val effectiveDimensions: Pair<Int, Int>
        get() {
            val exifSwaps = exifOrientation == 5 || exifOrientation == 6 || exifOrientation == 7 || exifOrientation == 8
            val manualSwaps = (manualRotationDegrees % 180) != 0
            return if (exifSwaps xor manualSwaps) {
                heightPx to widthPx
            } else {
                widthPx to heightPx
            }
        }

    val formatLabel: String
        get() = when {
            mimeType.contains("png", ignoreCase = true) || displayName.endsWith(".png", true) -> "PNG"
            mimeType.contains("webp", ignoreCase = true) || displayName.endsWith(".webp", true) -> "WEBP"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) ||
                displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true) -> "JPG"
            else -> mimeType.substringAfter("/", "IMG").uppercase()
        }
}

@Serializable
enum class ImageHandlingMode(val title: String, val description: String) {
    ORIGINAL_QUALITY(
        title = "Original Quality",
        description = "Preserves exact pixel dimensions, lossless PNG/WEBP streams, and raw JPEG data without recompression."
    ),
    OPTIMIZED_LOSSLESS(
        title = "Optimized Lossless",
        description = "Mathematically identical pixels with maximum Deflate predictor & grayscale detection to trim PDF overhead."
    )
}

@Serializable
enum class PdfPageSizePreset(val label: String) {
    ORIGINAL_IMAGE_SIZE("Original Image Size"),
    A4_PORTRAIT("A4 Portrait"),
    A4_LANDSCAPE("A4 Landscape"),
    FIT_TO_PAGE("Fit to Page"),
    CUSTOM("Custom Dimensions")
}

@Serializable
enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

@Serializable
enum class PageBackground(val label: String, val colorArgb: Long) {
    WHITE("White", 0xFFFFFFFF),
    BLACK("Black", 0xFF0E0D12)
}

@Serializable
enum class PageNumberPrefix(val token: String) {
    PG("PG"),
    PAGE("PAGE"),
    CHAPTER("CHAPTER")
}

@Serializable
enum class PageNumberFontStyle(val label: String) {
    NORMAL("Normal"),
    BOLD("Bold"),
    ITALIC("Italic"),
    BOLD_ITALIC("Bold Italic")
}

@Serializable
enum class PageNumberPosition(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom"),
    LEFT("Left"),
    RIGHT("Right")
}

@Serializable
enum class HorizontalAlign(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

@Serializable
enum class VerticalAlign(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom")
}

@Serializable
data class PageNumberConfig(
    val enabled: Boolean = true,
    val prefix: PageNumberPrefix = PageNumberPrefix.PG,
    val startNumber: Int = 1,
    val fontSizeSp: Float = 12f,
    val fontStyle: PageNumberFontStyle = PageNumberFontStyle.BOLD,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0xCC1B1924,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM,
    val horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER,
    val verticalAlign: VerticalAlign = VerticalAlign.BOTTOM,
    val marginDp: Float = 14f,
    val reserveSafeBand: Boolean = true
) {
    fun formatLabel(zeroBasedPageIndex: Int): String {
        val number = startNumber + zeroBasedPageIndex
        return "${prefix.token} $number"
    }
}

@Serializable
data class PdfExportConfig(
    val imageHandlingMode: ImageHandlingMode = ImageHandlingMode.ORIGINAL_QUALITY,
    val pageSizePreset: PdfPageSizePreset = PdfPageSizePreset.ORIGINAL_IMAGE_SIZE,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val customWidthMm: Float = 210f,
    val customHeightMm: Float = 297f,
    val background: PageBackground = PageBackground.WHITE,
    val outputFileName: String = "Manhwa_Chapter_01",
    val pageNumberConfig: PageNumberConfig = PageNumberConfig()
)

@Serializable
enum class OcrLanguage(val displayName: String, val scriptName: String, val modelFootprintMb: Int) {
    ENGLISH("English", "Latin", 18),
    HINDI("Hindi", "Devanagari", 22),
    JAPANESE("Japanese", "Japanese", 28),
    CHINESE("Chinese", "Chinese", 27)
}

enum class OcrModelStatus(val label: String) {
    BUNDLED_READY("Bundled Offline Ready"),
    INITIALIZING("Initializing"),
    UNAVAILABLE("Unavailable")
}

data class OcrLanguageStatus(
    val language: OcrLanguage,
    val status: OcrModelStatus,
    val isSelected: Boolean
)

@Serializable
data class OcrSettings(
    val enabled: Boolean = false,
    val selectedLanguages: Set<OcrLanguage> = setOf(OcrLanguage.ENGLISH)
)

@Serializable
data class OcrPageResult(
    val pageNumber: Int,
    val pageLabel: String,
    val imageId: String,
    val displayName: String,
    val extractedText: String,
    val languagesUsed: List<String>
)

@Serializable
enum class ExportStatus {
    IDLE,
    RUNNING,
    VALIDATING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ExportProgressState(
    val status: ExportStatus = ExportStatus.IDLE,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val phaseLabel: String = "",
    val progressFraction: Float = 0f,
    val outputUriString: String? = null,
    val outputFileName: String = "",
    val outputSizeBytes: Long = 0L,
    val ocrPagesCompleted: Int = 0,
    val errorMessage: String? = null,
    val validationSummary: String? = null,
    val wasInterrupted: Boolean = false
)
