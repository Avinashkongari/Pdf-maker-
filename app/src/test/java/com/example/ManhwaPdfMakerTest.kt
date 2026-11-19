package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ImageInputProvider
import com.example.engine.InsufficientStorageException
import com.example.engine.OnDeviceOcrEngine
import com.example.engine.PageLayoutCalculator
import com.example.engine.PdfValidator
import com.example.engine.StreamingPdfGenerator
import com.example.model.ImageHandlingMode
import com.example.model.ImportedImage
import com.example.model.OcrLanguage
import com.example.model.OcrModelStatus
import com.example.model.OcrPageResult
import com.example.model.OcrSettings
import com.example.model.PageBackground
import com.example.model.PageNumberConfig
import com.example.model.PageNumberPosition
import com.example.model.PageNumberPrefix
import com.example.model.PdfExportConfig
import com.example.model.PdfPageSizePreset
import com.example.ui.screens.OcrSettingsScreen
import com.example.ui.screens.PdfSettingsScreen
import com.example.ui.theme.ManhwaPdfMakerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManhwaPdfMakerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    private class FileBackedInputProvider(private val files: Map<String, File>) : ImageInputProvider {
        override fun openInputStream(image: ImportedImage): InputStream? {
            val file = files[image.id] ?: image.cachedPath?.let { File(it) } ?: return null
            return FileInputStream(file)
        }
    }

    private fun createSyntheticBitmapFile(
        name: String,
        width: Int,
        height: Int,
        format: Bitmap.CompressFormat,
        quality: Int = 100,
        grayscale: Boolean = false
    ): File {
        val file = File(tempFolder.root, name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (grayscale) {
            bmp.eraseColor(Color.rgb(210, 210, 210))
        } else {
            bmp.eraseColor(Color.rgb(168, 117, 255))
        }
        FileOutputStream(file).use { out ->
            bmp.compress(format, quality, out)
        }
        bmp.recycle()
        return file
    }

    private fun buildImportedList(
        count: Int,
        mixedFormats: Boolean = false,
        width: Int = 120,
        height: Int = 180
    ): Pair<List<ImportedImage>, Map<String, File>> {
        val list = ArrayList<ImportedImage>(count)
        val fileMap = mutableMapOf<String, File>()

        val jpgFile = createSyntheticBitmapFile("fixture.jpg", width, height, Bitmap.CompressFormat.JPEG, 95)
        val pngFile = createSyntheticBitmapFile("fixture.png", width, height, Bitmap.CompressFormat.PNG, 100)
        @Suppress("DEPRECATION")
        val webpFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
        val webpFile = createSyntheticBitmapFile("fixture.webp", width, height, webpFormat, 100)

        for (i in 0 until count) {
            val (file, mime, ext) = if (!mixedFormats) {
                Triple(jpgFile, "image/jpeg", "jpg")
            } else {
                when (i % 3) {
                    0 -> Triple(jpgFile, "image/jpeg", "jpg")
                    1 -> Triple(pngFile, "image/png", "png")
                    else -> Triple(webpFile, "image/webp", "webp")
                }
            }
            val id = "img_$i"
            fileMap[id] = file
            list.add(
                ImportedImage(
                    id = id,
                    uriString = file.toURI().toString(),
                    cachedPath = file.absolutePath,
                    displayName = "chapter_page_${i + 1}.$ext",
                    mimeType = mime,
                    widthPx = width,
                    heightPx = height,
                    fileSizeBytes = file.length(),
                    exifOrientation = ExifInterface.ORIENTATION_NORMAL,
                    orderIndex = i
                )
            )
        }
        return list to fileMap
    }

    @Test
    fun `test 1 image PDF generation and validation`() = runTest {
        val (images, files) = buildImportedList(1)
        val outFile = File(tempFolder.root, "one_page.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))
        val res = generator.generatePdf(images, PdfExportConfig(), outFile)

        assertEquals(1, res.pageCount)
        val report = PdfValidator.validatePdfFile(outFile, expectedPages = 1)
        assertTrue(report.message, report.isValid)
    }

    @Test
    fun `test 20 image PDF generation and validation`() = runTest {
        val (images, files) = buildImportedList(20, mixedFormats = true)
        val outFile = File(tempFolder.root, "twenty_pages.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))
        val res = generator.generatePdf(images, PdfExportConfig(), outFile)

        assertEquals(20, res.pageCount)
        val report = PdfValidator.validatePdfFile(outFile, expectedPages = 20)
        assertTrue(report.message, report.isValid)
    }

    @Test
    fun `test 100 image PDF generation and validation`() = runTest {
        val (images, files) = buildImportedList(100, mixedFormats = true)
        val outFile = File(tempFolder.root, "hundred_pages.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))
        val res = generator.generatePdf(images, PdfExportConfig(), outFile)

        assertEquals(100, res.pageCount)
        val report = PdfValidator.validatePdfFile(outFile, expectedPages = 100)
        assertTrue(report.message, report.isValid)
    }

    @Test
    fun `test 200 image PDF streaming generation and validation`() = runTest {
        val (images, files) = buildImportedList(200, mixedFormats = true)
        val outFile = File(tempFolder.root, "two_hundred_pages.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))
        val res = generator.generatePdf(images, PdfExportConfig(), outFile)

        assertEquals(200, res.pageCount)
        val report = PdfValidator.validatePdfFile(outFile, expectedPages = 200)
        assertTrue(report.message, report.isValid)
    }

    @Test
    fun `test mixed JPG PNG WEBP preserves lossless PNG and raw JPEG without converting PNG to JPEG`() = runTest {
        val (images, files) = buildImportedList(3, mixedFormats = true)
        val outFile = File(tempFolder.root, "mixed_formats.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))
        generator.generatePdf(images, PdfExportConfig(imageHandlingMode = ImageHandlingMode.ORIGINAL_QUALITY), outFile)

        val pdfBytes = outFile.readBytes()
        val pdfText = String(pdfBytes, StandardCharsets.ISO_8859_1)

        assertTrue("Expected /DCTDecode for original JPEG", pdfText.contains("/Filter /DCTDecode"))
        val dctCount = Regex("/Filter /DCTDecode").findAll(pdfText).count()
        assertEquals("Only the 1 JPEG should use DCTDecode; PNG and WEBP must stay lossless FlateDecode", 1, dctCount)

        val rawJpgBytes = files["img_0"]!!.readBytes()
        val rawJpgLatin1 = String(rawJpgBytes, StandardCharsets.ISO_8859_1)
        assertTrue("Original JPEG bytes must be preserved bit-for-bit in Original Quality mode", pdfText.contains(rawJpgLatin1))
    }

    @Test
    fun `test high resolution manhwa strip preserves exact pixel dimensions and Optimized Lossless grayscale predictor`() = runTest {
        val hiResFile = createSyntheticBitmapFile("hires_strip.png", 1600, 4200, Bitmap.CompressFormat.PNG, grayscale = true)
        val img = ImportedImage(
            id = "hires",
            uriString = hiResFile.toURI().toString(),
            cachedPath = hiResFile.absolutePath,
            displayName = "hires_strip.png",
            mimeType = "image/png",
            widthPx = 1600,
            heightPx = 4200,
            fileSizeBytes = hiResFile.length()
        )
        val origOut = File(tempFolder.root, "hires_orig.pdf")
        val optOut = File(tempFolder.root, "hires_opt.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(mapOf("hires" to hiResFile)))

        generator.generatePdf(listOf(img), PdfExportConfig(imageHandlingMode = ImageHandlingMode.ORIGINAL_QUALITY), origOut)
        generator.generatePdf(listOf(img), PdfExportConfig(imageHandlingMode = ImageHandlingMode.OPTIMIZED_LOSSLESS), optOut)

        val origText = String(origOut.readBytes(), StandardCharsets.ISO_8859_1)
        val optText = String(optOut.readBytes(), StandardCharsets.ISO_8859_1)

        assertTrue(origText.contains("/Width 1600 /Height 4200"))
        assertTrue(optText.contains("/Width 1600 /Height 4200"))
        assertTrue(optText.contains("/ColorSpace /DeviceGray"))
        assertTrue(optText.contains("/Predictor 12"))
        assertTrue("Optimized Lossless should shrink overhead vs raw RGB Flate", optOut.length() <= origOut.length())
    }

    @Test
    fun `test automatic page numbering prefixes safe band non-overlap and EXIF rotation matrix`() {
        val img = ImportedImage(
            id = "p1",
            uriString = "",
            displayName = "page1.png",
            mimeType = "image/png",
            widthPx = 800,
            heightPx = 1200,
            fileSizeBytes = 1000L,
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_90,
            manualRotationDegrees = 0
        )
        assertEquals(1200 to 800, img.effectiveDimensions)

        val cfg = PdfExportConfig(
            pageSizePreset = PdfPageSizePreset.A4_PORTRAIT,
            background = PageBackground.BLACK,
            pageNumberConfig = PageNumberConfig(
                enabled = true,
                prefix = PageNumberPrefix.CHAPTER,
                startNumber = 5,
                position = PageNumberPosition.BOTTOM,
                reserveSafeBand = true
            )
        )
        val layout = PageLayoutCalculator.calculatePageLayout(img, zeroBasedPageIndex = 2, config = cfg)
        val numLayout = layout.pageNumberLayout
        assertNotNull(numLayout)
        assertEquals("CHAPTER 7", numLayout!!.text)

        assertTrue(
            "Image bottom (${layout.imageDrawRect.bottom}) must stay above page number badge top (${numLayout.badgeRect.top})",
            layout.imageDrawRect.bottom >= numLayout.badgeRect.top
        )
    }

    @Test
    fun `test PDF generation cancellation cleans staging file and leaves zero corrupted output`() = runTest {
        val (images, files) = buildImportedList(10)
        val outFile = File(tempFolder.root, "cancelled.pdf")
        val generator = StreamingPdfGenerator(FileBackedInputProvider(files))

        var processed = 0
        var caughtCancel = false
        try {
            generator.generatePdf(
                images = images,
                config = PdfExportConfig(),
                outputFile = outFile,
                isCancelled = { processed >= 3 },
                onProgress = { cur, _, _ -> processed = cur }
            )
        } catch (_: CancellationException) {
            caughtCancel = true
        }

        assertTrue("CancellationException must be thrown", caughtCancel)
        assertFalse("Cancelled partial PDF must be deleted automatically", outFile.exists())
    }

    @Test
    fun `test insufficient storage throws InsufficientStorageException gracefully`() = runTest {
        val (images, files) = buildImportedList(2)
        val outFile = File(tempFolder.root, "no_space.pdf")
        val generator = StreamingPdfGenerator(
            inputProvider = FileBackedInputProvider(files),
            availableBytesProvider = { 1024L * 1024L }
        )

        var caughtStorage = false
        try {
            generator.generatePdf(images, PdfExportConfig(), outFile)
        } catch (_: InsufficientStorageException) {
            caughtStorage = true
        }

        assertTrue("InsufficientStorageException must be thrown", caughtStorage)
        assertFalse("No corrupted file should exist on storage error", outFile.exists())
    }

    @Test
    fun `test PdfValidator rejects truncated or corrupted PDF files`() {
        val corrupt = File(tempFolder.root, "corrupt.pdf")
        corrupt.writeText("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n")
        val report = PdfValidator.validatePdfFile(corrupt, expectedPages = 1)
        assertFalse("Validator must reject incomplete PDF without %%EOF", report.isValid)
    }

    @Test
    fun `test OCR OFF does not process images and supports EN HI JA ZH statuses`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OnDeviceOcrEngine(context)
        val (images, _) = buildImportedList(3)

        val offResults = engine.recognizePages(
            images = images,
            ocrSettings = OcrSettings(enabled = false),
            pageNumberConfig = PageNumberConfig()
        )
        assertTrue(offResults.isEmpty())

        val statuses = engine.getLanguageStatuses(setOf(OcrLanguage.ENGLISH, OcrLanguage.HINDI, OcrLanguage.JAPANESE, OcrLanguage.CHINESE))
        assertEquals(4, statuses.size)
        assertTrue(statuses.all { it.status == OcrModelStatus.BUNDLED_READY && it.isSelected })
    }

    @Test
    fun `test reordering image list updates order indices correctly`() {
        val (images, _) = buildImportedList(5)
        val mutable = images.toMutableList()
        val moved = mutable.removeAt(0)
        mutable.add(3, moved)
        val reindexed = mutable.mapIndexed { idx, img -> img.copy(orderIndex = idx) }

        assertEquals("img_1", reindexed[0].id)
        assertEquals("img_0", reindexed[3].id)
        assertEquals(3, reindexed[3].orderIndex)
    }

    @Test
    fun `test Compose UI OCR ON OFF toggle`() {
        var ocrSettings = OcrSettings(enabled = false)

        composeRule.setContent {
            ManhwaPdfMakerTheme {
                OcrSettingsScreen(
                    ocrSettings = ocrSettings,
                    languageStatuses = OcrLanguage.entries.map {
                        com.example.model.OcrLanguageStatus(it, OcrModelStatus.BUNDLED_READY, it in ocrSettings.selectedLanguages)
                    },
                    ocrResults = listOf(
                        OcrPageResult(1, "PG 1", "id1", "page1.png", "Hello Manhwa", listOf("English"))
                    ),
                    searchQuery = "",
                    isScanning = false,
                    scanProgress = 0 to 0,
                    imageCount = 1,
                    onUpdateSettings = { transform -> ocrSettings = transform(ocrSettings) },
                    onRunOcr = {},
                    onCancelOcr = {},
                    onSearchQueryChange = {},
                    onExportTxtUri = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("enable_ocr_switch").assertIsDisplayed().performClick()
        assertTrue("OCR switch should turn ON", ocrSettings.enabled)
    }

    @Test
    fun `test Compose UI PDF settings interactions`() {
        var latestConfig = PdfExportConfig()

        composeRule.setContent {
            var pdfConfig by remember { mutableStateOf(PdfExportConfig()) }
            ManhwaPdfMakerTheme {
                PdfSettingsScreen(
                    config = pdfConfig,
                    sampleImage = null,
                    onUpdateConfig = { transform ->
                        pdfConfig = transform(pdfConfig)
                        latestConfig = pdfConfig
                    }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pdf_settings_list").performScrollToIndex(1)
        composeRule.onNodeWithTag("quality_mode_OPTIMIZED_LOSSLESS").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(ImageHandlingMode.OPTIMIZED_LOSSLESS, latestConfig.imageHandlingMode)

        composeRule.onNodeWithTag("pdf_settings_list").performScrollToIndex(2)
        composeRule.onNodeWithTag("page_size_A4_PORTRAIT").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(PdfPageSizePreset.A4_PORTRAIT, latestConfig.pageSizePreset)
    }

    @Test
    fun `test MainActivity launches cleanly under Robolectric`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertEquals("com.axion.manhwapdfmaker", activity.packageName)
        controller.pause().stop().destroy()
    }
}
