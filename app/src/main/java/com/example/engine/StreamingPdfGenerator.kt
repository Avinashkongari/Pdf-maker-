package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.StatFs
import com.example.model.ImageHandlingMode
import com.example.model.ImportedImage
import com.example.model.PageBackground
import com.example.model.PageNumberFontStyle
import com.example.model.PdfExportConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

class InsufficientStorageException(message: String) : Exception(message)

interface ImageInputProvider {
    fun openInputStream(image: ImportedImage): InputStream?
}

class AndroidImageInputProvider(private val context: Context) : ImageInputProvider {
    override fun openInputStream(image: ImportedImage): InputStream? {
        val cached = image.cachedPath?.let { File(it) }
        if (cached != null && cached.exists() && cached.canRead()) {
            return FileInputStream(cached)
        }
        return context.contentResolver.openInputStream(Uri.parse(image.uriString))
    }
}

private class CountingOutputStream(private val delegate: OutputStream) : OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

data class PdfGenerationResult(
    val outputFile: File,
    val pageCount: Int,
    val fileSizeBytes: Long
)

class StreamingPdfGenerator(
    private val inputProvider: ImageInputProvider,
    private val availableBytesProvider: (File) -> Long = { dir ->
        val statFsBytes = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)
        maxOf(statFsBytes, dir.usableSpace)
    }
) {

    suspend fun generatePdf(
        images: List<ImportedImage>,
        config: PdfExportConfig,
        outputFile: File,
        isCancelled: () -> Boolean = { false },
        onProgress: suspend (currentPage: Int, totalPages: Int, phase: String) -> Unit = { _, _, _ -> }
    ): PdfGenerationResult {
        require(images.isNotEmpty()) { "At least one image is required to build a PDF." }
        outputFile.parentFile?.mkdirs()

        // Reserve at least 16 MB safety buffer plus estimated minimum output overhead
        val minRequiredBuffer = 16L * 1024L * 1024L
        val parentDir = outputFile.parentFile ?: outputFile
        if (availableBytesProvider(parentDir) < minRequiredBuffer) {
            throw InsufficientStorageException("Less than 16 MB of free storage available.")
        }

        val pageObjectIds = ArrayList<Int>(images.size)
        // Object 1 = Catalog, Object 2 = Pages, Object 3..6 = Standard Type1 Fonts
        val offsets = mutableMapOf<Int, Long>()
        var nextObjectId = 7

        val fos = FileOutputStream(outputFile)
        val countingOut = CountingOutputStream(BufferedOutputStream(fos, 64 * 1024))

        var completedNormally = false
        try {
            // PDF 1.4 Header with binary marker
            countingOut.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
            countingOut.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

            // Write 4 standard Helvetica Type1 font objects (IDs 3..6)
            writeFontObject(countingOut, offsets, 3, "Helvetica")
            writeFontObject(countingOut, offsets, 4, "Helvetica-Bold")
            writeFontObject(countingOut, offsets, 5, "Helvetica-Oblique")
            writeFontObject(countingOut, offsets, 6, "Helvetica-BoldOblique")

            val total = images.size
            for ((index, image) in images.withIndex()) {
                currentCoroutineContext().ensureActive()
                if (isCancelled()) throw CancellationException("PDF export cancelled by user.")

                // Periodically guard storage before processing each high-resolution page
                val estNeeded = (image.fileSizeBytes.coerceAtLeast(512L * 1024L)) + 4L * 1024L * 1024L
                if (availableBytesProvider(parentDir) < estNeeded) {
                    throw InsufficientStorageException("Insufficient storage space while writing page ${index + 1}.")
                }

                onProgress(index + 1, total, "Embedding page ${index + 1} of $total (${image.formatLabel})")

                val layout = PageLayoutCalculator.calculatePageLayout(image, index, config)

                val imageObjId = nextObjectId++
                val smaskObjId = writeImageXObjectStreaming(
                    out = countingOut,
                    offsets = offsets,
                    imageObjId = imageObjId,
                    nextObjIdProvider = { nextObjectId++ },
                    image = image,
                    mode = config.imageHandlingMode,
                    cacheDir = outputFile.parentFile
                )
                // Notice writeImageXObjectStreaming already writes the XObject; let's keep IDs tidy
                @Suppress("UNUSED_VARIABLE")
                val unusedSmask = smaskObjId

                val contentObjId = nextObjectId++
                val fontResId = when (config.pageNumberConfig.fontStyle) {
                    PageNumberFontStyle.NORMAL -> 3
                    PageNumberFontStyle.BOLD -> 4
                    PageNumberFontStyle.ITALIC -> 5
                    PageNumberFontStyle.BOLD_ITALIC -> 6
                }
                val contentBytes = buildPageContentStream(layout, config, fontResId)
                writeStreamObject(
                    out = countingOut,
                    offsets = offsets,
                    objId = contentObjId,
                    dictEntries = emptyMap(),
                    rawStream = contentBytes,
                    compress = true
                )

                val pageObjId = nextObjectId++
                pageObjectIds.add(pageObjId)
                writePageObject(
                    out = countingOut,
                    offsets = offsets,
                    pageObjId = pageObjId,
                    pagesParentId = 2,
                    layout = layout,
                    imageObjId = imageObjId,
                    contentObjId = contentObjId,
                    fontResId = fontResId
                )
            }

            if (isCancelled()) throw CancellationException("PDF export cancelled by user.")
            onProgress(total, total, "Finalizing PDF cross-reference table...")

            // Write /Pages object (ID 2)
            offsets[2] = countingOut.bytesWritten
            val kidsRef = pageObjectIds.joinToString(" ") { "$it 0 R" }
            val pagesObj = "2 0 obj\n<< /Type /Pages /Count ${pageObjectIds.size} /Kids [ $kidsRef ] >>\nendobj\n"
            countingOut.write(pagesObj.toByteArray(StandardCharsets.US_ASCII))

            // Write /Catalog object (ID 1)
            offsets[1] = countingOut.bytesWritten
            val catalogObj = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
            countingOut.write(catalogObj.toByteArray(StandardCharsets.US_ASCII))

            // Write xref table
            val maxObjId = nextObjectId - 1
            val xrefOffset = countingOut.bytesWritten
            val xrefHeader = "xref\n0 ${maxObjId + 1}\n0000000000 65535 f \n"
            countingOut.write(xrefHeader.toByteArray(StandardCharsets.US_ASCII))
            for (id in 1..maxObjId) {
                val off = offsets[id] ?: 0L
                val line = String.format(Locale.US, "%010d 00000 n \n", off)
                countingOut.write(line.toByteArray(StandardCharsets.US_ASCII))
            }

            val trailer = "trailer\n<< /Size ${maxObjId + 1} /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
            countingOut.write(trailer.toByteArray(StandardCharsets.US_ASCII))
            countingOut.flush()
            completedNormally = true
            return PdfGenerationResult(
                outputFile = outputFile,
                pageCount = pageObjectIds.size,
                fileSizeBytes = countingOut.bytesWritten
            )
        } finally {
            runCatching { countingOut.close() }
            if (!completedNormally) {
                runCatching { if (outputFile.exists()) outputFile.delete() }
            }
        }
    }

    private fun writeFontObject(
        out: CountingOutputStream,
        offsets: MutableMap<Int, Long>,
        objId: Int,
        baseFont: String
    ) {
        offsets[objId] = out.bytesWritten
        val obj = "$objId 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /$baseFont /Encoding /WinAnsiEncoding >>\nendobj\n"
        out.write(obj.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writeImageXObjectStreaming(
        out: CountingOutputStream,
        offsets: MutableMap<Int, Long>,
        imageObjId: Int,
        nextObjIdProvider: () -> Int,
        image: ImportedImage,
        mode: ImageHandlingMode,
        cacheDir: File?
    ): Int? {
        val rawBytes = inputProvider.openInputStream(image)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read image stream for ${image.displayName}")

        // 1. Direct JPEG /DCTDecode passthrough when JPEG is standard YCbCr/Grayscale
        val isJpeg = image.mimeType.contains("jpeg", ignoreCase = true) ||
            image.mimeType.contains("jpg", ignoreCase = true) ||
            (rawBytes.size >= 2 && (rawBytes[0].toInt() and 0xFF) == 0xFF && (rawBytes[1].toInt() and 0xFF) == 0xD8)

        if (isJpeg) {
            val jpegInfo = JpegHeaderParser.inspect(rawBytes)
            if (jpegInfo != null && jpegInfo.isStandardBaselineOrProgressive) {
                val jpegPayload = if (mode == ImageHandlingMode.OPTIMIZED_LOSSLESS) {
                    JpegHeaderParser.stripMetadataLosslessly(rawBytes)
                } else {
                    rawBytes
                }
                val colorSpace = if (jpegInfo.components == 1) "/DeviceGray" else "/DeviceRGB"
                offsets[imageObjId] = out.bytesWritten
                val dict = buildString {
                    append("$imageObjId 0 obj\n")
                    append("<< /Type /XObject /Subtype /Image ")
                    append("/Width ${jpegInfo.width} /Height ${jpegInfo.height} ")
                    append("/ColorSpace $colorSpace /BitsPerComponent 8 ")
                    append("/Filter /DCTDecode /Length ${jpegPayload.size} >>\n")
                    append("stream\n")
                }
                out.write(dict.toByteArray(StandardCharsets.US_ASCII))
                out.write(jpegPayload)
                out.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
                return null
            }
        }

        // 2. Lossless Bitmap decoding for PNG / WEBP / non-baseline JPEG (NEVER converted to JPEG!)
        val decodeOpts = BitmapFactory.Options().apply {
            inScaled = false
            inSampleSize = 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalStateException("Failed to decode lossless bitmap for ${image.displayName}")

        try {
            val width = bitmap.width
            val height = bitmap.height
            val rowPixels = IntArray(width)

            var hasTransparency = false
            var isGrayscale = (mode == ImageHandlingMode.OPTIMIZED_LOSSLESS)

            if (bitmap.hasAlpha() || mode == ImageHandlingMode.OPTIMIZED_LOSSLESS) {
                for (y in 0 until height) {
                    bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
                    for (x in 0 until width) {
                        val px = rowPixels[x]
                        val a = (px ushr 24) and 0xFF
                        if (a < 255) hasTransparency = true
                        if (isGrayscale) {
                            val r = (px ushr 16) and 0xFF
                            val g = (px ushr 8) and 0xFF
                            val b = px and 0xFF
                            if (r != g || g != b) isGrayscale = false
                        }
                        if (hasTransparency && !isGrayscale) break
                    }
                    if (hasTransparency && !isGrayscale) break
                }
            }

            // Write optional Alpha SMask first so imageObjId can reference it cleanly
            var smaskObjId: Int? = null
            if (hasTransparency) {
                smaskObjId = nextObjIdProvider()
                val alphaFile = File.createTempFile("alpha_", ".flate", cacheDir)
                try {
                    val deflaterLevel = if (mode == ImageHandlingMode.OPTIMIZED_LOSSLESS) Deflater.BEST_COMPRESSION else Deflater.DEFAULT_COMPRESSION
                    val alphaOut = FileOutputStream(alphaFile)
                    val deflater = Deflater(deflaterLevel)
                    DeflaterOutputStream(alphaOut, deflater, 16384).use { dos ->
                        val alphaRow = ByteArray(width)
                        for (y in 0 until height) {
                            bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
                            for (x in 0 until width) {
                                alphaRow[x] = ((rowPixels[x] ushr 24) and 0xFF).toByte()
                            }
                            dos.write(alphaRow)
                        }
                        dos.finish()
                    }
                    deflater.end()

                    val alphaLen = alphaFile.length()
                    offsets[smaskObjId] = out.bytesWritten
                    val smaskDict = "$smaskObjId 0 obj\n<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode /Length $alphaLen >>\nstream\n"
                    out.write(smaskDict.toByteArray(StandardCharsets.US_ASCII))
                    FileInputStream(alphaFile).use { fis ->
                        val buf = ByteArray(16384)
                        while (true) {
                            val read = fis.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                        }
                    }
                    out.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
                } finally {
                    runCatching { alphaFile.delete() }
                }
            }

            // Stream lossless Flate-compressed pixel data row by row to temp file so RAM stays tiny even for huge 4K x 12K webtoon strips!
            val pixelFile = File.createTempFile("px_", ".flate", cacheDir)
            try {
                val usePredictor = mode == ImageHandlingMode.OPTIMIZED_LOSSLESS
                val channels = if (isGrayscale) 1 else 3
                val deflaterLevel = if (mode == ImageHandlingMode.OPTIMIZED_LOSSLESS) Deflater.BEST_COMPRESSION else Deflater.DEFAULT_COMPRESSION
                val deflater = Deflater(deflaterLevel)
                DeflaterOutputStream(FileOutputStream(pixelFile), deflater, 32768).use { dos ->
                    val rowBytes = ByteArray(width * channels)
                    var prevRowBytes = if (usePredictor) ByteArray(width * channels) else null
                    val filteredRow = if (usePredictor) ByteArray(1 + width * channels) else null

                    for (y in 0 until height) {
                        bitmap.getPixels(rowPixels, 0, width, 0, y, width, 1)
                        if (isGrayscale) {
                            for (x in 0 until width) {
                                rowBytes[x] = ((rowPixels[x] ushr 16) and 0xFF).toByte()
                            }
                        } else {
                            var idx = 0
                            for (x in 0 until width) {
                                val px = rowPixels[x]
                                rowBytes[idx++] = ((px ushr 16) and 0xFF).toByte()
                                rowBytes[idx++] = ((px ushr 8) and 0xFF).toByte()
                                rowBytes[idx++] = (px and 0xFF).toByte()
                            }
                        }

                        if (usePredictor && prevRowBytes != null && filteredRow != null) {
                            // PNG Up predictor (filter type = 2) -> mathematically 100% lossless!
                            filteredRow[0] = 2
                            for (i in rowBytes.indices) {
                                val diff = (rowBytes[i].toInt() and 0xFF) - (prevRowBytes[i].toInt() and 0xFF)
                                filteredRow[i + 1] = (diff and 0xFF).toByte()
                            }
                            dos.write(filteredRow)
                            System.arraycopy(rowBytes, 0, prevRowBytes, 0, rowBytes.size)
                        } else {
                            dos.write(rowBytes)
                        }
                    }
                    dos.finish()
                }
                deflater.end()

                val streamLen = pixelFile.length()
                val colorSpace = if (isGrayscale) "/DeviceGray" else "/DeviceRGB"
                val decodeParms = if (usePredictor) {
                    " /DecodeParms << /Predictor 12 /Colors $channels /BitsPerComponent 8 /Columns $width >>"
                } else ""
                val smaskEntry = if (smaskObjId != null) " /SMask $smaskObjId 0 R" else ""

                offsets[imageObjId] = out.bytesWritten
                val imgHeader = "$imageObjId 0 obj\n<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace $colorSpace /BitsPerComponent 8 /Filter /FlateDecode$decodeParms$smaskEntry /Length $streamLen >>\nstream\n"
                out.write(imgHeader.toByteArray(StandardCharsets.US_ASCII))
                FileInputStream(pixelFile).use { fis ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val read = fis.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                }
                out.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
                return smaskObjId
            } finally {
                runCatching { pixelFile.delete() }
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun buildPageContentStream(
        layout: PageLayoutResult,
        config: PdfExportConfig,
        fontResId: Int
    ): ByteArray {
        val sb = StringBuilder(256)
        // 1. Page Background Fill
        val bgRgb = colorLongToPdfRgb(layout.backgroundColor.colorArgb)
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", bgRgb.first, bgRgb.second, bgRgb.third))
        sb.append(String.format(Locale.US, "0 0 %.3f %.3f re f\n", layout.pageWidthPt, layout.pageHeightPt))

        // 2. Draw Image XObject with exact EXIF + manual rotation matrix
        sb.append("q\n")
        sb.append(layout.imageMatrix.toPdfCommand()).append('\n')
        sb.append("/Im1 Do\n")
        sb.append("Q\n")

        // 3. Draw Page Numbering badge & text if enabled
        val numLayout = layout.pageNumberLayout
        if (numLayout != null) {
            val badgeAlpha = ((config.pageNumberConfig.backgroundColorHex ushr 24) and 0xFF)
            if (badgeAlpha > 0) {
                val badgeRgb = colorLongToPdfRgb(config.pageNumberConfig.backgroundColorHex)
                sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", badgeRgb.first, badgeRgb.second, badgeRgb.third))
                val r = numLayout.badgeRect
                sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f re f\n", r.left, r.bottom, r.width, r.height))
            }

            val textRgb = colorLongToPdfRgb(config.pageNumberConfig.textColorHex)
            val escaped = escapePdfText(numLayout.text)
            sb.append("BT\n")
            sb.append("/F$fontResId ${String.format(Locale.US, "%.2f", numLayout.fontSizePt)} Tf\n")
            sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", textRgb.first, textRgb.second, textRgb.third))
            sb.append(String.format(Locale.US, "1 0 0 1 %.3f %.3f Tm\n", numLayout.textX, numLayout.textY))
            sb.append("($escaped) Tj\n")
            sb.append("ET\n")
        }

        return sb.toString().toByteArray(StandardCharsets.US_ASCII)
    }

    private fun writeStreamObject(
        out: CountingOutputStream,
        offsets: MutableMap<Int, Long>,
        objId: Int,
        dictEntries: Map<String, String>,
        rawStream: ByteArray,
        compress: Boolean
    ) {
        val payload = if (compress) {
            val baos = ByteArrayOutputStream()
            DeflaterOutputStream(baos, Deflater(Deflater.DEFAULT_COMPRESSION)).use { it.write(rawStream) }
            baos.toByteArray()
        } else {
            rawStream
        }

        offsets[objId] = out.bytesWritten
        val dict = buildString {
            append("$objId 0 obj\n<< ")
            dictEntries.forEach { (k, v) -> append("/$k $v ") }
            if (compress) append("/Filter /FlateDecode ")
            append("/Length ${payload.size} >>\nstream\n")
        }
        out.write(dict.toByteArray(StandardCharsets.US_ASCII))
        out.write(payload)
        out.write("\nendstream\nendobj\n".toByteArray(StandardCharsets.US_ASCII))
    }

    private fun writePageObject(
        out: CountingOutputStream,
        offsets: MutableMap<Int, Long>,
        pageObjId: Int,
        pagesParentId: Int,
        layout: PageLayoutResult,
        imageObjId: Int,
        contentObjId: Int,
        fontResId: Int
    ) {
        offsets[pageObjId] = out.bytesWritten
        val w = String.format(Locale.US, "%.2f", layout.pageWidthPt)
        val h = String.format(Locale.US, "%.2f", layout.pageHeightPt)
        val obj = """
            $pageObjId 0 obj
            << /Type /Page /Parent $pagesParentId 0 R /MediaBox [0 0 $w $h] /Resources << /XObject << /Im1 $imageObjId 0 R >> /Font << /F$fontResId $fontResId 0 R >> >> /Contents $contentObjId 0 R >>
            endobj
            
        """.trimIndent()
        out.write(obj.toByteArray(StandardCharsets.US_ASCII))
    }

    private fun colorLongToPdfRgb(argb: Long): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return Triple(r, g, b)
    }

    private fun escapePdfText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
    }
}
